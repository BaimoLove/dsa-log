package com.baimo.dsalogserver.dto;

import lombok.Data;

/**
 * 更新标签：null = 不改。
 *
 * <p>不加 {@code @NotBlank}：注解分不清「没传」与「传了空串」，
 * 而这里的空串要当作校验失败给出明确消息，交由 service 判断。
 */
@Data
public class UpdateTagRequest {

    private String name;
    private String color;
}
