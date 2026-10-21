package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.ErrorCode;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.entity.ReviewRecord;
import com.baimo.dsalogserver.entity.Tag;
import com.baimo.dsalogserver.mapper.ProblemListItemMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.mapper.TagMapper;
import com.baimo.dsalogserver.service.impl.BatchServiceImpl;
import com.baimo.dsalogserver.vo.BatchVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量操作的契约测试。不连库：这些用例要钉住的是「口径」——
 * affected 怎么算、哪些分支不该落库、空集合与超限抛什么码，
 * 而不是 MyBatis 生成的 SQL 长什么样（那部分靠 curl 实测）。
 */
@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemTagMapper problemTagMapper;
    @Mock
    private ProblemListItemMapper problemListItemMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ReviewRecordMapper reviewRecordMapper;
    @Mock
    private ListService listService;

    @InjectMocks
    private BatchServiceImpl service;

    @Test
    @DisplayName("ids 为空或全是空白：参数错误，不碰数据库")
    void emptyIds() {
        assertThatThrownBy(() -> service.markSolved(null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> service.markUnsolved(List.of(" ", "\t")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("超过 500 条：413 而不是 400，与参数格式错误区分")
    void tooManyIds() {
        List<String> ids = java.util.stream.IntStream.rangeClosed(1, 501)
                .mapToObj(i -> "p" + i).toList();
        assertThatThrownBy(() -> service.remove(ids))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("标记已刷：affected 按命中行数计，不按入参长度计")
    void markSolvedCountsHits() {
        Problem p1 = problem("p1", "todo", null);
        Problem p2 = problem("p2", "solved", LocalDateTime.now().plusDays(3));
        when(problemMapper.selectByIdsForUpdate(anyList())).thenReturn(List.of(p1, p2));

        BatchVO vo = service.markSolved(List.of("p1", "p2", "not-exist"));

        assertThat(vo.affected()).isEqualTo(2);
        verify(problemMapper).markSolvedBatch(eq(List.of("p1", "p2")), any(), any());
        assertThat(capturedRecords()).hasSize(2)
                .allSatisfy(r -> assertThat(r.getKind()).isEqualTo("MARK_SOLVED"));
    }

    @Test
    @DisplayName("标记已刷：一题都没命中时不写流水也不执行 UPDATE")
    void markSolvedNoHits() {
        when(problemMapper.selectByIdsForUpdate(anyList())).thenReturn(List.of());

        assertThat(service.markSolved(List.of("x")).affected()).isZero();
        verify(problemMapper, never()).markSolvedBatch(anyList(), any(), any());
        verify(reviewRecordMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("取消已刷：只统计真正的 solved 行，todo 行跳过且不写流水")
    void markUnsolvedOnlySolved() {
        Problem solved = problem("p1", "solved", LocalDateTime.now());
        Problem todo = problem("p2", "todo", null);
        when(problemMapper.selectByIdsForUpdate(anyList())).thenReturn(List.of(solved, todo));

        BatchVO vo = service.markUnsolved(List.of("p1", "p2"));

        assertThat(vo.affected()).isEqualTo(1);
        verify(problemMapper).markUnsolvedBatch(eq(List.of("p1")), any());
        assertThat(capturedRecords()).singleElement()
                .satisfies(r -> assertThat(r.getKind()).isEqualTo("MARK_UNSOLVED"));
    }

    @Test
    @DisplayName("打标签：标签库里一个都不存在时 affected=0，不插关联")
    void addTagsWithoutValidTag() {
        when(tagMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.addTags(List.of("p1"), List.of("nope")).affected()).isZero();
        verify(problemTagMapper, never()).insertIgnoreBatch(anyList(), anyList());
    }

    @Test
    @DisplayName("打标签：已全部包含的题不重复插关联，affected=0")
    void addTagsAlreadyHasAll() {
        when(tagMapper.selectList(any())).thenReturn(List.of(tag("t1")));
        when(problemMapper.selectExistingIds(anyList())).thenReturn(List.of("p1"));
        when(problemMapper.selectIdsNeedTags(anyList(), anyList(), anyInt())).thenReturn(List.of());

        assertThat(service.addTags(List.of("p1"), List.of("t1")).affected()).isZero();
        verify(problemTagMapper, never()).insertIgnoreBatch(anyList(), anyList());
        verify(problemMapper, never()).touchBatch(anyList(), any());
    }

    @Test
    @DisplayName("打标签：affected 按缺标签的题数计，不是关联行数")
    void addTagsCountsProblems() {
        when(tagMapper.selectList(any())).thenReturn(List.of(tag("t1"), tag("t2")));
        when(problemMapper.selectExistingIds(anyList())).thenReturn(List.of("p1", "p2"));
        when(problemMapper.selectIdsNeedTags(anyList(), anyList(), anyInt())).thenReturn(List.of("p1"));

        assertThat(service.addTags(List.of("p1", "p2"), List.of("t1", "t2")).affected()).isEqualTo(1);
        verify(problemTagMapper).insertIgnoreBatch(List.of("p1"), List.of("t1", "t2"));
    }

    @Test
    @DisplayName("加入题单：委托 ListService，本类不再自己算 affected")
    void addToListDelegates() {
        // 「查缺失 → 取序号 → INSERT IGNORE」只在 ListServiceImpl 里有一份，
        // 两个端点调出同一个数字，所以这里只验转发（细节见 ListServiceTest）
        when(listService.addProblems("l1", List.of("p1", "p2"))).thenReturn(new BatchVO(1));

        assertThat(service.addToList(List.of("p1", "p2"), "l1").affected()).isEqualTo(1);
        verify(listService).addProblems("l1", List.of("p1", "p2"));
        verify(problemListItemMapper, never()).insertIgnoreBatch(anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("批量删除：只删真实存在的题，先清关联再逻辑删")
    void removeOnlyExisting() {
        when(problemMapper.selectExistingIds(anyList())).thenReturn(List.of("p1"));

        assertThat(service.remove(List.of("p1", "ghost")).affected()).isEqualTo(1);
        verify(problemTagMapper).deleteByProblemIds(List.of("p1"));
        verify(problemListItemMapper).deleteByProblemIds(List.of("p1"));
        verify(problemMapper).logicalDeleteBatch(eq(List.of("p1")), any());
    }

    @Test
    @DisplayName("批量删除：一题都不存在时不执行任何写操作")
    void removeNothing() {
        when(problemMapper.selectExistingIds(anyList())).thenReturn(List.of());

        assertThat(service.remove(List.of("ghost")).affected()).isZero();
        verify(problemMapper, never()).logicalDeleteBatch(anyList(), any());
        verify(problemTagMapper, never()).deleteByProblemIds(anyList());
    }

    // ---------------- 辅助 ----------------

    private List<ReviewRecord> capturedRecords() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReviewRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewRecordMapper).insertBatch(captor.capture());
        return captor.getValue();
    }

    private static Problem problem(String id, String status, LocalDateTime nextReviewAt) {
        Problem p = new Problem();
        p.setId(id);
        p.setStatus(status);
        p.setReviewStage(nextReviewAt == null ? 0 : 1);
        p.setNextReviewAt(nextReviewAt);
        p.setSolveCount(nextReviewAt == null ? 0 : 1);
        return p;
    }

    private static Tag tag(String id) {
        Tag t = new Tag();
        t.setId(id);
        return t;
    }
}
