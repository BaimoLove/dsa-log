package com.baimo.dsalogserver.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 部分更新。约定：字段为 null = 不改；清空文本传 ""；tagIds 传 [] = 清空标签。
 */
@Data
public class UpdateProblemRequest {

    @Size(min = 1, max = 255, message = "标题长度需在 1..255 之间")
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

    /** null = 不改标签；[] = 清空 */
    private List<String> tagIds;
}
