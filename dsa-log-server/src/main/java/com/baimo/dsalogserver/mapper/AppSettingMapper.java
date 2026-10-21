package com.baimo.dsalogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baimo.dsalogserver.entity.AppSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppSettingMapper extends BaseMapper<AppSetting> {

    /**
     * 批量 upsert。配置项写入永远是「有则改、无则插」，
     * 逐条先查再写会多一轮往返，也没必要。
     */
    int upsertBatch(@Param("items") List<AppSetting> items);
}
