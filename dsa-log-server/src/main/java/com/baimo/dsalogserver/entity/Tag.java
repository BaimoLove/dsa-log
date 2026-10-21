package com.baimo.dsalogserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tag")
public class Tag {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;
    private String color;
    private LocalDateTime createdAt;
}
