package com.baimo.dsalogserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 标记已刷的返回：firstSolve 表示这次是否首次，前端据此决定提示文案。
 */
@Data
@AllArgsConstructor
public class MarkSolvedVO {

    private ProblemVO problem;
    private boolean firstSolve;
    private Long nextReviewAt;
}
