package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.PageResult;
import com.baimo.dsalogserver.dto.CreateListRequest;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.dto.UpdateListRequest;
import com.baimo.dsalogserver.vo.BatchVO;
import com.baimo.dsalogserver.vo.ListVO;
import com.baimo.dsalogserver.vo.ProblemVO;

import java.util.List;

/**
 * 题单业务接口，实现见 {@link impl.ListServiceImpl}。
 *
 * <p>不变式：{@code total/solved} 永远现算（关联表 × 存活题目），任何返回 ListVO 的方法
 * 都必须给出与题单内查询一致的数字。事务注解只写在实现上。
 */
public interface ListService {

    /** 看板：全部题单 + 进度，按 sortOrder / createdAt 升序 */
    List<ListVO> list();

    /** 单条题单 + 进度，不存在抛 notFound */
    ListVO get(String id);

    /** 新建题单，sortOrder 取当前最大值 +1；进度恒为 0/0 */
    ListVO create(CreateListRequest request);

    /** 部分更新：字段为 null 不改，空串表示清空描述 */
    ListVO update(String id, UpdateListRequest request);

    /** 逻辑删题单 + 物理清关联，题目本身不受影响 */
    void remove(String id);

    /** 题单内分页。路径上的 listId 优先，query 里带的 listId 会被覆盖 */
    PageResult<ProblemVO> problems(String listId, ProblemQuery query);

    /** 加入题目（幂等）：返回真正新增的题数，已在题单里的不计入 */
    BatchVO addProblems(String listId, List<String> problemIds);

    /** 移出一道题；不在题单里抛 notFound */
    void removeProblem(String listId, String problemId);

    /**
     * 题单成员 id，按加入顺序。题单不存在返回空列表（不抛 404）——
     * 前端仓储契约里它是「查询关联」而不是「查询题单」。
     */
    List<String> problemIds(String listId);
}
