package com.baimo.dsalogserver.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签列表的查询投影：标签本身 + 引用它的未删题目数。
 * 单独一层是为了让 VO 里不出现 LocalDateTime（出参时间一律 epoch 毫秒）。
 */
@Data
public class TagUsageRow {

    private String id;
    private String name;
    private String color;
    private LocalDateTime createdAt;
    /** COUNT(p.id)：已删题目不计入 */
    private long usageCount;
}
