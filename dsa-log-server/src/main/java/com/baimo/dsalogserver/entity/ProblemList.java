package com.baimo.dsalogserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题单。进度 {@code total/solved} 不是表的列，由关联表现算，见 ListMapper 的进度 SQL。
 */
@Data
@TableName("problem_list")
public class ProblemList {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;
    private String description;

    /** 卡片主题色，前端色板取值，缺省 #6366f1 */
    private String color;

    /** 看板顺序，越小越靠前；新建时取 max+1 */
    private Integer sortOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
