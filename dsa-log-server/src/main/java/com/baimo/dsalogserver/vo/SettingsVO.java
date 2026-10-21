package com.baimo.dsalogserver.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置出参，与前端 AppSettings 一一对应。
 * 存进 app_setting 时是字符串，这里已经解析成正确类型。
 */
@Data
public class SettingsVO {

    private int dailyReviewLimit;
    private int pageSize;
    private List<Integer> reviewIntervals = new ArrayList<>();
    private boolean overdueFromNow;
}
