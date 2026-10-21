package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.service.StatsService;
import com.baimo.dsalogserver.vo.OverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public Result<OverviewVO> overview() {
        return Result.success(statsService.overview());
    }
}
