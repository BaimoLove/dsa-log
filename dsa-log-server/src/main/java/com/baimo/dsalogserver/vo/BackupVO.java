package com.baimo.dsalogserver.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 全量备份的顶层形状，导出与导入共用同一个类。
 *
 * <p>导出时 problems 用 ProblemVO（含 tagIds / tags 快照），导入时只取
 * tagIds —— tags 是渲染快照，落库的关联只有 problem_tag 一份。
 *
 * <p>lists 里带 total/solved（ListVO 现算的进度），导入时忽略：
 * 进度是查询结果，不是数据。
 */
@Data
public class BackupVO {

    private String app = "dsa-log";
    private Integer schemaVersion;
    private Long exportedAt;

    private List<ProblemVO> problems = new ArrayList<>();
    private List<TagUsageVO> tags = new ArrayList<>();
    private List<ListVO> lists = new ArrayList<>();
    private List<ListItemVO> listItems = new ArrayList<>();
    private List<ReviewRecordVO> reviews = new ArrayList<>();
    private SettingsVO settings;
}
