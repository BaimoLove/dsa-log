package com.baimo.dsalogserver.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 题目列表查询条件。由 query string 绑定，page 从 1 起。
 */
@Data
public class ProblemQuery {

    /** 关键词，匹配 title / approach / note / source（小写包含） */
    private String keyword;

    /** easy/medium/hard，多选为 IN */
    private List<String> difficulties = new ArrayList<>();

    /** todo/solved，多选为 IN */
    private List<String> statuses = new ArrayList<>();

    /** 标签多选为 AND（全命中） */
    private List<String> tagIds = new ArrayList<>();

    /** 只查某个题单内的题 */
    private String listId;

    /**
     * 0 只看未归档（默认）/ 1 只看已归档。是**精确匹配**而不是「0 表示不过滤」：
     * 前端 Dexie 实现在这一问题上是同一个语义，两边必须一致。
     */
    private Integer archived = 0;

    private int page = 1;
    private int size = 20;

    /** 排序白名单，见 ProblemServiceImpl.SORTS */
    private String sort = "updatedAt_desc";

    public int safePage() {
        return Math.max(page, 1);
    }

    /** archived 只认 0/1，其它值一律当 0（不让脏参数把归档题混进正常列表） */
    public int safeArchived() {
        return archived != null && archived == 1 ? 1 : 0;
    }

    /** size 夹在 1..100 */
    public int safeSize() {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    /** 多标签 AND 的 HAVING 期望值，SQL 里用 #{q.tagCount} 取 */
    public int getTagCount() {
        return tagIds == null ? 0 : tagIds.size();
    }
}
