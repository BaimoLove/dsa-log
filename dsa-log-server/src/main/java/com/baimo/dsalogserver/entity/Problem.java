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
    /** 最近一次复习评分时间；与 MARK_SOLVED 的 lastSolvedAt 是两个不同事件 */
    private LocalDateTime lastReviewedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 0 正常 / 1 已归档。归档与逻辑删除是两件事：
     * 归档的题还在库里、还能在「已归档」筛选项里看到，只是不参与随机、统计与待复习。
     * 前端从 M2 起就有这个字段，后端 M8 才补齐。
     */
    private Integer archived;

    @TableLogic
    private Integer deleted;
}
