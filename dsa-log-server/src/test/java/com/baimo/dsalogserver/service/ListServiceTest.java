package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.ErrorCode;
import com.baimo.dsalogserver.common.PageResult;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.ListConverter;
import com.baimo.dsalogserver.dto.CreateListRequest;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.dto.UpdateListRequest;
import com.baimo.dsalogserver.entity.ProblemList;
import com.baimo.dsalogserver.mapper.ProblemListItemMapper;
import com.baimo.dsalogserver.mapper.ProblemListMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.service.impl.ListServiceImpl;
import com.baimo.dsalogserver.vo.ListRow;
import com.baimo.dsalogserver.vo.ListVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题单业务的契约测试。不连库，钉住的是「口径与边界」：
 * 进度怎么带出来、名称/颜色的校验落到哪个码、加题幂等、移出不在题单里的题返什么。
 * SQL 是否正确由 curl 实测覆盖，不在单测里重复。
 */
@ExtendWith(MockitoExtension.class)
class ListServiceTest {

    @Mock
    private ProblemListMapper listMapper;
    @Mock
    private ProblemListItemMapper itemMapper;
    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemService problemService;
    @Spy
    private ListConverter converter = new ListConverter();

    @InjectMocks
    private ListServiceImpl service;

    // ---------------- 查询 ----------------

