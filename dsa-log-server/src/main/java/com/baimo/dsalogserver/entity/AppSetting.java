package com.baimo.dsalogserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用配置，key-value 两列。值统一存字符串（数字、布尔、JSON 数组），
 * 解析与校验放在 SettingService，表里不出现类型。
 */
@Data
@TableName("app_setting")
public class AppSetting {

    @TableId(type = IdType.INPUT)
    private String settingKey;

    private String settingValue;

    private LocalDateTime updatedAt;
}
