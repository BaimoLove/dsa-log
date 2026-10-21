package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.TimeUtil;

import java.time.LocalDateTime;

/**
 * 间隔重复算法：阶段 0-4，间隔 1/3/7/15 天。
 * 纯静态函数，不碰数据库，便于参数化单测；前端保留同构实现做乐观预演。
 */
public final class ReviewScheduler {

    public static final int[] INTERVALS = {1, 3, 7, 15};
    public static final int MAX_STAGE = 4;

    private ReviewScheduler() {
    }

    /** stage 4 复用最后一个间隔 */
    public static int intervalOf(int stage) {
        int idx = Math.min(Math.max(stage, 0), INTERVALS.length - 1);
        return INTERVALS[idx];
    }

    public record Outcome(int stage, int intervalDays, LocalDateTime nextReviewAt) {
    }

    /** 首次标记已刷：进阶段 0，明天复习 */
    public static Outcome onFirstSolve(LocalDateTime now) {
        int interval = intervalOf(0);
        return new Outcome(0, interval, now.plusDays(interval));
    }

    /**
     * 复习评分。逾期不补算欠账，一律从 now 起算。
     * good 升一阶 / hard 退一阶 / again 归零并落到次日零点
     */
    public static Outcome rate(int stage, String rating, LocalDateTime now) {
        if (rating == null) {
            throw BizException.invalid("rating 不能为空");
        }
        return switch (rating) {
            case "good" -> next(Math.min(MAX_STAGE, stage + 1), now);
            case "hard" -> next(Math.max(0, stage - 1), now);
            case "again" -> new Outcome(0, 1, TimeUtil.startOfNextDay(now));
            default -> throw BizException.invalid("rating 只能是 good/hard/again");
        };
    }

    private static Outcome next(int stage, LocalDateTime now) {
        int interval = intervalOf(stage);
        return new Outcome(stage, interval, now.plusDays(interval));
    }
}
