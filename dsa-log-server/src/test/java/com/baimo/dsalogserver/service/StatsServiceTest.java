package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.converter.ListConverter;
import com.baimo.dsalogserver.mapper.ProblemListMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.service.impl.StatsServiceImpl;
import com.baimo.dsalogserver.vo.ListRow;
import com.baimo.dsalogserver.vo.OverviewVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 概览口径：五个数字各来自哪条 SQL，题单进度与看板同源。
 * 这里钉住「谁调谁」，真实 SQL 的数由 curl 实测对拍。
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ReviewRecordMapper reviewRecordMapper;
    @Mock
    private ProblemListMapper problemListMapper;
    @Spy
    private ListConverter listConverter = new ListConverter();

    @InjectMocks
    private StatsServiceImpl service;

    @Test
    @DisplayName("overview：五个数字 + 题单进度，顺序与口径都按约定")
    void overview() {
        when(problemMapper.countDue(any())).thenReturn(3L);
        // 第一次 selectCount 是总数，第二次是已刷数
        when(problemMapper.selectCount(any())).thenReturn(10L, 6L);
        when(reviewRecordMapper.countDistinctSolvedBetween(any(), any())).thenReturn(2L);
        when(reviewRecordMapper.countReviewBetween(any(), any())).thenReturn(4L);

        ListRow list = new ListRow();
        list.setId("l1");
        list.setName("热题 100");
        list.setColor("#6366f1");
        list.setSortOrder(0);
        list.setCreatedAt(LocalDateTime.of(2026, 10, 20, 9, 0, 0));
        list.setUpdatedAt(LocalDateTime.of(2026, 10, 20, 9, 0, 0));
        list.setTotal(30);
        list.setSolved(12);
        when(problemListMapper.selectAllWithProgress()).thenReturn(List.of(list));

        OverviewVO vo = service.overview();

        assertThat(vo.getDueCount()).isEqualTo(3);
        assertThat(vo.getTotalProblems()).isEqualTo(10);
        assertThat(vo.getSolvedProblems()).isEqualTo(6);
        assertThat(vo.getSolvedToday()).isEqualTo(2);
        assertThat(vo.getReviewedToday()).isEqualTo(4);
        assertThat(vo.getListProgress()).hasSize(1);
        assertThat(vo.getListProgress().get(0).getTotal()).isEqualTo(30);
        assertThat(vo.getListProgress().get(0).getSolved()).isEqualTo(12);
    }
}
