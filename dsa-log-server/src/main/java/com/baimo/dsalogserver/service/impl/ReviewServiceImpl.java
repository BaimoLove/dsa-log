package com.baimo.dsalogserver.service.impl;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.ProblemConverter;
import com.baimo.dsalogserver.dto.RateReviewRequest;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.entity.ReviewRecord;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.service.ReviewScheduler;
import com.baimo.dsalogserver.service.ReviewService;
import com.baimo.dsalogserver.vo.DueItemVO;
import com.baimo.dsalogserver.vo.DueReviewVO;
import com.baimo.dsalogserver.vo.ProblemVO;
import com.baimo.dsalogserver.vo.RateVO;
import com.baimo.dsalogserver.vo.ReviewRecordVO;
import com.baimo.dsalogserver.vo.TagRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 复习业务实现。
 *
 * <p>评分的三个约束：
 * <ol>
 *   <li>行锁 + 事务：同一题并发点两次评分，只会推进一档。</li>
 *   <li>不在池里（nextReviewAt 为 null）直接 400：给它评分等于凭空造出排期。</li>
 *   <li>逾期不补算欠账，一律从 now 起算——算法里就这么定的，这里不再叠一层。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final ProblemConverter problemConverter;

    @Override
    public DueReviewVO due(Integer limit) {
        LocalDateTime now = TimeUtil.now();
        int capped = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        List<Problem> rows = problemMapper.selectDue(now, capped);
        long total = problemMapper.countDue(now);

        Map<String, List<TagRow>> tags = rows.isEmpty() ? Map.of()
                : problemTagMapper.selectByProblemIds(rows.stream().map(Problem::getId).toList())
                .stream().collect(Collectors.groupingBy(TagRow::getProblemId));

        DueReviewVO vo = new DueReviewVO();
        vo.setTotal(total);
        vo.setLimited(rows.size());
        for (Problem p : rows) {
            DueItemVO item = new DueItemVO();
            item.setProblem(problemConverter.toVO(p, tags.getOrDefault(p.getId(), List.of())));
            item.setDueAt(TimeUtil.toMillis(p.getNextReviewAt()));
            // 按自然日算逾期：今天到期的题 overdueDays=0，不算逾期
            int days = (int) ChronoUnit.DAYS.between(p.getNextReviewAt().toLocalDate(), now.toLocalDate());
            item.setOverdueDays(Math.max(0, days));
            item.setIsOverdue(days > 0);
            vo.getItems().add(item);
        }
        return vo;
    }

    @Override
    @Transactional
    public RateVO rate(String problemId, RateReviewRequest req) {
        Problem p = problemMapper.selectForUpdate(problemId);
        if (p == null) {
            throw BizException.notFound("题目");
        }
        if (!"solved".equals(p.getStatus()) || p.getNextReviewAt() == null) {
            throw BizException.invalid("这道题不在复习队列里");
        }

        LocalDateTime now = TimeUtil.now();
        int fromStage = p.getReviewStage() == null ? 0 : p.getReviewStage();
        ReviewScheduler.Outcome outcome = ReviewScheduler.rate(fromStage, req.getRating(), now);

        p.setReviewStage(outcome.stage());
        p.setNextReviewAt(outcome.nextReviewAt());
        p.setLastReviewedAt(now);
        p.setUpdatedAt(now);
        problemMapper.updateById(p);

        ReviewRecord record = new ReviewRecord();
        record.setId(UUID.randomUUID().toString());
        record.setProblemId(problemId);
        record.setKind("REVIEW");
        record.setRating(req.getRating());
        record.setFromStage(fromStage);
        record.setToStage(outcome.stage());
        record.setIntervalDays(outcome.intervalDays());
        record.setNextReviewAt(outcome.nextReviewAt());
        record.setReviewedAt(now);
        record.setCreatedAt(now);
        reviewRecordMapper.insert(record);

        RateVO vo = new RateVO();
        vo.setProblem(problemConverter.toVO(p, problemTagMapper.selectByProblemIds(List.of(problemId))));
        vo.setRecord(toRecordVO(record));
        vo.setFromStage(fromStage);
        vo.setToStage(outcome.stage());
        vo.setIntervalDays(outcome.intervalDays());
        vo.setNextReviewAt(TimeUtil.toMillis(outcome.nextReviewAt()));
        return vo;
    }

    @Override
    public List<ReviewRecordVO> history(String problemId) {
        String id = problemId == null ? "" : problemId.trim();
        if (id.isEmpty()) {
            throw BizException.invalid("problemId 不能为空");
        }
        return reviewRecordMapper.selectByProblemId(id).stream().map(this::toRecordVO).toList();
    }

    private ReviewRecordVO toRecordVO(ReviewRecord r) {
        ReviewRecordVO vo = new ReviewRecordVO();
        vo.setId(r.getId());
        vo.setProblemId(r.getProblemId());
        vo.setKind(r.getKind());
        vo.setRating(r.getRating());
        vo.setFromStage(r.getFromStage());
        vo.setToStage(r.getToStage());
        vo.setIntervalDays(r.getIntervalDays());
        vo.setNextReviewAt(TimeUtil.toMillis(r.getNextReviewAt()));
        vo.setReviewedAt(TimeUtil.toMillis(r.getReviewedAt()));
        return vo;
    }
}
