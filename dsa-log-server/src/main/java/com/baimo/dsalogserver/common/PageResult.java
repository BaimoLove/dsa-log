package com.baimo.dsalogserver.common;

import java.util.List;

/**
 * 分页结果。集合永不为 null，total 是筛选后的真实总数。
 *
 * <p>totalPages 至少为 1：前端 Dexie 实现一直按 {@code Math.max(1, ceil(total/size))} 算，
 * 视图也按「至少一页」渲染页码。空结果给 0 会出现「第 1 页 / 共 0 页」，
 * M8 双跑对拍时这就是一处必须消掉的差异。
 */
public record PageResult<T>(List<T> items, long total, int page, int size, int totalPages) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        int pages = size <= 0 ? 1 : (int) Math.max(1, (total + size - 1) / size);
        return new PageResult<>(items == null ? List.of() : items, total, page, size, pages);
    }
}
