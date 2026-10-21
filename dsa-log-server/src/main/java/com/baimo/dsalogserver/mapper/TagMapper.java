package com.baimo.dsalogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baimo.dsalogserver.entity.Tag;
import com.baimo.dsalogserver.vo.TagUsageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 标签表访问。
 *
 * <p>标签是物理删除（与 problem / problem_list 的逻辑删不同），
 * 所以这里的 SQL 不带 deleted 条件；删除时由 service 先清 problem_tag。
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 标签 + 未删题目的引用数，一次查完（不做 N+1）。
     * 计数用 COUNT(p.id) 而不是 COUNT(*)：「题目已逻辑删但关联还在」的占位行
     * 会因为 LEFT JOIN 产出一行 p.id 为 NULL 的记录。
     */
    List<TagUsageRow> selectWithUsage(@Param("keyword") String keyword);

    /** 单个标签 + 引用数：新建/改名后要把 usageCount 一起返回，前端才知道该插在哪 */
    TagUsageRow selectUsageById(@Param("id") String id);

    /** 重名校验；excludeId 用于改名时排除自己 */
    int countByName(@Param("name") String name, @Param("excludeId") String excludeId);
}
