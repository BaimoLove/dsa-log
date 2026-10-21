package com.baimo.dsalogserver.vo;

import lombok.Data;

/**
 * 题单内的一条关联。导出必须带上它，否则还原后题单是空的；
 * sortOrder 决定题单内的展示顺序，不能丢。
 */
@Data
public class ListItemVO {

    private String listId;
    private String problemId;
    private Integer sortOrder;
    private Long addedAt;
}
