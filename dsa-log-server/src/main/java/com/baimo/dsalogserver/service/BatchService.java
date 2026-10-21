package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.vo.BatchVO;

import java.util.List;

/**
 * 批量操作，实现见 {@link impl.BatchServiceImpl}。
 *
 * <p>五条能力的共同不变式：
 * <ul>
 *   <li>返回的 affected 是「真正发生改变的条数」，不是入参长度——不存在的 id、
 *       已符合目标状态的题都不计入；</li>
 *   <li>入参 ids 先去空、去重、trim，再按上限校验（超出抛 413 而非 400）；</li>
 *   <li>标记已刷与取消已刷互为逆操作，且都写 {@code review_record} 流水，
 *       单条与批量的行为必须一致。</li>
 * </ul>
 *
 * <p>刻意不提供撤销：想把批量已刷退回去，再发一次 {@link #markUnsolved} 就行。
 * 撤销快照要抄旧值、发令牌、处理 TTL 与重复调用的竞态，成本远高于收益。
 */
public interface BatchService {

    /** 批量标记已刷。首次（不在复习池）进池并排到明天，重复刷只累加次数、保留原排期 */
    BatchVO markSolved(List<String> ids);

    /** 批量取消已刷。只处理当前为 solved 的行，solvedAt / solveCount 作为历史保留 */
    BatchVO markUnsolved(List<String> ids);

    /** 批量追加标签，幂等；affected 按「至少新增一个标签的题数」计 */
    BatchVO addTags(List<String> ids, List<String> tagIds);

    /** 批量加入题单，幂等；题单不存在抛 notFound */
    BatchVO addToList(List<String> ids, String listId);

    /** 批量逻辑删 + 清标签/题单关联。没有相反操作，属于不可恢复操作 */
    BatchVO remove(List<String> ids);
}
