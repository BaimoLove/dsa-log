package com.baimo.dsalogserver.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 时间工具。全项目只认这三点：
 * 1. 数据库 DATETIME(3)，Java LocalDateTime，时区 Asia/Shanghai
 * 2. 出参一律 epoch 毫秒（Long），绝不出现 ISO 字符串
 * 3. “今日”边界由这里算，SQL 里禁止对索引列套函数
 */
public final class TimeUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TimeUtil() {
    }

    /**
     * 当前时间，截断到毫秒。
     *
     * <p>这里原本写的是 {@code withNano(0)}——它把毫秒也一起清零了，于是同一秒内的
     * 多次写入时间戳完全同值：列表按 updated_at / created_at 排序时并列，MySQL 返回
     * 顺序不确定；备份 merge 按 updatedAt 比新旧也会被判成「同时」。
     * DATETIME(3) 本来就存到毫秒，截掉毫秒没有任何好处。
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    public static Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZONE).toInstant().toEpochMilli();
    }

    public static LocalDateTime fromMillis(Long millis) {
        return millis == null ? null : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZONE);
    }

    /** 今日 00:00:00.000 */
    public static LocalDateTime startOfToday() {
        return LocalDate.now(ZONE).atStartOfDay();
    }

    /** 明日 00:00:00.000 */
    public static LocalDateTime startOfTomorrow() {
        return LocalDate.now(ZONE).plusDays(1).atStartOfDay();
    }

    /** 指定时刻所在日期的次日 00:00:00.000（again 评分用） */
    public static LocalDateTime startOfNextDay(LocalDateTime ref) {
        return ref.toLocalDate().plusDays(1).atStartOfDay();
    }
}
