package com.baimo.dsalogserver.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 只带 ids 的批量请求（已刷 / 取消已刷 / 删除共用）。
 * 数量上限不在这里校验：超出是 413 而不是 400，两种语义不在同一个注解里混。
 */
@Data
public class BulkIdsRequest {

    @NotEmpty(message = "ids 不能为空")
    private List<String> ids;
}
