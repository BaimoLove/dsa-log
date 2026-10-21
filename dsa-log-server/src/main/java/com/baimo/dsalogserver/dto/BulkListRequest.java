package com.baimo.dsalogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 批量加入题单 */
@Data
public class BulkListRequest {

    @NotEmpty(message = "ids 不能为空")
    private List<String> ids;

    @NotBlank(message = "listId 不能为空")
    private String listId;
}
