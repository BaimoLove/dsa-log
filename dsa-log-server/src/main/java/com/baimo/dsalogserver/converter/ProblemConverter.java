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
 *
 * <p>四个文本字段（approach/note/source/url）库里是 NULL，出参一律转成空串：
 * 前端 `Problem` 模型里它们是必填 string，而备份文件的 zod schema 写的是
 * `z.string().default('')`——default 只对 undefined 生效，null 会直接校验失败。
 * 与其让前端每个入口都写一次 `?? ''`，不如在唯一的转换出口归一化。
 */
@Component
public class ProblemConverter {

    public ProblemVO toVO(Problem p, List<TagRow> tagRows) {
        ProblemVO vo = new ProblemVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setDifficulty(p.getDifficulty());
        vo.setStatus(p.getStatus());
        vo.setApproach(nz(p.getApproach()));
        vo.setNote(nz(p.getNote()));
        vo.setSource(nz(p.getSource()));
        vo.setUrl(nz(p.getUrl()));
        vo.setSolvedAt(TimeUtil.toMillis(p.getSolvedAt()));
        vo.setLastSolvedAt(TimeUtil.toMillis(p.getLastSolvedAt()));
        vo.setSolveCount(p.getSolveCount() == null ? 0 : p.getSolveCount());
        vo.setReviewStage(p.getReviewStage() == null ? 0 : p.getReviewStage());
        vo.setNextReviewAt(TimeUtil.toMillis(p.getNextReviewAt()));
        vo.setLastReviewedAt(TimeUtil.toMillis(p.getLastReviewedAt()));
        vo.setCreatedAt(TimeUtil.toMillis(p.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toMillis(p.getUpdatedAt()));
        // 库里是 NOT NULL DEFAULT 0，但导入的老文件可能没有这个字段，转 VO 时兜个 0
        vo.setArchived(p.getArchived() == null ? 0 : p.getArchived());

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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
