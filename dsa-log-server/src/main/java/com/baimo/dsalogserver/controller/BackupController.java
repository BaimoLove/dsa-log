package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.service.BackupService;
import com.baimo.dsalogserver.vo.BackupVO;
import com.baimo.dsalogserver.vo.ImportResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 导入导出。
 *
 * <p>导出直接返回附件流，不套 Result：浏览器下载要的是文件本身，
 * 套一层 JSON 反而要前端再解一次。
 */
@RestController
@RequiredArgsConstructor
public class BackupController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final BackupService backupService;

    /**
     * @param format json（默认，整库备份）或 csv（只导题目，给人看）
     */
    @GetMapping("/api/export")
    public ResponseEntity<Object> export(@RequestParam(required = false, defaultValue = "json") String format) {
        String stamp = LocalDateTime.now().format(STAMP);
        if ("csv".equalsIgnoreCase(format)) {
            byte[] body = backupService.exportCsv().getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"dsa-log-problems-" + stamp + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv;charset=utf-8"))
                    .body(body);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dsa-log-backup-" + stamp + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(backupService.exportAll());
    }

    /**
     * 全量导入。replace 必须带 confirm=REPLACE，否则 400 —— 这条分支在
     * service 里判，controller 只负责把参数原样传下去。
     */
    @PostMapping("/api/import")
    public Result<ImportResultVO> importBackup(@RequestBody BackupVO payload,
                                               @RequestParam(required = false) String mode,
                                               @RequestParam(required = false) String confirm) {
        return Result.success(backupService.importBackup(payload, mode, confirm));
    }
}
