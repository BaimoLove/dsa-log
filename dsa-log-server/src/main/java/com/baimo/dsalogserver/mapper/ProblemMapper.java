package com.baimo.dsalogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 动态条件分页查询。count 语句与 LIMIT 由 PaginationInnerInterceptor 生成，
     * XML 里只写业务条件，不写 offset/size。
     */
    IPage<Problem> selectPageList(IPage<Problem> page, @Param("q") ProblemQuery q);

    /** 标记已刷/取消已刷时加行锁，避免并发下重复写 solved_at */
    Problem selectForUpdate(@Param("id") String id);

    /** 随机一题；status 为空表示全库，excludeIds 为空表示不排除 */
    Problem selectRandomOne(@Param("status") String status,
                            @Param("excludeIds") List<String> excludeIds);

    /** 批量标记已刷：一次 UPDATE 完成，返回影响行数 */
    int markSolvedBatch(@Param("ids") List<String> ids);
}
