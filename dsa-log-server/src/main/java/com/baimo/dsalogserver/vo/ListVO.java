package com.baimo.dsalogserver.vo;

import lombok.Data;

/**
 * 题单出参。字段与前端 {@code ProblemListBasic + total/solved} 一一对应，
 * 时间一律 epoch 毫秒。看板上的「12/30」与题单内的计数同源于此，不另算一遍。
 */
@Data
public class ListVO {

    private String id;
    private String name;
    private String description;
    private String color;
    private Integer sortOrder;

    private Long createdAt;
    private Long updatedAt;

    private long total;
    private long solved;
}
