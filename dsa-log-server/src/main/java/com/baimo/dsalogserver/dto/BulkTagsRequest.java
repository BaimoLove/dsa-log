package com.baimo.dsalogserver.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量打标签：标签是追加语义，不是覆盖 */
@Data
public class BulkTagsRequest {

    @NotEmpty(message = "ids 不能为空")
    private List<String> ids;

    @NotEmpty(message = "tagIds 不能为空")
    @Size(max = 20, message = "一次最多加 20 个标签")
    private List<String> tagIds;
}
