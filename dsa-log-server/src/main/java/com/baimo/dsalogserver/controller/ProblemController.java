package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.PageResult;
import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.dto.CreateProblemRequest;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.dto.UpdateProblemRequest;
import com.baimo.dsalogserver.service.ProblemService;
import com.baimo.dsalogserver.vo.MarkSolvedVO;
import com.baimo.dsalogserver.vo.ProblemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public Result<PageResult<ProblemVO>> list(@ModelAttribute ProblemQuery query) {
        return Result.success(problemService.list(query));
    }

    /**
     * 题库为空时返 200 + data:null，不用 204：成功一律走 Result 包装，
     * 前端只判 data 是否存在，不必再对 HTTP 状态码开一条分支。
     * 路径写在 /{id} 之前只是阅读顺序，Spring 本来就优先匹配字面量。
     */
    @GetMapping("/random")
    public Result<ProblemVO> random(@RequestParam(required = false) String pool,
                                    @RequestParam(required = false) List<String> exclude) {
        return Result.success(problemService.random(pool, exclude));
    }

    @GetMapping("/{id}")
    public Result<ProblemVO> get(@PathVariable String id) {
        return Result.success(problemService.get(id));
    }

    @PostMapping
    public Result<ProblemVO> create(@Valid @RequestBody CreateProblemRequest request) {
        return Result.success(problemService.create(request));
    }

    /** 部分更新：字段为 null 不改，清空文本传 ""，tagIds 传 [] 清空标签 */
    @PatchMapping("/{id}")
    public Result<ProblemVO> update(@PathVariable String id, @Valid @RequestBody UpdateProblemRequest request) {
        return Result.success(problemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable String id) {
        problemService.remove(id);
        return Result.success();
    }

    @PostMapping("/{id}/solved")
    public Result<MarkSolvedVO> markSolved(@PathVariable String id) {
        return Result.success(problemService.markSolved(id));
    }

    @PostMapping("/{id}/unsolved")
    public Result<ProblemVO> markUnsolved(@PathVariable String id) {
        return Result.success(problemService.markUnsolved(id));
    }
}
