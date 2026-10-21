package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.dto.CreateTagRequest;
import com.baimo.dsalogserver.dto.UpdateTagRequest;
import com.baimo.dsalogserver.service.TagService;
import com.baimo.dsalogserver.vo.TagUsageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public Result<List<TagUsageVO>> list(@RequestParam(required = false) String keyword) {
        return Result.success(tagService.list(keyword));
    }

    @PostMapping
    public Result<TagUsageVO> create(@RequestBody CreateTagRequest request) {
        return Result.success(tagService.create(request));
    }

    /** 部分更新：字段为 null 不改；清空名字会被 400 拒绝（名字是必填项） */
    @PatchMapping("/{id}")
    public Result<TagUsageVO> update(@PathVariable String id, @RequestBody UpdateTagRequest request) {
        return Result.success(tagService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable String id) {
        tagService.remove(id);
        return Result.success();
    }
}