    @Test
    @DisplayName("看板：直接带出 SQL 现算的 total/solved，description 为空也不返 null")
    void listCarriesProgress() {
        when(listMapper.selectAllWithProgress()).thenReturn(List.of(
                row("l1", "热题 100", 30, 12),
                row("l2", "动态规划", 0, 0)));

        List<ListVO> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTotal()).isEqualTo(30);
        assertThat(result.get(0).getSolved()).isEqualTo(12);
        // 空题单也必须在结果里：0/0 不是「查不到」
        assertThat(result.get(1).getTotal()).isZero();
        assertThat(result.get(1).getDescription()).isEmpty();
    }

    @Test
    @DisplayName("详情：题单不存在抛 404")
    void getMissing() {
        when(listMapper.selectOneWithProgress("nope")).thenReturn(null);

        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("新建：名称去空格、颜色缺省、序号取 max+1，进度直接给 0/0")
    void createNormalizes() {
        when(listMapper.nextSortOrder()).thenReturn(3);

        CreateListRequest req = new CreateListRequest();
        req.setName("  热题 100  ");
        ListVO vo = service.create(req);

        ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
        verify(listMapper).insert(captor.capture());
        ProblemList saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("热题 100");
        assertThat(saved.getColor()).isEqualTo("#6366f1");
        assertThat(saved.getSortOrder()).isEqualTo(3);
        assertThat(saved.getDescription()).isEmpty();
        assertThat(saved.getDeleted()).isZero();
        assertThat(saved.getId()).isNotBlank();

        assertThat(vo.getTotal()).isZero();
        assertThat(vo.getSolved()).isZero();
    }

    @Test
    @DisplayName("新建：名称为空白抛 400，不落库")
    void createBlankName() {
        CreateListRequest req = new CreateListRequest();
        req.setName("   ");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(listMapper, never()).insert(any(ProblemList.class));
    }

    @Test
    @DisplayName("新建：颜色不是 #RGB/#RRGGBB 抛 400")
    void createBadColor() {
        CreateListRequest req = new CreateListRequest();
        req.setName("动态规划");
        req.setColor("red");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("部分更新：只传颜色时不动名称，返回值带最新进度")
    void updateOnlyColor() {
        ProblemList existing = entity("l1", "旧名", "#6366f1");
        when(listMapper.selectById("l1")).thenReturn(existing);
        when(listMapper.selectOneWithProgress("l1")).thenReturn(row("l1", "旧名", 10, 4));

        UpdateListRequest req = new UpdateListRequest();
        req.setColor("#ef4444");
        ListVO vo = service.update("l1", req);

        ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
        verify(listMapper).updateById(captor.capture());
        assertThat(captor.getValue().getColor()).isEqualTo("#ef4444");
        assertThat(captor.getValue().getName()).isEqualTo("旧名");
        assertThat(vo.getSolved()).isEqualTo(4);
    }

    @Test
    @DisplayName("部分更新：显式传空名称抛 400（null 才是「不改」）")
    void updateBlankName() {
        when(listMapper.selectById("l1")).thenReturn(entity("l1", "旧名", "#6366f1"));

        UpdateListRequest req = new UpdateListRequest();
        req.setName("  ");

        assertThatThrownBy(() -> service.update("l1", req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(listMapper, never()).updateById(any(ProblemList.class));
    }

    @Test
    @DisplayName("删除：题单逻辑删 + 关联清空，题目本身不动")
    void removeClearsItems() {
        when(listMapper.selectById("l1")).thenReturn(entity("l1", "热题 100", "#6366f1"));

        service.remove("l1");

        verify(listMapper).deleteById("l1");
        verify(itemMapper).deleteByListId("l1");
        // 题单删除不该碰题目表
        verify(problemMapper, never()).logicalDeleteBatch(anyList(), any());
    }

    @Test
    @DisplayName("加入题目：只对不在题单里的题插行，序号接在现有最大值后面")
    void addProblemsOnlyMissing() {
        when(listMapper.countAlive("l1")).thenReturn(1);
        when(problemMapper.selectExistingIds(anyList())).thenReturn(List.of("p1", "p2"));
        when(itemMapper.selectMissingIds("l1", List.of("p1", "p2"))).thenReturn(List.of("p2"));
        when(itemMapper.nextSortOrder("l1")).thenReturn(7);

        assertThat(service.addProblems("l1", List.of("p1", "p1", " p2 ")).affected()).isEqualTo(1);
        verify(itemMapper).insertIgnoreBatch("l1", List.of("p2"), 7);
    }

    @Test
    @DisplayName("加入题目：全都在题单里时 affected=0 且不插行（幂等）")
    void addProblemsIdempotent() {
        when(listMapper.countAlive("l1")).thenReturn(1);
        when(problemMapper.selectExistingIds(anyList())).thenReturn(List.of("p1"));
        when(itemMapper.selectMissingIds("l1", List.of("p1"))).thenReturn(List.of());

        assertThat(service.addProblems("l1", List.of("p1")).affected()).isZero();
        verify(itemMapper, never()).insertIgnoreBatch(anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("加入题目：题单不存在抛 404，不写关联")
    void addProblemsMissingList() {
        when(listMapper.countAlive("nope")).thenReturn(0);

        assertThatThrownBy(() -> service.addProblems("nope", List.of("p1")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(itemMapper, never()).insertIgnoreBatch(anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("加入题目：超过 500 条是 413（数据量超限），不是 400")
    void addProblemsTooMany() {
        when(listMapper.countAlive("l1")).thenReturn(1);
        List<String> ids = IntStream.rangeClosed(1, 501).mapToObj(i -> "p" + i).toList();

        assertThatThrownBy(() -> service.addProblems("l1", ids))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("移出题目：不在题单里抛 404，消息不拼成「不存在」")
    void removeProblemMissing() {
        when(listMapper.countAlive("l1")).thenReturn(1);
        when(itemMapper.deleteOne("l1", "p9")).thenReturn(0);

        assertThatThrownBy(() -> service.removeProblem("l1", "p9"))
                .isInstanceOf(BizException.class)
                .hasMessage("该题不在此题单内")
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("题单内分页：listId 以路径为准，query 里带的值被覆盖")
    void problemsForceListId() {
        when(listMapper.countAlive("l1")).thenReturn(1);
        when(problemService.list(any())).thenReturn(PageResult.of(List.of(), 0, 1, 20));

        ProblemQuery q = new ProblemQuery();
        q.setListId("other");
        q.setStatuses(List.of("solved"));
        service.problems("l1", q);

        ArgumentCaptor<ProblemQuery> captor = ArgumentCaptor.forClass(ProblemQuery.class);
        verify(problemService).list(captor.capture());
        assertThat(captor.getValue().getListId()).isEqualTo("l1");
        // 其余筛选条件原样透传，题单内筛选与题库筛选用的是同一套
        assertThat(captor.getValue().getStatuses()).containsExactly("solved");
    }

    // ---------------- 辅助 ----------------

    private static ListRow row(String id, String name, long total, long solved) {
        ListRow row = new ListRow();
        row.setId(id);
        row.setName(name);
        row.setDescription(null);
        row.setColor("#6366f1");
        row.setSortOrder(0);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
        row.setTotal(total);
        row.setSolved(solved);
        return row;
    }

    private static ProblemList entity(String id, String name, String color) {
        ProblemList entity = new ProblemList();
        entity.setId(id);
        entity.setName(name);
        entity.setDescription("");
        entity.setColor(color);
        entity.setSortOrder(0);
        entity.setCreatedAt(TimeUtil.now());
        entity.setUpdatedAt(TimeUtil.now());
        entity.setDeleted(0);
        return entity;
    }
}
