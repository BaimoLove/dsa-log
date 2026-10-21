package com.baimo.dsalogserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目。只存题名与外链，不存题干原文。
 * 时间字段在库内是 DATETIME(3)，出参由 Converter 转 epoch 毫秒。
 */
@Data
@TableName("problem")
public class Problem {

    @TableId(type = IdType.INPUT)
    private String id;

    private String title;
    private String difficulty;   // easy / medium / hard
    private String status;       // todo / solved

    private String approach;
    private String note;
    private String source;
    private String url;

    private LocalDateTime solvedAt;
    private LocalDateTime lastSolvedAt;
    private Integer solveCount;
    private Integer reviewStage;
    private LocalDateTime nextReviewAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
