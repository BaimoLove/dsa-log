package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.dto.UpdateSettingsRequest;
import com.baimo.dsalogserver.service.SettingService;
import com.baimo.dsalogserver.vo.SettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public Result<SettingsVO> get() {
        return Result.success(settingService.get());
    }

    /** 部分更新：字段为 null 不改；越界值直接 400，不静默钳制 */
    @PutMapping
    public Result<SettingsVO> update(@RequestBody UpdateSettingsRequest request) {
        return Result.success(settingService.update(request));
    }
}
