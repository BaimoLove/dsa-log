package com.baimo.dsalogserver.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 题单部分更新：字段为 null 表示不改，这一点与 {@code PATCH /api/problems/{id}} 一致。
 *
 * <p>不校验 {@code @NotBlank}：注解无法区分「没传」与「传了空串」，
 * 会把「只改颜色」的请求也判成非法。名称的非空校验放在 service 里，
 * 只在字段真的出现时才执行。
 */
@Data
public class UpdateListRequest {

    @Size(max = 64, message = "题单名不能超过 64 个字符")
    private String name;

    @Size(max = 512, message = "描述不能超过 512 个字符")
    private String description;

    @Size(max = 16, message = "颜色值不合法")
    private String color;
}
