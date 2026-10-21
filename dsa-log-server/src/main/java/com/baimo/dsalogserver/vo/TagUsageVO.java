package com.baimo.dsalogserver.vo;

import lombok.Data;

/**
 * 标签出参：与前端 TagWithUsage 对齐（createdAt 为 epoch 毫秒，usageCount 为未删题目数）。
 * 列表按使用次数降序、再按名称升序，删除按钮就在这个顺序下用。
 */
@Data
public class TagUsageVO {

    private String id;
    private String name;
    private String color;
    private Long createdAt;
    private long usageCount;
}
