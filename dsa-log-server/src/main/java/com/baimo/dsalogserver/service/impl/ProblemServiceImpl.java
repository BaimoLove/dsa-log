package com.baimo.dsalogserver.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.PageResult;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.ProblemConverter;
import com.baimo.dsalogserver.dto.CreateProblemRequest;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.dto.UpdateProblemRequest;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.entity.ReviewRecord;
import com.baimo.dsalogserver.entity.Tag;
import com.baimo.dsalogserver.mapper.*;
import com.baimo.dsalogserver.service.ProblemService;
import com.baimo.dsalogserver.service.ReviewScheduler;
import com.baimo.dsalogserver.vo.MarkSolvedVO;
import com.baimo.dsalogserver.vo.ProblemVO;
import com.baimo.dsalogserver.vo.TagRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 题目业务实现。事务边界集中在这里：
 * - markSolved / markUnsolved：problem 行锁 + 写流水，同一事务
 * - remove：逻辑删 + 清关联，同一事务
 */
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    /** 排序白名单，非法值回落默认，不做字符串拼接 */
    private static final Set<String> SORTS = Set.of(
            "updatedAt_desc", "createdAt_desc", "difficulty_asc",
            "difficulty_desc", "nextReviewAt_asc", "solvedAt_desc");

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemListItemMapper problemListItemMapper;
    private final TagMapper tagMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final ProblemConverter converter;

    // ---------------- 查询 ----------------

    @Override
    public PageResult<ProblemVO> list(ProblemQuery raw) {
        ProblemQuery q = normalize(raw);
        // count 与 LIMIT 由 PaginationInnerInterceptor 补齐；页码越界时 records 为空、total 仍准确
        IPage<Problem> page = problemMapper.selectPageList(new Page<>(q.safePage(), q.safeSize()), q);

        List<Problem> rows = page.getRecords();
        Map<String, List<TagRow>> tags = loadTags(rows.stream().map(Problem::getId).toList());
        List<ProblemVO> items = rows.stream()
                .map(p -> converter.toVO(p, tags.getOrDefault(p.getId(), List.of())))
                .toList();
        return PageResult.of(items, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public ProblemVO get(String id) {
        Problem p = problemMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("题目");
        }
        return converter.toVO(p, problemTagMapper.selectByProblemIds(List.of(id)));
    }

    /**
     * 随机一题，四级级联：未刷未抽 → 未刷重置 → 全库未抽 → 全库重置 → null。
     */
    @Override
    public ProblemVO random(String pool, List<String> excludeIds) {
        List<String> exclude = excludeIds == null ? List.of()
                : excludeIds.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().toList();

        Problem picked = null;
        if (!"all".equalsIgnoreCase(pool)) {
            picked = problemMapper.selectRandomOne("todo", exclude);
            if (picked == null) {
                picked = problemMapper.selectRandomOne("todo", List.of());
            }
        }
        if (picked == null) {
            picked = problemMapper.selectRandomOne(null, exclude);
        }
        if (picked == null) {
            picked = problemMapper.selectRandomOne(null, List.of());
        }
        if (picked == null) {
            return null;
        }
        return converter.toVO(picked, problemTagMapper.selectByProblemIds(List.of(picked.getId())));
    }

    // ---------------- 写 ----------------

    @Override
    @Transactional
    public ProblemVO create(CreateProblemRequest req) {
        String title = trim(req.getTitle());
        if (title == null || title.isEmpty()) {
            throw BizException.invalid("标题不能为空");
        }
        String difficulty = trim(req.getDifficulty());
        if (difficulty == null) {
            difficulty = "medium";
        }
        if (!DIFFICULTIES.contains(difficulty)) {
            throw BizException.invalid("难度只能是 easy/medium/hard");
        }

        LocalDateTime now = TimeUtil.now();
        Problem p = new Problem();
        p.setId(UUID.randomUUID().toString());
        p.setTitle(title);
        p.setDifficulty(difficulty);
        p.setStatus("todo");
        p.setApproach(trim(req.getApproach()));
        p.setNote(trim(req.getNote()));
        p.setSource(trim(req.getSource()));
        p.setUrl(trim(req.getUrl()));
        p.setSolveCount(0);
        p.setReviewStage(0);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        p.setDeleted(0);
        problemMapper.insert(p);

        replaceTags(p.getId(), req.getTagIds());
        return get(p.getId());
    }

    @Override
    @Transactional
    public ProblemVO update(String id, UpdateProblemRequest req) {
        Problem p = problemMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("题目");
        }
        if (req.getTitle() != null) {
            String title = trim(req.getTitle());
            if (title == null || title.isEmpty()) {
                throw BizException.invalid("标题不能为空");
            }
            p.setTitle(title);
        }
        if (req.getDifficulty() != null) {
            p.setDifficulty(req.getDifficulty());
        }
        if (req.getApproach() != null) {
            p.setApproach(trim(req.getApproach()));
        }
        if (req.getNote() != null) {
            p.setNote(trim(req.getNote()));
        }
        if (req.getSource() != null) {
            p.setSource(trim(req.getSource()));
        }
        if (req.getUrl() != null) {
            p.setUrl(trim(req.getUrl()));
        }
        p.setUpdatedAt(TimeUtil.now());
        problemMapper.updateById(p);

        if (req.getTagIds() != null) {
            replaceTags(id, req.getTagIds());
        }
        return get(id);
    }

    /** 逻辑删 + 清关联；复习流水保留，作为历史 */
    @Override
    @Transactional
    public void remove(String id) {
        Problem p = problemMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("题目");
        }
        problemMapper.deleteById(id);
        problemTagMapper.deleteByProblemId(id);
        problemListItemMapper.deleteByProblemId(id);
    }

    /**
     * 标记已刷。首次写 solvedAt 并进复习池（明天到期）；
     * 重复刷只累加 solveCount、刷新 lastSolvedAt，不动复习排期。
     */
    @Override
    @Transactional
    public MarkSolvedVO markSolved(String id) {
        Problem p = problemMapper.selectForUpdate(id);
        if (p == null) {
            throw BizException.notFound("题目");
        }
        LocalDateTime now = TimeUtil.now();
        boolean firstSolve = p.getSolvedAt() == null;
        int fromStage = p.getReviewStage() == null ? 0 : p.getReviewStage();

        ReviewScheduler.Outcome outcome = firstSolve ? ReviewScheduler.onFirstSolve(now) : null;

        p.setStatus("solved");
        p.setSolvedAt(firstSolve ? now : p.getSolvedAt());
        p.setLastSolvedAt(now);
        p.setSolveCount((p.getSolveCount() == null ? 0 : p.getSolveCount()) + 1);
        if (firstSolve) {
            p.setReviewStage(outcome.stage());
            p.setNextReviewAt(outcome.nextReviewAt());
        }
        p.setUpdatedAt(now);
        problemMapper.updateById(p);

        ReviewRecord record = new ReviewRecord();
        record.setId(UUID.randomUUID().toString());
        record.setProblemId(id);
        record.setKind("MARK_SOLVED");
        record.setFromStage(fromStage);
        record.setToStage(p.getReviewStage());
        record.setIntervalDays(outcome == null ? null : outcome.intervalDays());
        record.setNextReviewAt(p.getNextReviewAt());
        record.setReviewedAt(now);
        record.setCreatedAt(now);
        reviewRecordMapper.insert(record);

        ProblemVO vo = converter.toVO(p, problemTagMapper.selectByProblemIds(List.of(id)));
        return new MarkSolvedVO(vo, firstSolve, TimeUtil.toMillis(p.getNextReviewAt()));
    }

    /** 取消已刷。solvedAt / solveCount 是历史事实，不抹掉；退出复习池 */
    @Override
    @Transactional
    public ProblemVO markUnsolved(String id) {
        Problem p = problemMapper.selectForUpdate(id);
        if (p == null) {
            throw BizException.notFound("题目");
        }
        if ("todo".equals(p.getStatus())) {
            return converter.toVO(p, problemTagMapper.selectByProblemIds(List.of(id)));
        }
        LocalDateTime now = TimeUtil.now();
        int fromStage = p.getReviewStage() == null ? 0 : p.getReviewStage();

        p.setStatus("todo");
        p.setReviewStage(0);
        p.setNextReviewAt(null);
        p.setUpdatedAt(now);
        problemMapper.updateById(p);

        ReviewRecord record = new ReviewRecord();
        record.setId(UUID.randomUUID().toString());
        record.setProblemId(id);
        record.setKind("MARK_UNSOLVED");
        record.setFromStage(fromStage);
        record.setToStage(0);
        record.setReviewedAt(now);
        record.setCreatedAt(now);
        reviewRecordMapper.insert(record);

        return converter.toVO(p, problemTagMapper.selectByProblemIds(List.of(id)));
    }

    // ---------------- 内部 ----------------

    private ProblemQuery normalize(ProblemQuery q) {
        ProblemQuery query = q == null ? new ProblemQuery() : q;
        query.setKeyword(trim(query.getKeyword()));
        query.setDifficulties(cleanList(query.getDifficulties(), s -> DIFFICULTIES.contains(s.toLowerCase()), String::toLowerCase));
        query.setStatuses(cleanList(query.getStatuses(), s -> "todo".equals(s) || "solved".equals(s), String::toLowerCase));
        query.setTagIds(cleanList(query.getTagIds(), s -> true, s -> s));
        if (query.getSort() == null || !SORTS.contains(query.getSort())) {
            query.setSort("updatedAt_desc");
        }
        return query;
    }

    private List<String> cleanList(List<String> raw, java.util.function.Predicate<String> keep,
                                   Function<String, String> mapper) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(mapper)
                .filter(keep)
                .distinct()
                .toList();
    }

    /** 用标签表校验一遍，避免挂上不存在的 tagId */
    private void replaceTags(String problemId, List<String> tagIds) {
        problemTagMapper.deleteByProblemId(problemId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<String> wanted = tagIds.stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .distinct().limit(20).toList();
        if (wanted.isEmpty()) {
            return;
        }
        List<Tag> existing = tagMapper.selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getId, wanted));
        List<String> valid = existing.stream().map(Tag::getId).toList();
        if (!valid.isEmpty()) {
            problemTagMapper.insertIgnore(problemId, valid);
        }
    }

    private Map<String, List<TagRow>> loadTags(List<String> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }
        return problemTagMapper.selectByProblemIds(problemIds).stream()
                .collect(Collectors.groupingBy(TagRow::getProblemId));
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        return s.trim();
    }
}
