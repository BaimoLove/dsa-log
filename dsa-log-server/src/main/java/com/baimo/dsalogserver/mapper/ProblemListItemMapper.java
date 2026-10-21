package com.baimo.dsalogserver.mapper;

import com.baimo.dsalogserver.vo.ListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemListItemMapper {

    /** 删除题目时级联清理题单关联 */
    int deleteByProblemId(@Param("problemId") String problemId);

    /** 批量删除题目时一次清掉关联 */
    int deleteByProblemIds(@Param("problemIds") List<String> problemIds);

    /** 删除题单时清空它的全部关联（关联表物理删，题单本身是逻辑删） */
    int deleteByListId(@Param("listId") String listId);

    /** 从题单移出一道题；返回 0 表示本来就不在里面，调用方据此抛 404 */
    int deleteOne(@Param("listId") String listId, @Param("problemId") String problemId);

    /** 题单内下一个可用排序号，空题单返回 0 */
    int nextSortOrder(@Param("listId") String listId);

    /** 未在该题单里的题目 id：批量加入只对这部分插行，affected 才等于真实新增数 */
    List<String> selectMissingIds(@Param("listId") String listId, @Param("ids") List<String> ids);

    /**
     * 题单里的题目 id（按加入顺序）。这是前端仓储契约里 {@code problemIdsOf} 的数据来源，
     * 不校验题单是否存在（题单不存在就是空集合，与 Dexie 侧行为一致），
     * 也不过滤归档——它问的是「关联表里有什么」，筛选题是调用方的事。
     */
    List<String> selectProblemIds(@Param("listId") String listId);

    int insertIgnoreBatch(@Param("listId") String listId,
                          @Param("problemIds") List<String> problemIds,
                          @Param("startOrder") int startOrder);

    /** 导出用：全部关联，时间在 SQL 里转成 epoch 毫秒（VO 不出现 LocalDateTime） */
    List<ListItemVO> selectAll();

    /** 导入用：保留原始的 sort_order 与加入时间，与「加入题单」时的自增分配不同 */
    int insertIgnoreFullBatch(@Param("items") List<ListItemVO> items);

    /** 导入 replace 模式用：物理清空 */
    int deleteAll();
}
