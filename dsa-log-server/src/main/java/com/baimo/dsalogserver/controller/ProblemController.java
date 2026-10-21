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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public Result<PageResult<ProblemVO>> list(@ModelAttribute ProblemQuery query) {
        return Result.ok(problemService.list(query));
    }

    /** 必须写在 /{id} 之前语义上更清晰；Spring 会优先匹配字面量路径 */
    @GetMapping("/random")
    public ResponseEntity<Result<ProblemVO>> random(@RequestParam(required = false) String pool,
                                                    @RequestParam(required = false) List<String> exclude) {
        ProblemVO vo = problemService.random(pool, exclude);
        if (vo == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(Result.ok(vo));
    }

    @GetMapping("/{id}")
    public Result<ProblemVO> get(@PathVariable String id) {
        return Result.ok(problemService.get(id));
    }

    @PostMapping
    public Result<ProblemVO> create(@Valid @RequestBody CreateProblemRequest request) {
        return Result.ok(problemService.create(request));
    }

    /** 部分更新：字段为 null 不改，清空文本传 ""，tagIds 传 [] 清空标签 */
    @PatchMapping("/{id}")
    public Result<ProblemVO> update(@PathVariable String id, @Valid @RequestBody UpdateProblemRequest request) {
        return Result.ok(problemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable String id) {
        problemService.remove(id);
        return Result.ok();
    }

    @PostMapping("/{id}/solved")
    public Result<MarkSolvedVO> markSolved(@PathVariable String id) {
        return Result.ok(problemService.markSolved(id));
    }

    @PostMapping("/{id}/unsolved")
    public Result<ProblemVO> markUnsolved(@PathVariable String id) {
        return Result.ok(problemService.markUnsolved(id));
    }
}
