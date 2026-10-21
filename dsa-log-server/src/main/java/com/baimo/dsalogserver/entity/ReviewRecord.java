package com.baimo.dsalogserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 复习流水，只增不改。
 */
@Data
@TableName("review_record")
public class ReviewRecord {

    @TableId(type = IdType.INPUT)
    private String id;

    private String problemId;
    private String kind;        // MARK_SOLVED / MARK_UNSOLVED / REVIEW
    private String rating;      // good / hard / again
    private Integer fromStage;
    private Integer toStage;
    private Integer intervalDays;
    private LocalDateTime nextReviewAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
