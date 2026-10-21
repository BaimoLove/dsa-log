package com.baimo.dsalogserver.converter;

import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.vo.ProblemVO;
import com.baimo.dsalogserver.vo.TagRow;
import com.baimo.dsalogserver.vo.TagVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 手写转换（不用 MapStruct）：字段少、聚合逻辑多，手写更直观。
 * 时间在这里统一转 epoch 毫秒，VO 里不出现 LocalDateTime。
 */
@Component
public class ProblemConverter {

    public ProblemVO toVO(Problem p, List<TagRow> tagRows) {
        ProblemVO vo = new ProblemVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setDifficulty(p.getDifficulty());
        vo.setStatus(p.getStatus());
        vo.setApproach(p.getApproach());
        vo.setNote(p.getNote());
        vo.setSource(p.getSource());
        vo.setUrl(p.getUrl());
        vo.setSolvedAt(TimeUtil.toMillis(p.getSolvedAt()));
        vo.setLastSolvedAt(TimeUtil.toMillis(p.getLastSolvedAt()));
        vo.setSolveCount(p.getSolveCount() == null ? 0 : p.getSolveCount());
        vo.setReviewStage(p.getReviewStage() == null ? 0 : p.getReviewStage());
        vo.setNextReviewAt(TimeUtil.toMillis(p.getNextReviewAt()));
        vo.setLastReviewedAt(TimeUtil.toMillis(p.getLastReviewedAt()));
        vo.setCreatedAt(TimeUtil.toMillis(p.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toMillis(p.getUpdatedAt()));

        if (tagRows != null) {
            for (TagRow row : tagRows) {
                TagVO tag = new TagVO();
                tag.setId(row.getId());
                tag.setName(row.getName());
                tag.setColor(row.getColor());
                vo.getTags().add(tag);
                vo.getTagIds().add(row.getId());
            }
        }
        return vo;
    }
}
