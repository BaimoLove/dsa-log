package com.baimo.dsalogserver.mapper;

import com.baimo.dsalogserver.dto.ProblemTagPair;
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

    /** 批量幂等挂标签：题 × 标签的笛卡尔积，已存在的关联由 IGNORE 跳过 */
    int insertIgnoreBatch(@Param("problemIds") List<String> problemIds,
                          @Param("tagIds") List<String> tagIds);

    int deleteByProblemId(@Param("problemId") String problemId);

    /** 批量删关联，批量删除时一次清掉，不逐题往返 */
    int deleteByProblemIds(@Param("problemIds") List<String> problemIds);

    int deleteByTagId(@Param("tagId") String tagId);

    List<String> selectTagIdsByProblemId(@Param("problemId") String problemId);

    /** 导入用：每道题的标签不同，只能逐对插入，不能做笛卡尔积 */
    int insertIgnorePairs(@Param("pairs") List<ProblemTagPair> pairs);

    /** 导入 replace 模式用：物理清空 */
    int deleteAll();
}
