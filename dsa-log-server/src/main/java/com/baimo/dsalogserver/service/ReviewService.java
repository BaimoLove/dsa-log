package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.dto.RateReviewRequest;
import com.baimo.dsalogserver.vo.DueReviewVO;
import com.baimo.dsalogserver.vo.RateVO;
import com.baimo.dsalogserver.vo.ReviewRecordVO;

import java.util.List;

/**
 * 复习业务。评分算法在 {@link ReviewScheduler}，这里只负责取数、加锁与落库。
 */
public interface ReviewService {

    /** 待复习队列；limit 只截断 items，不影响 total */
    DueReviewVO due(Integer limit);

    /** 三档评分：改排期 + 写一条 REVIEW 流水 */
    RateVO rate(String problemId, RateReviewRequest req);

    /** 某题的复习历史，按时间倒序 */
    List<ReviewRecordVO> history(String problemId);
}
