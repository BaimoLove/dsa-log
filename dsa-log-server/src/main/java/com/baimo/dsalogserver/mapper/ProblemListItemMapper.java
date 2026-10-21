package com.baimo.dsalogserver.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProblemListItemMapper {

    /** 删除题目时级联清理题单关联 */
    @Delete("DELETE FROM problem_list_item WHERE problem_id = #{problemId}")
    int deleteByProblemId(@Param("problemId") String problemId);
}
