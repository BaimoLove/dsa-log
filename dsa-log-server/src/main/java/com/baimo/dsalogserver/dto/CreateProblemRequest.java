package com.baimo.dsalogserver.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建题目。注意：没有 content 字段——题目只存题名，不存题干原文。
 */
@Data
public class CreateProblemRequest {

    @Size(max = 255, message = "标题最长 255 字符")
    private String title;

    @Pattern(regexp = "easy|medium|hard", message = "难度只能是 easy/medium/hard")
    private String difficulty;

    @Size(max = 200000, message = "解题思路过长")
    private String approach;

    @Size(max = 20000, message = "备注过长")
    private String note;

    @Size(max = 128, message = "来源最长 128 字符")
    private String source;

    @Size(max = 1024, message = "链接最长 1024 字符")
    private String url;

    private List<String> tagIds;
}
