package com.baimo.dsalogserver.common;

import java.util.List;
import java.util.Objects;

/**
 * 批量 id 入参归一化。批量改题与「题单加题」共用同一套口径，
 * 避免两处各写一份 trim/去重后行为分叉：
 *
 * <ul>
 *   <li>空集合 / 全是空白 → 400，是参数错误</li>
 *   <li>去重后超上限 → 413，是数据量超限</li>
 * </ul>
 *
 * 两种语义不能混成一个码：前端对 400 的处理是「提示改输入」，对 413 是「提示分批」。
 */
public final class BatchIds {

    /** 单次批量上限。与前端「本页全选」的量级匹配，超出说明调用方分页逻辑有问题 */
    public static final int MAX = 500;

    private BatchIds() {
    }

    public static List<String> normalize(List<String> ids) {
        return normalize(ids, MAX, "ids");
    }

    public static List<String> normalize(List<String> ids, int max, String label) {
        if (ids == null) {
            throw BizException.invalid(label + " 不能为空");
        }
        List<String> cleaned = ids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            throw BizException.invalid(label + " 不能为空");
        }
        if (cleaned.size() > max) {
            throw BizException.payloadTooLarge("单次批量最多 " + max + " 条");
        }
        return cleaned;
    }
}
