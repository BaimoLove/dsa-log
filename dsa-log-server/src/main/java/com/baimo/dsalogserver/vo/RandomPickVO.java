package com.baimo.dsalogserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 随机一题的结果。
 *
 * <p>M8 之前这里只返回 {@link ProblemVO}，前端 http 仓储必须自己推演
 * 「这次用的是哪个池子」「本轮是否已抽完重置」——那需要在浏览器里再取一次全量列表，
 * 且两套口径必然分叉。池子归属只有后端知道（它就是四级级联的执行者），
 * 所以把三个元信息一起返回。
 *
 * <ul>
 *   <li>{@code pool}：unsolved（未刷池）或 all（全库）</li>
 *   <li>{@code reset}：true 表示该池本轮已抽完、刚被重置，前端据此清空 drawnIds</li>
 *   <li>{@code candidateCount}：命中那一级的候选数，用于「从 N 道未刷题中抽取」</li>
 * </ul>
 *
 * <p>{@code candidateCount} 用 {@code Long} 而不是 {@code long}：题库为空时整体返回
 * data:null，此时没有候选数可言，用 null 比用 0 更准确。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RandomPickVO {

    private ProblemVO problem;

    /** unsolved | all */
    private String pool;

    private Boolean reset;

    private Long candidateCount;
}
