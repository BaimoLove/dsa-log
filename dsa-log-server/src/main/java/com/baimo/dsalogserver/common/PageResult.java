package com.baimo.dsalogserver.common;

import java.util.List;

/**
 * 分页结果。集合永不为 null，total 是筛选后的真实总数。
 */
public record PageResult<T>(List<T> items, long total, int page, int size, int totalPages) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        int pages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResult<>(items == null ? List.of() : items, total, page, size, pages);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0, page, size, 0);
    }
}
