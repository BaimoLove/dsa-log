package com.baimo.dsalogserver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemListItemMapper {

    /** 删除题目时级联清理题单关联 */
    int deleteByProblemId(@Param("problemId") String problemId);

    /** 批量删除题目时一次清掉关联 */
    int deleteByProblemIds(@Param("problemIds") List<String> problemIds);

    /** 题单内下一个可用排序号，空题单返回 0 */
    int nextSortOrder(@Param("listId") String listId);

    /** 未在该题单里的题目 id：批量加入只对这部分插行，affected 才等于真实新增数 */
    List<String> selectMissingIds(@Param("listId") String listId, @Param("ids") List<String> ids);

    int insertIgnoreBatch(@Param("listId") String listId,
                          @Param("problemIds") List<String> problemIds,
                          @Param("startOrder") int startOrder);
}
