package com.baimo.dsalogserver.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 待复习队列。
 *
 * <p>{@code total} 不受 limit 影响：前端要显示「今天还有 37 道」，
 * 而不是「最多 20 道」；{@code limited} 是本次真正返回的条数，
 * 两者不等时前端才知道后面还有。
 */
@Data
public class DueReviewVO {

    private long total;
    private int limited;
    private List<DueItemVO> items = new ArrayList<>();
}
