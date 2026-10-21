package com.baimo.dsalogserver.mapper;

import com.baimo.dsalogserver.vo.TagRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemTagMapper {

    /** 一次查出多道题的标签，避免 N+1 */
    List<TagRow> selectByProblemIds(@Param("ids") List<String> ids);

    /** 幂等挂标签 */
    int insertIgnore(@Param("problemId") String problemId, @Param("tagIds") List<String> tagIds);

    int deleteByProblemId(@Param("problemId") String problemId);

    int deleteByTagId(@Param("tagId") String tagId);

    List<String> selectTagIdsByProblemId(@Param("problemId") String problemId);
}
