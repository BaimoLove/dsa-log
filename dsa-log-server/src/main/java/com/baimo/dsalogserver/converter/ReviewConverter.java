package com.baimo.dsalogserver.converter;

import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.entity.ReviewRecord;
import com.baimo.dsalogserver.vo.ReviewRecordVO;
import org.springframework.stereotype.Component;

/** 复习流水转换：历史接口与导出备份共用，时间只在这里转一次 epoch 毫秒 */
@Component
public class ReviewConverter {

    public ReviewRecordVO toVO(ReviewRecord r) {
        ReviewRecordVO vo = new ReviewRecordVO();
        vo.setId(r.getId());
        vo.setProblemId(r.getProblemId());
        vo.setKind(r.getKind());
        vo.setRating(r.getRating());
        vo.setFromStage(r.getFromStage());
        vo.setToStage(r.getToStage());
        vo.setIntervalDays(r.getIntervalDays());
        vo.setNextReviewAt(TimeUtil.toMillis(r.getNextReviewAt()));
        vo.setReviewedAt(TimeUtil.toMillis(r.getReviewedAt()));
        return vo;
    }
}
