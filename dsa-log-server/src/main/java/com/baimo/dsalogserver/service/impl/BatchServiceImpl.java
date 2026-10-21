package com.baimo.dsalogserver.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baimo.dsalogserver.common.BatchIds;
import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.entity.ReviewRecord;
import com.baimo.dsalogserver.entity.Tag;
import com.baimo.dsalogserver.mapper.ProblemListItemMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.mapper.TagMapper;
import com.baimo.dsalogserver.service.BatchService;
import com.baimo.dsalogserver.service.ListService;
import com.baimo.dsalogserver.service.ReviewScheduler;
import com.baimo.dsalogserver.vo.BatchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 批量操作实现。
 *
 * <p>统一的执行骨架：归一化 ids → 查「真实命中且未删」的行 → 一次批量 SQL 改状态
 * → 需要流水的再批量插。不做逐题往返，也不依赖 MP 的 {@code updateById}：
 * 批量场景下 {@code FieldStrategy.NOT_NULL} 会让 {@code next_review_at = null}
 * 这类「清空」操作静默失效。
 */
@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

    /** 标签单次上限比 id 更紧：一次给一道题挂 20 个标签已经没有意义 */
    private static final int MAX_TAGS_PER_BATCH = 20;

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemListItemMapper problemListItemMapper;
    private final TagMapper tagMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final ListService listService;

    @Override
    @Transactional
    public BatchVO markSolved(List<String> ids) {
        List<String> targets = BatchIds.normalize(ids);
        List<Problem> rows = problemMapper.selectByIdsForUpdate(targets);
        if (rows.isEmpty()) {
            return new BatchVO(0);
        }

        LocalDateTime now = TimeUtil.now();
        // 首次进池的到期时间与单条走同一个算法，避免两处魔数
        LocalDateTime firstReviewAt = now.plusDays(ReviewScheduler.intervalOf(0));
        List<ReviewRecord> records = new ArrayList<>(rows.size());

        for (Problem p : rows) {
            int fromStage = p.getReviewStage() == null ? 0 : p.getReviewStage();
            boolean inPool = p.getNextReviewAt() != null;
            int toStage = fromStage;
            Integer intervalDays = null;
            LocalDateTime nextReviewAt = p.getNextReviewAt();
            if (!inPool) {
                ReviewScheduler.Outcome outcome = ReviewScheduler.onFirstSolve(now);
                toStage = outcome.stage();
                intervalDays = outcome.intervalDays();
                nextReviewAt = outcome.nextReviewAt();
            }
            records.add(record(p.getId(), "MARK_SOLVED", fromStage, toStage, intervalDays, nextReviewAt, now));
        }

        problemMapper.markSolvedBatch(rows.stream().map(Problem::getId).toList(), now, firstReviewAt);
        reviewRecordMapper.insertBatch(records);
        return new BatchVO(rows.size());
    }

    @Override
    @Transactional
    public BatchVO markUnsolved(List<String> ids) {
        List<String> targets = BatchIds.normalize(ids);
        List<Problem> rows = problemMapper.selectByIdsForUpdate(targets);
        List<Problem> solved = rows.stream().filter(p -> "solved".equals(p.getStatus())).toList();
        if (solved.isEmpty()) {
            return new BatchVO(0);
        }

        LocalDateTime now = TimeUtil.now();
        List<ReviewRecord> records = new ArrayList<>(solved.size());
        for (Problem p : solved) {
            int fromStage = p.getReviewStage() == null ? 0 : p.getReviewStage();
            records.add(record(p.getId(), "MARK_UNSOLVED", fromStage, 0, null, null, now));
        }

        problemMapper.markUnsolvedBatch(solved.stream().map(Problem::getId).toList(), now);
        reviewRecordMapper.insertBatch(records);
        return new BatchVO(solved.size());
    }

    @Override
    @Transactional
    public BatchVO addTags(List<String> ids, List<String> tagIds) {
        List<String> targets = BatchIds.normalize(ids);
        List<String> wanted = normalizeTagIds(tagIds);

        // 只挂库里真实存在的标签，与单条 replaceTags 的静默过滤保持一致
        List<String> validTags = tagMapper.selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getId, wanted))
                .stream().map(Tag::getId).toList();
        if (validTags.isEmpty()) {
            return new BatchVO(0);
        }

        List<String> existing = problemMapper.selectExistingIds(targets);
        if (existing.isEmpty()) {
            return new BatchVO(0);
        }
        List<String> need = problemMapper.selectIdsNeedTags(existing, validTags, validTags.size());
        if (need.isEmpty()) {
            return new BatchVO(0);
        }

        problemTagMapper.insertIgnoreBatch(need, validTags);
        problemMapper.touchBatch(need, TimeUtil.now());
        return new BatchVO(need.size());
    }

    @Override
    @Transactional
    public BatchVO addToList(List<String> ids, String listId) {
        // 委托给 ListService：题单相关的「查缺失 → 取序号 → INSERT IGNORE」只保留一份实现，
        // 题单端点与本端点调出完全一致的 affected 口径。事务边界由被委托方自己声明。
        return listService.addProblems(listId, ids);
    }

    @Override
    @Transactional
    public BatchVO remove(List<String> ids) {
        List<String> targets = BatchIds.normalize(ids);
        // 先按库里真实存在的行筛一遍，affected 才等于真正删掉的条数
        List<String> existing = problemMapper.selectExistingIds(targets);
        if (existing.isEmpty()) {
            return new BatchVO(0);
        }

        // 关联行是物理删，先清再逻辑删题；两条失败任一都靠事务回滚
        problemTagMapper.deleteByProblemIds(existing);
        problemListItemMapper.deleteByProblemIds(existing);
        problemMapper.logicalDeleteBatch(existing, TimeUtil.now());
        return new BatchVO(existing.size());
    }

    // ---------------- 内部 ----------------

    private static ReviewRecord record(String problemId, String kind, int fromStage, int toStage,
                                       Integer intervalDays, LocalDateTime nextReviewAt, LocalDateTime now) {
        ReviewRecord r = new ReviewRecord();
        r.setId(UUID.randomUUID().toString());
        r.setProblemId(problemId);
        r.setKind(kind);
        r.setFromStage(fromStage);
        r.setToStage(toStage);
        r.setIntervalDays(intervalDays);
        r.setNextReviewAt(nextReviewAt);
        r.setReviewedAt(now);
        r.setCreatedAt(now);
        return r;
    }

    /** 归一化放进 BatchIds：题单端点也要同一套「空集合 400 / 超限 413」口径 */

    private static List<String> normalizeTagIds(List<String> tagIds) {
        if (tagIds == null) {
            throw BizException.invalid("tagIds 不能为空");
        }
        List<String> cleaned = tagIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            throw BizException.invalid("tagIds 不能为空");
        }
        if (cleaned.size() > MAX_TAGS_PER_BATCH) {
            throw BizException.payloadTooLarge("一次最多加 " + MAX_TAGS_PER_BATCH + " 个标签");
        }
        return cleaned;
    }
}
