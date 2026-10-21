package com.baimo.dsalogserver.converter;

import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.entity.ProblemList;
import com.baimo.dsalogserver.vo.ListRow;
import com.baimo.dsalogserver.vo.ListVO;
import org.springframework.stereotype.Component;

/**
 * 题单转换。与 ProblemConverter 同一口径：时间在转换层一次性转 epoch 毫秒，
 * VO 里不出现 LocalDateTime；实体到 VO 与投影到 VO 两条路径共用这一个出口。
 */
@Component
public class ListConverter {

    public ListVO toVO(ProblemList list) {
        return toVO(list, 0, 0);
    }

    public ListVO toVO(ListRow row) {
        return toVO(row, row.getTotal(), row.getSolved());
    }

    private ListVO toVO(ProblemList list, long total, long solved) {
        ListVO vo = new ListVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription() == null ? "" : list.getDescription());
        vo.setColor(list.getColor());
        vo.setSortOrder(list.getSortOrder() == null ? 0 : list.getSortOrder());
        vo.setCreatedAt(TimeUtil.toMillis(list.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toMillis(list.getUpdatedAt()));
        vo.setTotal(total);
        vo.setSolved(solved);
        return vo;
    }

    private ListVO toVO(ListRow row, long total, long solved) {
        ListVO vo = new ListVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setDescription(row.getDescription() == null ? "" : row.getDescription());
        vo.setColor(row.getColor());
        vo.setSortOrder(row.getSortOrder() == null ? 0 : row.getSortOrder());
        vo.setCreatedAt(TimeUtil.toMillis(row.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toMillis(row.getUpdatedAt()));
        vo.setTotal(total);
        vo.setSolved(solved);
        return vo;
    }
}
