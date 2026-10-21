package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.ErrorCode;
import com.baimo.dsalogserver.converter.ListConverter;
import com.baimo.dsalogserver.converter.ProblemConverter;
import com.baimo.dsalogserver.converter.ReviewConverter;
import com.baimo.dsalogserver.converter.TagConverter;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.entity.ProblemList;
import com.baimo.dsalogserver.mapper.ProblemListItemMapper;
import com.baimo.dsalogserver.mapper.ProblemListMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.mapper.TagMapper;
import com.baimo.dsalogserver.service.impl.BackupServiceImpl;
import com.baimo.dsalogserver.vo.BackupVO;
import com.baimo.dsalogserver.vo.ImportResultVO;
import com.baimo.dsalogserver.vo.ProblemVO;
import com.baimo.dsalogserver.vo.SettingsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 备份契约：版本闸门、replace 的 confirm、merge 的 updatedAt 规则。
 * 真实往返（导出→导入→数据一致）由 curl 实测覆盖，这里只钉分支。
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemTagMapper problemTagMapper;
    @Mock
    private ProblemListMapper problemListMapper;
    @Mock
    private ProblemListItemMapper problemListItemMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ReviewRecordMapper reviewRecordMapper;
    @Mock
    private SettingService settingService;

    @Spy
    private ProblemConverter problemConverter = new ProblemConverter();
    @Spy
    private ListConverter listConverter = new ListConverter();
    @Spy
    private TagConverter tagConverter = new TagConverter();
    @Spy
    private ReviewConverter reviewConverter = new ReviewConverter();

    @InjectMocks
    private BackupServiceImpl service;

    private static ProblemVO problemVO(String id, String title, long updatedAt) {
        ProblemVO vo = new ProblemVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setDifficulty("easy");
        vo.setStatus("solved");
        vo.setSolveCount(1);
        vo.setReviewStage(1);
        vo.setUpdatedAt(updatedAt);
        vo.setCreatedAt(updatedAt);
        return vo;
    }

    private static BackupVO payload(Integer version, ProblemVO... problems) {
        BackupVO vo = new BackupVO();
        vo.setSchemaVersion(version);
        vo.setProblems(List.of(problems));
        return vo;
    }

    @Test
    @DisplayName("版本不匹配 / 缺版本 → 422 IMPORT_INVALID")
    void versionGate() {
        assertThatThrownBy(() -> service.importBackup(payload(99), "merge", null))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMPORT_INVALID);
        assertThatThrownBy(() -> service.importBackup(payload(null), "merge", null))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMPORT_INVALID);
        verify(problemMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("replace 不带 confirm=REPLACE → 400，且一条都没删")
    void replaceNeedsConfirm() {
        assertThatThrownBy(() -> service.importBackup(payload(1, problemVO("p1", "x", 1000L)), "replace", null))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(problemMapper, never()).physicalDeleteAll();
        verify(tagMapper, never()).deleteAll();
    }

    @Test
    @DisplayName("mode 只认 merge / replace")
    void invalidMode() {
        assertThatThrownBy(() -> service.importBackup(payload(1), "overwrite", null))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("replace 带 confirm：先清六张表再写入，created 计数正确")
    void replaceWrites() {
        when(problemMapper.selectAllIncludingDeleted()).thenReturn(List.of());

        ImportResultVO result = service.importBackup(
                payload(1, problemVO("p1", "两数之和", 1000L), problemVO("p2", "爬楼梯", 1000L)),
                "replace", " REPLACE ");

        assertThat(result.getMode()).isEqualTo("replace");
        assertThat(result.getProblems().getCreated()).isEqualTo(2);
        verify(problemMapper).physicalDeleteAll();
        verify(problemListMapper).physicalDeleteAll();
        verify(problemTagMapper).deleteAll();
        verify(problemListItemMapper).deleteAll();
        verify(reviewRecordMapper).deleteAll();
        verify(tagMapper).deleteAll();
        verify(problemMapper).upsertBatch(anyList());
    }

    @Test
    @DisplayName("merge：库里更新则 skipped，文件更新则覆盖")
    void mergeByUpdatedAt() {
        Problem existing = new Problem();
        existing.setId("p1");
        existing.setTitle("库里的标题");
        existing.setUpdatedAt(LocalDateTime.of(2026, 10, 21, 12, 0, 0));
        when(problemMapper.selectAllIncludingDeleted()).thenReturn(List.of(existing));

        long older = LocalDateTime.of(2026, 10, 20, 12, 0, 0)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        ImportResultVO skipped = service.importBackup(payload(1, problemVO("p1", "旧文件", older)), "merge", null);
        assertThat(skipped.getProblems().getSkipped()).isEqualTo(1);
        assertThat(skipped.getProblems().getUpdated()).isZero();
        verify(problemMapper, never()).upsertBatch(anyList());

        long newer = LocalDateTime.of(2026, 10, 22, 12, 0, 0)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        ImportResultVO updated = service.importBackup(payload(1, problemVO("p1", "新文件", newer)), "merge", null);
        assertThat(updated.getProblems().getUpdated()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Problem>> captor = ArgumentCaptor.forClass(List.class);
        verify(problemMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue().get(0).getTitle()).isEqualTo("新文件");
    }

    @Test
    @DisplayName("merge 不动设置，replace 才还原")
    void settingsOnlyOnReplace() {
        SettingsVO settings = new SettingsVO();
        settings.setDailyReviewLimit(7);
        BackupVO merge = payload(1);
        merge.setSettings(settings);

        service.importBackup(merge, "merge", null);
        verify(settingService, never()).replaceAll(any());

        BackupVO replace = payload(1);
        replace.setSettings(settings);
        service.importBackup(replace, "replace", "REPLACE");
        verify(settingService).replaceAll(settings);
    }

    @Test
    @DisplayName("导出：六块数据齐全，schemaVersion=1，时间仍是毫秒")
    void exportAll() {
        Problem p = new Problem();
        p.setId("p1");
        p.setTitle("两数之和");
        p.setDifficulty("easy");
        p.setStatus("solved");
        p.setSolveCount(1);
        p.setReviewStage(0);
        p.setCreatedAt(LocalDateTime.of(2026, 10, 21, 10, 0, 0));
        p.setUpdatedAt(LocalDateTime.of(2026, 10, 21, 10, 0, 0));
        when(problemMapper.selectList(any())).thenReturn(List.of(p));
        when(problemTagMapper.selectByProblemIds(anyList())).thenReturn(List.of());
        when(tagMapper.selectWithUsage(any())).thenReturn(List.of());
        when(problemListMapper.selectAllWithProgress()).thenReturn(List.of());
        when(problemListItemMapper.selectAll()).thenReturn(List.of());
        when(reviewRecordMapper.selectList(any())).thenReturn(List.of());
        when(settingService.get()).thenReturn(new SettingsVO());

        BackupVO payload = service.exportAll();
        assertThat(payload.getSchemaVersion()).isEqualTo(1);
        assertThat(payload.getApp()).isEqualTo("dsa-log");
        assertThat(payload.getProblems()).hasSize(1);
        assertThat(payload.getProblems().get(0).getCreatedAt())
                .isEqualTo(com.baimo.dsalogserver.common.TimeUtil.toMillis(
                        LocalDateTime.of(2026, 10, 21, 10, 0, 0)));
        assertThat(payload.getExportedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("CSV：带 BOM 与表头，时间写成 ISO")
    void exportCsv() {
        Problem p = new Problem();
        p.setId("p1");
        p.setTitle("含,逗号");
        p.setDifficulty("easy");
        p.setStatus("todo");
        p.setSolveCount(0);
        p.setReviewStage(0);
        p.setCreatedAt(LocalDateTime.of(2026, 10, 21, 10, 30, 0));
        p.setUpdatedAt(LocalDateTime.of(2026, 10, 21, 10, 30, 0));
        when(problemMapper.selectList(any())).thenReturn(List.of(p));
        when(problemTagMapper.selectByProblemIds(anyList())).thenReturn(List.of());
        when(tagMapper.selectWithUsage(any())).thenReturn(List.of());
        when(problemListMapper.selectAllWithProgress()).thenReturn(List.of());
        when(problemListItemMapper.selectAll()).thenReturn(List.of());
        when(reviewRecordMapper.selectList(any())).thenReturn(List.of());
        when(settingService.get()).thenReturn(new SettingsVO());

        String csv = service.exportCsv();
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("id,title,difficulty,status,tags");
        assertThat(csv).contains("\"含,逗号\"");
        assertThat(csv).contains("2026-10-21 10:30");
    }
}
