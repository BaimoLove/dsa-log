package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.converter.ProblemConverter;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.mapper.ProblemListItemMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.mapper.TagMapper;
import com.baimo.dsalogserver.service.impl.ProblemServiceImpl;
import com.baimo.dsalogserver.vo.RandomPickVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 随机抽题的四级级联与元信息。
 *
 * <p>算法本身（均匀性、下标夹取）在前端 `randomPicker.test.ts` 里覆盖，
 * 这里钉的是「后端这一侧返回的 pool / reset / candidateCount 与命中级别一致」——
 * M8 之前后端只返题目，这三个值由前端自己推演，是两套口径最容易分叉的地方。
 */
@ExtendWith(MockitoExtension.class)
class ProblemRandomTest {

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
    @Spy
    private ProblemConverter converter = new ProblemConverter();
    @InjectMocks
    private ProblemServiceImpl service;

    private static Problem problem(String id, String status) {
        Problem p = new Problem();
        p.setId(id);
        p.setTitle("题 " + id);
        p.setDifficulty("easy");
        p.setStatus(status);
        return p;
    }

    private void stubTags(String id) {
        when(problemTagMapper.selectByProblemIds(List.of(id))).thenReturn(List.of());
    }

    @Test
    @DisplayName("未刷池还有没抽过的题：pool=unsolved、reset=false、候选数是未刷去掉已抽")
    void firstLevel() {
        stubTags("p1");
        when(problemMapper.selectRandomOne("todo", List.of("p0"))).thenReturn(problem("p1", "todo"));
        when(problemMapper.countCandidates("todo", List.of("p0"))).thenReturn(4L);

        RandomPickVO vo = service.random(null, List.of("p0"));

        assertThat(vo.getPool()).isEqualTo("unsolved");
        assertThat(vo.getReset()).isFalse();
        assertThat(vo.getCandidateCount()).isEqualTo(4L);
        assertThat(vo.getProblem().getId()).isEqualTo("p1");
    }

    @Test
    @DisplayName("未刷池本轮抽完：reset=true、候选数是未刷总数（不再排除已抽）")
    void secondLevelResets() {
        stubTags("p1");
        when(problemMapper.selectRandomOne("todo", List.of("p0"))).thenReturn(null);
        when(problemMapper.selectRandomOne("todo", List.of())).thenReturn(problem("p1", "todo"));
        when(problemMapper.countCandidates("todo", List.of())).thenReturn(6L);

        RandomPickVO vo = service.random(null, List.of("p0"));

        assertThat(vo.getPool()).isEqualTo("unsolved");
        assertThat(vo.getReset()).isTrue();
        assertThat(vo.getCandidateCount()).isEqualTo(6L);
    }

    @Test
    @DisplayName("未刷为 0：退到全库且不算重置，pool=all、reset=false")
    void thirdLevelFallsBackToAll() {
        stubTags("p2");
        when(problemMapper.selectRandomOne("todo", List.of("p0"))).thenReturn(null);
        when(problemMapper.selectRandomOne("todo", List.of())).thenReturn(null);
        when(problemMapper.selectRandomOne(null, List.of("p0"))).thenReturn(problem("p2", "solved"));
        when(problemMapper.countCandidates(null, List.of("p0"))).thenReturn(9L);

        RandomPickVO vo = service.random(null, List.of("p0"));

        assertThat(vo.getPool()).isEqualTo("all");
        assertThat(vo.getReset()).isFalse();
        assertThat(vo.getCandidateCount()).isEqualTo(9L);
    }

    @Test
    @DisplayName("全库也抽完：pool=all、reset=true")
    void fourthLevelResetsAll() {
        stubTags("p3");
        when(problemMapper.selectRandomOne("todo", List.of("p0"))).thenReturn(null);
        when(problemMapper.selectRandomOne("todo", List.of())).thenReturn(null);
        when(problemMapper.selectRandomOne(null, List.of("p0"))).thenReturn(null);
        when(problemMapper.selectRandomOne(null, List.of())).thenReturn(problem("p3", "solved"));
        when(problemMapper.countCandidates(null, List.of())).thenReturn(12L);

        RandomPickVO vo = service.random(null, List.of("p0"));

        assertThat(vo.getPool()).isEqualTo("all");
        assertThat(vo.getReset()).isTrue();
        assertThat(vo.getCandidateCount()).isEqualTo(12L);
    }

    @Test
    @DisplayName("pool=all 直接跳过未刷池，连一次 todo 查询都不发")
    void poolAllSkipsUnsolved() {
        stubTags("p4");
        when(problemMapper.selectRandomOne(null, List.of("p0"))).thenReturn(problem("p4", "solved"));
        when(problemMapper.countCandidates(null, List.of("p0"))).thenReturn(3L);

        RandomPickVO vo = service.random("all", List.of("p0"));

        assertThat(vo.getPool()).isEqualTo("all");
        verify(problemMapper, never()).selectRandomOne(eq("todo"), anyList());
    }

    @Test
    @DisplayName("题库为空：返回 null，且不去算候选数")
    void emptyBankReturnsNull() {
        when(problemMapper.selectRandomOne("todo", List.of())).thenReturn(null);
        when(problemMapper.selectRandomOne(null, List.of())).thenReturn(null);

        assertThat(service.random(null, null)).isNull();
        verify(problemMapper, never()).countCandidates(any(), anyList());
    }

    @Test
    @DisplayName("exclude 的空白与重复被归一化后再查询，避免 NOT IN 里塞脏值")
    void excludeNormalized() {
        stubTags("p1");
        when(problemMapper.selectRandomOne("todo", List.of("p1"))).thenReturn(problem("p1", "todo"));
        when(problemMapper.countCandidates("todo", List.of("p1"))).thenReturn(2L);

        RandomPickVO vo = service.random(null, List.of(" p1 ", "p1", "  "));

        assertThat(vo.getCandidateCount()).isEqualTo(2L);
        verify(problemMapper).selectRandomOne("todo", List.of("p1"));
    }
}
