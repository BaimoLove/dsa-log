package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.PageResult;
import com.baimo.dsalogserver.dto.CreateProblemRequest;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.dto.UpdateProblemRequest;
import com.baimo.dsalogserver.vo.MarkSolvedVO;
import com.baimo.dsalogserver.vo.ProblemVO;
import com.baimo.dsalogserver.vo.RandomPickVO;

import java.util.List;

/**
 * 题目业务接口，实现见 {@link impl.ProblemServiceImpl}。
 *
 * <p>按阿里分层规约把「对外契约」与「实现」拆开：接口只声明能力与不变式，
 * 事务边界、SQL 组织、标签拼装等细节留在实现类。因此这里不出现
 * {@code @Transactional} —— 手册同样禁止把事务注解写在接口上，
 * 放在实现上才能保证「换实现不会悄悄改变事务语义」。
 */
public interface ProblemService {

    /**
     * 条件分页查询。page 从 1 起，size 夹在 1..100；
     * sort 取 {@code updatedAt_desc/createdAt_desc/difficulty_asc/difficulty_desc/nextReviewAt_asc/solvedAt_desc}，
     * 白名单外回落 {@code updatedAt_desc}。
     */
    PageResult<ProblemVO> list(ProblemQuery query);

    /** 题目详情，不存在抛 notFound */
    ProblemVO get(String id);

    /**
     * 随机一题。pool=all 表示直接跳过未刷池，excludeIds 为本轮已抽过的题；
     * 题库为空返回 null。返回体除题目本身外还带 pool/reset/candidateCount，
     * 因为「这次用的是哪个池子」只有四级级联的执行者知道。
     */
    RandomPickVO random(String pool, List<String> excludeIds);

    /** 新建题目，status 恒为 todo，难度缺省 medium */
    ProblemVO create(CreateProblemRequest request);

    /** 部分更新：null 表示不改，空串表示清空文本，tagIds 传空列表表示清空标签 */
    ProblemVO update(String id, UpdateProblemRequest request);

    /** 逻辑删除题目并清掉标签/题单关联，复习流水保留为历史 */
    void remove(String id);

    /** 标记已刷，返回「是否首次」与下次复习时间 */
    MarkSolvedVO markSolved(String id);

    /** 取消已刷，保留 solvedAt / solveCount 作为历史事实 */
    ProblemVO markUnsolved(String id);
}
