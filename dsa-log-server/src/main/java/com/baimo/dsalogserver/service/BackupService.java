package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.vo.BackupVO;
import com.baimo.dsalogserver.vo.ImportResultVO;

/**
 * 备份业务。
 *
 * <p>导出与导入用同一个 {@link BackupVO}：导出的形状就是导入要的形状，
 * 这样「导出 → 导入」不需要中间转换，也就不会出现两边字段对不上的问题。
 */
public interface BackupService {

    /** 全量导出：题目（含标签关联）、标签、题单、题单关联、复习流水、设置 */
    BackupVO exportAll();

    /** CSV 只导题目主体字段，给人用 Excel 看 */
    String exportCsv();

    /**
     * 全量导入。
     *
     * @param mode    merge（默认）或 replace
     * @param confirm replace 时必须等于 REPLACE
     */
    ImportResultVO importBackup(BackupVO payload, String mode, String confirm);
}
