package com.baimo.dsalogserver.vo;

import lombok.Data;

/**
 * 复习流水出参。时间一律 epoch 毫秒，可空字段保持 null（不塞 0）。
 */
@Data
public class ReviewRecordVO {

    private String id;
    private String problemId;
    /** MARK_SOLVED / MARK_UNSOLVED / REVIEW */
    private String kind;
    /** good / hard / again；非 REVIEW 流水为 null */
    private String rating;
    private Integer fromStage;
    private Integer toStage;
    private Integer intervalDays;
    private Long nextReviewAt;
    private Long reviewedAt;
}
