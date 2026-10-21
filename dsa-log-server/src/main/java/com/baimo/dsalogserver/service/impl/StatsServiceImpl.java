package com.baimo.dsalogserver.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.ListConverter;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.mapper.ProblemListMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.service.StatsService;
import com.baimo.dsalogserver.vo.OverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 概览实现。五个数字各自一条轻量 SQL，合计仍是常数条；
 * 不合并成一个大 UNION 是因为每个口径的索引不同（idx_due / idx_kind_time / 题单进度），
 * 硬合只会让优化器更没得选。
 *
 * <p>题单进度与看板共用 {@code selectAllWithProgress}，所以概览里的
 * 「12/30」与题单页的数字一定一致。
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final ProblemMapper problemMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final ProblemListMapper problemListMapper;
    private final ListConverter listConverter;

    @Override
    public OverviewVO overview() {
        OverviewVO vo = new OverviewVO();
        vo.setDueCount(problemMapper.countDue(TimeUtil.now()));
        // selectCount 由 MP 生成，@TableLogic 会自动带上 deleted = 0；
        // archived = 0 要显式写——归档题不算在题库规模里（前端 Dexie 概览同样只数未归档）
        vo.setTotalProblems(problemMapper.selectCount(
                Wrappers.<Problem>lambdaQuery().eq(Problem::getArchived, 0)));
        vo.setSolvedProblems(problemMapper.selectCount(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getStatus, "solved")
                        .eq(Problem::getArchived, 0)));

        // 今日 = [今日00:00, 明日00:00)，边界在 Java 侧算，SQL 不对索引列套函数
        vo.setSolvedToday(reviewRecordMapper.countDistinctSolvedBetween(
                TimeUtil.startOfToday(), TimeUtil.startOfTomorrow()));
        vo.setReviewedToday(reviewRecordMapper.countReviewBetween(
                TimeUtil.startOfToday(), TimeUtil.startOfTomorrow()));

        vo.setListProgress(problemListMapper.selectAllWithProgress().stream()
                .map(listConverter::toVO).toList());
        return vo;
    }
}
