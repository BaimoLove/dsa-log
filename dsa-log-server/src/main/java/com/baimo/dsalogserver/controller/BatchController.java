package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.dto.BulkIdsRequest;
import com.baimo.dsalogserver.dto.BulkListRequest;
import com.baimo.dsalogserver.dto.BulkTagsRequest;
import com.baimo.dsalogserver.service.BatchService;
import com.baimo.dsalogserver.vo.BatchVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批量操作。单独一个控制器而不是塞进 ProblemController：
 * 它依赖的 service、错误码语义（413 超限）与单条 CRUD 都不同，混在一起会让
 * 那个类变成「什么都在里面」的入口。
 *
 * <p>五个端点统一返回 {@code {affected}}，成功恒为 200，不做部分成功。
 */
@RestController
@RequestMapping("/api/problems/bulk")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @PostMapping("/solved")
    public Result<BatchVO> solved(@Valid @RequestBody BulkIdsRequest request) {
        return Result.success(batchService.markSolved(request.getIds()));
    }

    @PostMapping("/unsolved")
    public Result<BatchVO> unsolved(@Valid @RequestBody BulkIdsRequest request) {
        return Result.success(batchService.markUnsolved(request.getIds()));
    }

    @PostMapping("/tags")
    public Result<BatchVO> tags(@Valid @RequestBody BulkTagsRequest request) {
        return Result.success(batchService.addTags(request.getIds(), request.getTagIds()));
    }

    @PostMapping("/lists")
    public Result<BatchVO> lists(@Valid @RequestBody BulkListRequest request) {
        return Result.success(batchService.addToList(request.getIds(), request.getListId()));
    }

    /** 不可恢复：没有相反操作，前端文案必须明确提示 */
    @PostMapping("/delete")
    public Result<BatchVO> delete(@Valid @RequestBody BulkIdsRequest request) {
        return Result.success(batchService.remove(request.getIds()));
    }
}
