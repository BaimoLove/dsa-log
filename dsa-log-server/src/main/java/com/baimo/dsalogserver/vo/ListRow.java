package com.baimo.dsalogserver.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题单查询投影：表列 + 现算的进度。
 *
 * <p>进度必须来自 JOIN 后的 {@code COUNT(p.id)}，不是 {@code COUNT(*)}：
 * LEFT JOIN 到已逻辑删除的题目时会产出一行 p.id 为 NULL 的占位行，
 * {@code COUNT(*)} 会把它算进分母，{@code COUNT(p.id)} 不会。
 * 单独一个类而不复用实体，是为了不让「查询投影字段」污染实体定义。
 */
@Data
public class ListRow {

    private String id;
    private String name;
    private String description;
    private String color;
    private Integer sortOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 题单内未删题目数 */
    private long total;

    /** 其中已刷的题数 */
    private long solved;
}
