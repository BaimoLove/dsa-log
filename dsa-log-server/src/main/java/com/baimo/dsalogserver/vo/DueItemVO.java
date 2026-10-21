package com.baimo.dsalogserver.vo;

import lombok.Data;

/**
 * 待复习队列中的一项。
 *
 * <p>{@code overdueDays} 按自然日算（今日 - 到期日），{@code isOverdue} 即
 * {@code overdueDays > 0}；当天到期的题 overdueDays 为 0、不算逾期，
 * 这样前端不需要再自己比较一次日期。
 */
@Data
public class DueItemVO {

    private ProblemVO problem;
    private Long dueAt;
    private int overdueDays;
    /**
     * 用包装类型而不是 {@code boolean}：Lombok 给原始 boolean 生成的是
     * {@code isOverdue()}，Jackson 会把它序列化成 {@code overdue}，
     * 与契约里的 {@code isOverdue} 差一个前缀。Boolean 生成 {@code getIsOverdue()}，
     * 属性名才是 isOverdue。
     */
    private Boolean isOverdue;
}
