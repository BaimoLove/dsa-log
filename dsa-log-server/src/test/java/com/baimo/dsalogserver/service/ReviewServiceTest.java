package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.ErrorCode;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.ProblemConverter;
import com.baimo.dsalogserver.dto.RateReviewRequest;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.entity.ReviewRecord;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.service.impl.ReviewServiceImpl;
import com.baimo.dsalogserver.vo.DueReviewVO;
import com.baimo.dsalogserver.vo.RateVO;
import com.baimo.dsalogserver.vo.ReviewRecordVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 复习业务契约。
 *
 * <p>算法本身由 {@link ReviewSchedulerTest} 的 30 组参数化用例覆盖，
 * 这里只钉「什么情况该拒、写入哪些字段、逾期天数怎么算、limit 怎么夹」。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemTagMapper problemTagMapper;
    @Mock
    private ReviewRecordMapper reviewRecordMapper;
    @Spy
    private ProblemConverter converter = new ProblemConverter();

    @InjectMocks
    private ReviewServiceImpl service;

    private static Problem solved(String id, int stage, LocalDateTime nextReviewAt) {
        Problem p = new Problem();
        p.setId(id);
        p.setTitle("两数之和");
        p.setDifficulty("easy");
        p.setStatus("solved");
        p.setSolveCount(1);
        p.setReviewStage(stage);
        p.setNextReviewAt(nextReviewAt);
        p.setCreatedAt(nextReviewAt);
        p.setUpdatedAt(nextReviewAt);
        return p;
    }

    private static RateReviewRequest rating(String rating) {
        RateReviewRequest req = new RateReviewRequest();
        req.setRating(rating);
        return req;
    }

    // ---------------- due ----------------

    @Test
    @DisplayName("due：total 取 countDue（不受 limit 影响），items 按 limit 截断")
    void dueTotalIndependentOfLimit() {
        LocalDateTime due = TimeUtil.now().minusDays(2);
        when(problemMapper.selectDue(any(), eq(5))).thenReturn(List.of(solved("p1", 1, due), solved("p2", 1, due)));
        when(problemMapper.countDue(any())).thenReturn(37L);
        when(problemTagMapper.selectByProblemIds(anyList())).thenReturn(List.of());

        DueReviewVO vo = service.due(5);
        assertThat(vo.getTotal()).isEqualTo(37);
        assertThat(vo.getLimited()).isEqualTo(2);
        assertThat(vo.getItems()).hasSize(2);
        assertThat(vo.getItems().get(0).getDueAt()).isEqualTo(TimeUtil.toMillis(due));
    }

    @Test
    @DisplayName("due：逾期按自然日算，今天到期不算逾期")
    void dueOverdueDays() {
        when(problemMapper.selectDue(any(), anyInt())).thenReturn(List.of(
                solved("p1", 1, TimeUtil.now().minusDays(3)),
                solved("p2", 1, TimeUtil.now())));
        when(problemMapper.countDue(any())).thenReturn(2L);
        when(problemTagMapper.selectByProblemIds(anyList())).thenReturn(List.of());

        DueReviewVO vo = service.due(null);
        assertThat(vo.getItems().get(0).getOverdueDays()).isEqualTo(3);
        assertThat(vo.getItems().get(0).getIsOverdue()).isTrue();
        assertThat(vo.getItems().get(1).getOverdueDays()).isZero();
        assertThat(vo.getItems().get(1).getIsOverdue()).isFalse();
    }

    @Test
    @DisplayName("due：limit 夹到 1..200，null/0 走默认 100")
    void dueLimitClamp() {
        when(problemMapper.selectDue(any(), anyInt())).thenReturn(List.of());
        when(problemMapper.countDue(any())).thenReturn(0L);

        service.due(0);
        verify(problemMapper).selectDue(any(), eq(100));
        service.due(999);
        verify(problemMapper).selectDue(any(), eq(200));
        service.due(null);
        verify(problemMapper, times(2)).selectDue(any(), eq(100));
    }

    // ---------------- rate ----------------

    @Test
    @DisplayName("rate：good 升阶，写 REVIEW 流水并刷新 lastReviewedAt")
    void rateGood() {
        Problem p = solved("p1", 2, TimeUtil.now().minusDays(1));
        when(problemMapper.selectForUpdate("p1")).thenReturn(p);
        when(problemTagMapper.selectByProblemIds(anyList())).thenReturn(List.of());

        RateVO vo = service.rate("p1", rating("good"));

        assertThat(vo.getFromStage()).isEqualTo(2);
        assertThat(vo.getToStage()).isEqualTo(3);
        assertThat(vo.getIntervalDays()).isEqualTo(15);
        assertThat(vo.getNextReviewAt()).isEqualTo(TimeUtil.toMillis(p.getNextReviewAt()));
        assertThat(p.getLastReviewedAt()).isNotNull();
        verify(problemMapper).updateById(p);

        ArgumentCaptor<ReviewRecord> captor = ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordMapper).insert(captor.capture());
        ReviewRecord record = captor.getValue();
        assertThat(record.getKind()).isEqualTo("REVIEW");
        assertThat(record.getRating()).isEqualTo("good");
        assertThat(record.getFromStage()).isEqualTo(2);
        assertThat(record.getToStage()).isEqualTo(3);
        assertThat(record.getIntervalDays()).isEqualTo(15);
        assertThat(vo.getRecord().getKind()).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("rate：again 归零并落到次日 00:00:00.000")
    void rateAgainLandsOnNextMidnight() {
        Problem p = solved("p1", 3, TimeUtil.now().minusDays(5));
        when(problemMapper.selectForUpdate("p1")).thenReturn(p);
        when(problemTagMapper.selectByProblemIds(anyList())).thenReturn(List.of());

        service.rate("p1", rating("again"));

        assertThat(p.getReviewStage()).isZero();
        assertThat(p.getNextReviewAt().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(p.getNextReviewAt().toLocalDate())
                .isEqualTo(TimeUtil.now().toLocalDate().plusDays(1));
    }

    @Test
    @DisplayName("rate：题目不存在 404、未刷/不在池 400，且都不写流水")
    void rateRejects() {
        when(problemMapper.selectForUpdate("nope")).thenReturn(null);
        assertThatThrownBy(() -> service.rate("nope", rating("good")))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        Problem todo = solved("p2", 0, null);
        todo.setStatus("todo");
        when(problemMapper.selectForUpdate("p2")).thenReturn(todo);
        assertThatThrownBy(() -> service.rate("p2", rating("good")))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        // 已刷但排期被清空（取消后重标记前）也算不在池
        Problem noSchedule = solved("p3", 0, null);
        when(problemMapper.selectForUpdate("p3")).thenReturn(noSchedule);
        assertThatThrownBy(() -> service.rate("p3", rating("good")))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(reviewRecordMapper, never()).insert(any(ReviewRecord.class));
    }

    @Test
    @DisplayName("rate：非法 rating 由算法层抛 400")
    void rateInvalidRating() {
        when(problemMapper.selectForUpdate("p1")).thenReturn(solved("p1", 1, TimeUtil.now().minusDays(1)));
        assertThatThrownBy(() -> service.rate("p1", rating("easy")))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(reviewRecordMapper, never()).insert(any(ReviewRecord.class));
    }

    // ---------------- history ----------------

    @Test
    @DisplayName("history：按时间倒序映射，空 problemId 400")
    void history() {
        ReviewRecord newer = new ReviewRecord();
        newer.setId("r2");
        newer.setProblemId("p1");
        newer.setKind("REVIEW");
        newer.setRating("good");
        newer.setReviewedAt(LocalDateTime.of(2026, 10, 21, 12, 0, 0));
        newer.setNextReviewAt(LocalDateTime.of(2026, 10, 24, 12, 0, 0));

        ReviewRecord older = new ReviewRecord();
        older.setId("r1");
        older.setProblemId("p1");
        older.setKind("MARK_SOLVED");
        older.setReviewedAt(LocalDateTime.of(2026, 10, 20, 12, 0, 0));

        when(reviewRecordMapper.selectByProblemId("p1")).thenReturn(List.of(newer, older));

        List<ReviewRecordVO> list = service.history(" p1 ");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getKind()).isEqualTo("REVIEW");
        assertThat(list.get(0).getReviewedAt())
                .isEqualTo(TimeUtil.toMillis(LocalDateTime.of(2026, 10, 21, 12, 0, 0)));
        assertThat(list.get(1).getRating()).isNull();

        assertThatThrownBy(() -> service.history("  "))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        // 空 id 在查库之前就被拦下，只有上面那次 "p1" 真正打到 mapper
        verify(reviewRecordMapper).selectByProblemId("p1");
    }
}
