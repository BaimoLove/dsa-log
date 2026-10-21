package com.baimo.dsalogserver.vo;

import lombok.Data;

/**
 * 评分结果。problem 是写库后的完整行（前端整体替换本地对象即可，
 * 不必自己推演 nextReviewAt）；record 是这次写下的流水。
 */
@Data
public class RateVO {

    private ProblemVO problem;
    private ReviewRecordVO record;
    private int fromStage;
    private int toStage;
    private int intervalDays;
    private Long nextReviewAt;
}
