package com.baimo.dsalogserver.service.impl;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.dto.UpdateSettingsRequest;
import com.baimo.dsalogserver.entity.AppSetting;
import com.baimo.dsalogserver.mapper.AppSettingMapper;
import com.baimo.dsalogserver.service.SettingService;
import com.baimo.dsalogserver.vo.SettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设置读写实现。
 *
 * <p>表里全是字符串，类型与默认值只在这一层出现：
 * 读的时候缺键回默认、脏值也回默认（不抛错，配置坏了不该让整个站起不来）；
 * 写的时候越界直接 400（用户手输的值必须立刻纠正，静默钳制会让人以为保存成功）。
 */
@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    public static final String KEY_DAILY_LIMIT = "review.dailyLimit";
    public static final String KEY_INTERVALS = "review.intervals";
    public static final String KEY_PAGE_SIZE = "ui.pageSize";
    public static final String KEY_OVERDUE_FROM_NOW = "review.overdueFromNow";

    static final int DEFAULT_DAILY_LIMIT = 20;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final List<Integer> DEFAULT_INTERVALS = List.of(1, 3, 7, 15);
    static final boolean DEFAULT_OVERDUE_FROM_NOW = true;

    private static final int MAX_DAILY_LIMIT = 200;
    private static final int MAX_PAGE_SIZE = 100;

    private final AppSettingMapper settingMapper;

    @Override
    public SettingsVO get() {
        Map<String, String> raw = settingMapper.selectList(null).stream()
                .collect(Collectors.toMap(AppSetting::getSettingKey, a -> a.getSettingValue() == null ? "" : a.getSettingValue(),
                        (a, b) -> a));

        SettingsVO vo = new SettingsVO();
        vo.setDailyReviewLimit(parseInt(raw.get(KEY_DAILY_LIMIT), DEFAULT_DAILY_LIMIT, 1, MAX_DAILY_LIMIT));
        vo.setPageSize(parseInt(raw.get(KEY_PAGE_SIZE), DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE));
        vo.setReviewIntervals(parseIntervals(raw.get(KEY_INTERVALS)));
        vo.setOverdueFromNow(parseBool(raw.get(KEY_OVERDUE_FROM_NOW), DEFAULT_OVERDUE_FROM_NOW));
        return vo;
    }

    @Override
    @Transactional
    public SettingsVO update(UpdateSettingsRequest req) {
        List<AppSetting> items = new ArrayList<>();
        LocalDateTime now = TimeUtil.now();

        if (req.getDailyReviewLimit() != null) {
            int v = req.getDailyReviewLimit();
            requireBetween(v, 1, MAX_DAILY_LIMIT, "每次复习条数必须在 1.." + MAX_DAILY_LIMIT);
            items.add(item(KEY_DAILY_LIMIT, String.valueOf(v), now));
        }
        if (req.getPageSize() != null) {
            int v = req.getPageSize();
            requireBetween(v, 1, MAX_PAGE_SIZE, "每页条数必须在 1.." + MAX_PAGE_SIZE);
            items.add(item(KEY_PAGE_SIZE, String.valueOf(v), now));
        }
        if (req.getReviewIntervals() != null) {
            items.add(item(KEY_INTERVALS, formatIntervals(req.getReviewIntervals()), now));
        }
        if (req.getOverdueFromNow() != null) {
            items.add(item(KEY_OVERDUE_FROM_NOW, String.valueOf(req.getOverdueFromNow()), now));
        }

        if (!items.isEmpty()) {
            settingMapper.upsertBatch(items);
        }
        return get();
    }

    @Override
    @Transactional
    public void replaceAll(SettingsVO settings) {
        if (settings == null) {
            return;
        }
        LocalDateTime now = TimeUtil.now();
        List<AppSetting> items = new ArrayList<>();
        items.add(item(KEY_DAILY_LIMIT,
                String.valueOf(requireInt(settings.getDailyReviewLimit(), 1, MAX_DAILY_LIMIT, "每次复习条数")), now));
        items.add(item(KEY_PAGE_SIZE,
                String.valueOf(requireInt(settings.getPageSize(), 1, MAX_PAGE_SIZE, "每页条数")), now));
        items.add(item(KEY_INTERVALS, formatIntervals(settings.getReviewIntervals()), now));
        items.add(item(KEY_OVERDUE_FROM_NOW, String.valueOf(settings.isOverdueFromNow()), now));
        settingMapper.upsertBatch(items);
    }

    // ---------------- 解析 / 校验 ----------------

    private static AppSetting item(String key, String value, LocalDateTime now) {
        AppSetting a = new AppSetting();
        a.setSettingKey(key);
        a.setSettingValue(value);
        a.setUpdatedAt(now);
        return a;
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            // 脏值（比如手工改库写了个 9999）回默认，不做钳制：钳制会掩盖数据问题
            return v < min || v > max ? fallback : v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "false".equalsIgnoreCase(raw.trim())
                ? Boolean.parseBoolean(raw.trim()) : fallback;
    }

    /** 存的是 "[1,3,7,15]" 这种形状：比纯逗号串多一层自解释，又不必引 JSON 库 */
    private static List<Integer> parseIntervals(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>(DEFAULT_INTERVALS);
        }
        String body = raw.trim().replace("[", "").replace("]", "");
        List<Integer> list = new ArrayList<>();
        for (String part : body.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                int v = Integer.parseInt(s);
                if (v > 0) {
                    list.add(v);
                }
            } catch (NumberFormatException ignore) {
                // 单个坏值跳过；全坏则回默认
            }
        }
        return list.isEmpty() ? new ArrayList<>(DEFAULT_INTERVALS) : list;
    }

    private static String formatIntervals(List<Integer> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            throw BizException.invalid("复习间隔不能为空");
        }
        if (intervals.size() > 10) {
            throw BizException.invalid("复习间隔最多 10 档");
        }
        List<Integer> clean = new ArrayList<>();
        for (Integer v : intervals) {
            if (v == null || v < 1 || v > 365) {
                throw BizException.invalid("复习间隔必须在 1..365 天");
            }
            clean.add(v);
        }
        return clean.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private static int requireInt(int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw BizException.invalid(what + "必须在 " + min + ".." + max);
        }
        return value;
    }

    private static void requireBetween(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw BizException.invalid(message);
        }
    }
}
