package com.baimo.dsalogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baimo.dsalogserver.entity.ProblemList;
import com.baimo.dsalogserver.vo.ListRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题单表访问。
 *
 * <p>继承 BaseMapper 拿 MP 生成的单行 CRUD（{@code @TableLogic} 会自动带 deleted=0）；
 * 进度这类多表聚合自己写 XML：手写 SQL 不受 {@code @TableLogic} 保护，
 * 每条都要显式写 {@code deleted = 0}。
 */
@Mapper
public interface ProblemListMapper extends BaseMapper<ProblemList> {

    /** 未删的题单数：>0 表示可用。不做 selectOne，避免调用方误用已删行 */
    int countAlive(@Param("id") String id);

    /** 看板：全部题单 + 现算进度，按 sort_order / created_at 排 */
    List<ListRow> selectAllWithProgress();

    /** 单条题单 + 进度；不存在或已删返回 null */
    ListRow selectOneWithProgress(@Param("id") String id);

    /** 新题单的排序号：取未删行的 max+1，空表返回 0 */
    int nextSortOrder();
}
