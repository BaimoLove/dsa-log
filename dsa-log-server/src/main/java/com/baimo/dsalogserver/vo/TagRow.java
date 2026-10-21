package com.baimo.dsalogserver.vo;

import lombok.Data;

/**
 * 标签挂载行：一次查出一批题目的标签，避免 N+1。
 */
@Data
public class TagRow {

    private String problemId;
    private String id;
    private String name;
    private String color;
}
