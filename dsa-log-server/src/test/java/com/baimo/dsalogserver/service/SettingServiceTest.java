package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.ErrorCode;
import com.baimo.dsalogserver.dto.UpdateSettingsRequest;
import com.baimo.dsalogserver.entity.AppSetting;
import com.baimo.dsalogserver.mapper.AppSettingMapper;
import com.baimo.dsalogserver.service.impl.SettingServiceImpl;
import com.baimo.dsalogserver.vo.SettingsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设置读写契约：缺键回默认、脏值也回默认（配置坏了不能拖垮整个站），
 * 写入越界直接 400（静默钳制会让人以为保存成功）。
 */
@ExtendWith(MockitoExtension.class)
class SettingServiceTest {

    @Mock
    private AppSettingMapper settingMapper;

    @InjectMocks
    private SettingServiceImpl service;

    private static AppSetting row(String key, String value) {
        AppSetting a = new AppSetting();
        a.setSettingKey(key);
        a.setSettingValue(value);
        return a;
    }

    @Test
    @DisplayName("get：空表全部回默认")
    void getDefaults() {
        when(settingMapper.selectList(any())).thenReturn(List.of());

        SettingsVO vo = service.get();
        assertThat(vo.getDailyReviewLimit()).isEqualTo(20);
        assertThat(vo.getPageSize()).isEqualTo(20);
        assertThat(vo.getReviewIntervals()).containsExactly(1, 3, 7, 15);
        assertThat(vo.isOverdueFromNow()).isTrue();
    }

    @Test
    @DisplayName("get：正常值解析，越界与脏值回默认")
    void getParses() {
        when(settingMapper.selectList(any())).thenReturn(List.of(
                row(SettingServiceImpl.KEY_DAILY_LIMIT, "50"),
                row(SettingServiceImpl.KEY_PAGE_SIZE, "9999"),
                row(SettingServiceImpl.KEY_INTERVALS, "[2,5,9]"),
                row(SettingServiceImpl.KEY_OVERDUE_FROM_NOW, "false")));

        SettingsVO vo = service.get();
        assertThat(vo.getDailyReviewLimit()).isEqualTo(50);
        assertThat(vo.getPageSize()).isEqualTo(20);
        assertThat(vo.getReviewIntervals()).containsExactly(2, 5, 9);
        assertThat(vo.isOverdueFromNow()).isFalse();
    }

    @Test
    @DisplayName("get：间隔全坏回默认，不是空数组")
    void getBadIntervals() {
        when(settingMapper.selectList(any())).thenReturn(List.of(
                row(SettingServiceImpl.KEY_INTERVALS, "[abc,0]")));

        assertThat(service.get().getReviewIntervals()).containsExactly(1, 3, 7, 15);
    }

    @Test
    @DisplayName("update：全部为 null 时不写库")
    void updateNoop() {
        when(settingMapper.selectList(any())).thenReturn(List.of());
        service.update(new UpdateSettingsRequest());
        verify(settingMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("update：只写传了的字段，间隔格式化成 [a,b,c]")
    void updatePartial() {
        when(settingMapper.selectList(any())).thenReturn(List.of());
        ArgumentCaptor<List<AppSetting>> captor = ArgumentCaptor.forClass(List.class);

        UpdateSettingsRequest req = new UpdateSettingsRequest();
        req.setDailyReviewLimit(30);
        req.setReviewIntervals(List.of(1, 4, 10));
        service.update(req);
        verify(settingMapper).upsertBatch(captor.capture());

        List<AppSetting> items = captor.getValue();
        assertThat(items).hasSize(2);
        assertThat(items).extracting(AppSetting::getSettingKey)
                .containsExactlyInAnyOrder(SettingServiceImpl.KEY_DAILY_LIMIT, SettingServiceImpl.KEY_INTERVALS);
        assertThat(items).filteredOn(i -> i.getSettingKey().equals(SettingServiceImpl.KEY_INTERVALS))
                .extracting(AppSetting::getSettingValue).containsExactly("[1,4,10]");
    }

    @Test
    @DisplayName("update：越界与空间隔都是 400，且不写库")
    void updateValidation() {
        UpdateSettingsRequest tooBig = new UpdateSettingsRequest();
        tooBig.setDailyReviewLimit(201);
        assertThatThrownBy(() -> service.update(tooBig))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        UpdateSettingsRequest badPage = new UpdateSettingsRequest();
        badPage.setPageSize(0);
        assertThatThrownBy(() -> service.update(badPage))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        UpdateSettingsRequest emptyIntervals = new UpdateSettingsRequest();
        emptyIntervals.setReviewIntervals(List.of());
        assertThatThrownBy(() -> service.update(emptyIntervals))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(settingMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("replaceAll：写全四个键，非法值 400")
    void replaceAll() {
        SettingsVO settings = new SettingsVO();
        settings.setDailyReviewLimit(10);
        settings.setPageSize(50);
        settings.setReviewIntervals(List.of(1, 2));
        settings.setOverdueFromNow(false);

        ArgumentCaptor<List<AppSetting>> captor = ArgumentCaptor.forClass(List.class);
        service.replaceAll(settings);
        verify(settingMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(4);

        SettingsVO bad = new SettingsVO();
        bad.setDailyReviewLimit(0);
        bad.setPageSize(20);
        bad.setReviewIntervals(List.of(1));
        assertThatThrownBy(() -> service.replaceAll(bad))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
