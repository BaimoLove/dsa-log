package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.dto.UpdateSettingsRequest;
import com.baimo.dsalogserver.vo.SettingsVO;

/**
 * 设置读写。键名与默认值集中在这一层，别处不出现 "review.dailyLimit" 这类字面量。
 */
public interface SettingService {

    SettingsVO get();

    /** 部分更新：字段为 null 不改 */
    SettingsVO update(UpdateSettingsRequest req);

    /** 整份覆盖，导入的 replace 模式用；会校验全部字段 */
    void replaceAll(SettingsVO settings);
}
