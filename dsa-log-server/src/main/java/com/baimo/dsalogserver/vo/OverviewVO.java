package com.baimo.dsalogserver.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 概览指标。与前端 OverviewStats 一一对应，是 M8 对拍的主要比对对象。
 *
 * <p>三个口径容易各自漂，这里写死：
 * <ul>
 *   <li>dueCount：status='solved' 且 next_review_at &lt;= now（不是「今天到期」）</li>
 *   <li>solvedToday：今日 MARK_SOLVED 流水中**不同题目**数（同一题刷两次算一道）</li>
 *   <li>reviewedToday：今日 REVIEW 流水**条数**（同一题复习两次算两次，衡量工作量）</li>
 * </ul>
 */
@Data
public class OverviewVO {

    private long dueCount;
    private long solvedToday;
    private long reviewedToday;
    private long totalProblems;
    private long solvedProblems;
    private List<ListVO> listProgress = new ArrayList<>();
}
