package com.baimo.dsalogserver.converter;

import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.vo.TagUsageRow;
import com.baimo.dsalogserver.vo.TagUsageVO;
import org.springframework.stereotype.Component;

/**
 * 标签转换：唯一的出口。标签列表、新建/改名回参、导出备份都走这里，
 * 免得三处各自决定「createdAt 为空时给 null 还是 0」。
 */
@Component
public class TagConverter {

    public TagUsageVO toVO(TagUsageRow row) {
        TagUsageVO vo = new TagUsageVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setColor(row.getColor());
        vo.setCreatedAt(TimeUtil.toMillis(row.getCreatedAt()));
        vo.setUsageCount(row.getUsageCount());
        return vo;
    }
}
