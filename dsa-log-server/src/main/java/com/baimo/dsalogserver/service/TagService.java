package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.dto.CreateTagRequest;
import com.baimo.dsalogserver.dto.UpdateTagRequest;
import com.baimo.dsalogserver.vo.TagUsageVO;

import java.util.List;

/**
 * 标签业务。标签是物理删除：删掉即从全站消失，题目上的关联由这里一并清掉。
 */
public interface TagService {

    /** 全部标签 + 使用数，按使用数降序、名称升序 */
    List<TagUsageVO> list(String keyword);

    TagUsageVO create(CreateTagRequest req);

    /** 部分更新：字段为 null 不改；改名受唯一约束 */
    TagUsageVO update(String id, UpdateTagRequest req);

    /** 物理删除 + 清 problem_tag 关联；题目本身不动 */
    void remove(String id);
}
