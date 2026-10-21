package com.baimo.dsalogserver.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新设置：null = 不改。
 * 数值范围在 SettingService 里校验（1..200 / 1..100），这里不标注解，
 * 因为注解分不清「没传」和「传了 0」。
 */
@Data
public class UpdateSettingsRequest {

    private Integer dailyReviewLimit;
    private Integer pageSize;
    private List<Integer> reviewIntervals;
    private Boolean overdueFromNow;
}
