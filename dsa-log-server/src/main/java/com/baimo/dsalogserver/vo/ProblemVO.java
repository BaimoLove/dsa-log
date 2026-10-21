package com.baimo.dsalogserver.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 题目出参。时间一律 epoch 毫秒；tagIds 供筛选回填，tags 供渲染，两者都返回省一次请求。
 */
@Data
public class ProblemVO {

    private String id;
    private String title;
    private String difficulty;
    private String status;

    private String approach;
    private String note;
    private String source;
    private String url;

    private Long solvedAt;
    private Long lastSolvedAt;
    private Integer solveCount;
    private Integer reviewStage;
    private Long nextReviewAt;

    private Long createdAt;
    private Long updatedAt;

    private List<String> tagIds = new ArrayList<>();
    private List<TagVO> tags = new ArrayList<>();
}
