package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.dto.RateReviewRequest;
import com.baimo.dsalogserver.service.ReviewService;
import com.baimo.dsalogserver.vo.DueReviewVO;
import com.baimo.dsalogserver.vo.RateVO;
import com.baimo.dsalogserver.vo.ReviewRecordVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** limit 只截断 items；total 是「真正待复习多少道」，前端据此显示还剩多少 */
    @GetMapping("/due")
    public Result<DueReviewVO> due(@RequestParam(required = false) Integer limit) {
        return Result.success(reviewService.due(limit));
    }

    @PostMapping("/{problemId}/rate")
    public Result<RateVO> rate(@PathVariable String problemId,
                               @Valid @RequestBody RateReviewRequest request) {
        return Result.success(reviewService.rate(problemId, request));
    }

    @GetMapping
    public Result<List<ReviewRecordVO>> history(@RequestParam String problemId) {
        return Result.success(reviewService.history(problemId));
    }
}
