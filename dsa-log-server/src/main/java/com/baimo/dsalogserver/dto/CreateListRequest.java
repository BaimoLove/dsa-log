package com.baimo.dsalogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新建题单。sortOrder 由服务端取 max+1，不让前端指定 */
@Data
public class CreateListRequest {

    @NotBlank(message = "题单名不能为空")
    @Size(max = 64, message = "题单名不能超过 64 个字符")
    private String name;

    @Size(max = 512, message = "描述不能超过 512 个字符")
    private String description;

    /** 缺省 #6366f1，与前端色板第一项一致 */
    @Size(max = 16, message = "颜色值不合法")
    private String color;
}
