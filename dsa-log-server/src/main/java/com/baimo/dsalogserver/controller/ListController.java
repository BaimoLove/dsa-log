package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.PageResult;
import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.dto.BulkIdsRequest;
import com.baimo.dsalogserver.dto.CreateListRequest;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.dto.UpdateListRequest;
import com.baimo.dsalogserver.service.ListService;
import com.baimo.dsalogserver.vo.BatchVO;
import com.baimo.dsalogserver.vo.ListVO;
import com.baimo.dsalogserver.vo.ProblemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题单接口。单独一个控制器：题单与题目是两个聚合，题单删除只清关联、不动题目。
 *
 * <p>两个路径细节：
 * <ul>
 *   <li>{@code GET /{id}/problems} 走 {@link ProblemQuery}，与题库列表共用同一套筛选/排序/分页，
 *       只把 listId 固定在路径上——两处各写一份查询条件必然分叉</li>
 *   <li>{@code DELETE /{id}/items/{problemId}} 在关联不存在时返 404 而不是幂等 200，
 *       与前端 Dexie 实现保持同一行为，M8 双跑对拍才不用为它开特例</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/lists")
@RequiredArgsConstructor
public class ListController {

    private final ListService listService;

    @GetMapping
    public Result<List<ListVO>> list() {
        return Result.success(listService.list());
    }

    @PostMapping
    public Result<ListVO> create(@Valid @RequestBody CreateListRequest request) {
        return Result.success(listService.create(request));
    }

    @GetMapping("/{id}")
    public Result<ListVO> get(@PathVariable String id) {
        return Result.success(listService.get(id));
    }

    @PatchMapping("/{id}")
    public Result<ListVO> update(@PathVariable String id, @Valid @RequestBody UpdateListRequest request) {
        return Result.success(listService.update(id, request));
    }

    /** 只删题单与其关联，题目留在题库里 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable String id) {
        listService.remove(id);
        return Result.success();
    }

    @GetMapping("/{id}/problems")
    public Result<PageResult<ProblemVO>> problems(@PathVariable String id, @ModelAttribute ProblemQuery query) {
        return Result.success(listService.problems(id, query));
    }

    /** 加入题目，幂等：已在题单里的题不重复插行，affected 只算真正新增的 */
    @PostMapping("/{id}/items")
    public Result<BatchVO> addItems(@PathVariable String id, @Valid @RequestBody BulkIdsRequest request) {
        return Result.success(listService.addProblems(id, request.getIds()));
    }

    /**
     * 成员 id 列表。单独一个端点而不是复用 {@code /{id}/problems}：
     * 后者是分页的（size 上限 100），取「全集 id」要么翻页要么把上限调大，
     * 两条路都不如一条只查关联表的 SQL 干净。
     */
    @GetMapping("/{id}/problem-ids")
    public Result<List<String>> problemIds(@PathVariable String id) {
        return Result.success(listService.problemIds(id));
    }

    @DeleteMapping("/{id}/items/{problemId}")
    public Result<Void> removeItem(@PathVariable String id, @PathVariable String problemId) {
        listService.removeProblem(id, problemId);
        return Result.success();
    }
}
