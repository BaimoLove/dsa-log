package com.baimo.dsalogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /** 动态条件分页查询 */
    List<Problem> selectPageList(@Param("q") ProblemQuery q,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    /** 与上面同条件的总数，用于 total */
    long countByQuery(@Param("q") ProblemQuery q);

    /** 标记已刷/取消已刷时加行锁，避免并发下重复写 solved_at */
    Problem selectForUpdate(@Param("id") String id);

    /** 随机一题；status 为空表示全库，excludeIds 为空表示不排除 */
    Problem selectRandomOne(@Param("status") String status,
                            @Param("excludeIds") List<String> excludeIds);

    /** 批量标记已刷：一次 UPDATE 完成，返回影响行数 */
    int markSolvedBatch(@Param("ids") List<String> ids);
}
