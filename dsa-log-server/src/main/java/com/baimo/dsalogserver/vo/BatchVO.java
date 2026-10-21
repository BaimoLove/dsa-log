package com.baimo.dsalogserver.vo;

/**
 * 批量操作的统一返回。
 *
 * <p>只有 affected 一个字段，值恒为「真正发生改变的条数」，不是入参条数：
 * 已符合目标状态的题、不存在的 id 都不计入。前端据此决定提示文案
 * （0 条时提示「没有实际变更」），所以口径必须与 Dexie 阶段一致。
 */
public record BatchVO(int affected) {
}
