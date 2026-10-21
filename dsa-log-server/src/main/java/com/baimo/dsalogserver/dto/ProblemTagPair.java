package com.baimo.dsalogserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条「题目-标签」关联。导入时每道题的标签各不相同，
 * 不能用「题目 × 标签」的笛卡尔积批量插，只能逐对传入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemTagPair {

    private String problemId;
    private String tagId;
}
