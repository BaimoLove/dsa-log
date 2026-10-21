package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 复习算法单测：间隔表、三档评分、阶段边界、again 落次日零点。
 */
class ReviewSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 14, 30, 0);

    @ParameterizedTest
    @CsvSource({"0,1", "1,3", "2,7", "3,15", "4,15", "-1,1"})
    @DisplayName("间隔表：stage 4 复用 15 天，越界夹到边界")
    void intervalOf(int stage, int expected) {
        assertEquals(expected, ReviewScheduler.intervalOf(stage));
    }

    @Test
    @DisplayName("首次标记已刷：阶段 0，明天复习")
    void onFirstSolve() {
        ReviewScheduler.Outcome o = ReviewScheduler.onFirstSolve(NOW);
        assertEquals(0, o.stage());
        assertEquals(1, o.intervalDays());
        assertEquals(NOW.plusDays(1), o.nextReviewAt());
    }

    @ParameterizedTest
    @CsvSource({"0,1,3", "1,2,7", "2,3,15", "3,4,15", "4,4,15"})
    @DisplayName("good 升一阶，上限 4")
    void good(int stage, int expectStage, int expectInterval) {
        ReviewScheduler.Outcome o = ReviewScheduler.rate(stage, "good", NOW);
        assertEquals(expectStage, o.stage());
        assertEquals(expectInterval, o.intervalDays());
        assertEquals(NOW.plusDays(expectInterval), o.nextReviewAt());
    }

    @ParameterizedTest
    @CsvSource({"4,3,15", "3,2,7", "1,0,1", "0,0,1"})
    @DisplayName("hard 退一阶，下限 0")
    void hard(int stage, int expectStage, int expectInterval) {
        ReviewScheduler.Outcome o = ReviewScheduler.rate(stage, "hard", NOW);
        assertEquals(expectStage, o.stage());
        assertEquals(expectInterval, o.intervalDays());
    }

    @Test
    @DisplayName("again 归零，落到次日 00:00 而不是 now+24h")
    void again() {
        ReviewScheduler.Outcome o = ReviewScheduler.rate(3, "again", NOW);
        assertEquals(0, o.stage());
        assertEquals(LocalDateTime.of(2026, 10, 22, 0, 0, 0), o.nextReviewAt());
    }

    @Test
    @DisplayName("逾期不补欠账：从传入的 now 起算")
    void overdueRecalculatedFromNow() {
        LocalDateTime now = NOW.plusDays(10);
        ReviewScheduler.Outcome o = ReviewScheduler.rate(1, "good", now);
        assertEquals(now.plusDays(7), o.nextReviewAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "GOOD", "easy", "forgot"})
    @DisplayName("非法评分直接拒绝")
    void invalidRating(String rating) {
        assertThrows(BizException.class, () -> ReviewScheduler.rate(1, rating, NOW));
    }

    @Test
    @DisplayName("null 评分拒绝")
    void nullRating() {
        assertThrows(BizException.class, () -> ReviewScheduler.rate(1, null, NOW));
    }
}
