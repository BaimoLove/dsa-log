package com.baimo.dsalogserver.dto;

import lombok.Data;

/** 新建标签：颜色可省，缺省用前端色板第一项 */
@Data
public class CreateTagRequest {

    private String name;
    private String color;
}
