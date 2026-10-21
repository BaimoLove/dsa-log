package com.baimo.dsalogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 动态条件分页查询。count 语句与 LIMIT 由 PaginationInnerInterceptor 生成，
     * XML 里只写业务条件，不写 offset/size。
     */
    IPage<Problem> selectPageList(IPage<Problem> page, @Param("q") ProblemQuery q);

    /** 标记已刷/取消已刷时加行锁，避免并发下重复写 solved_at */
    Problem selectForUpdate(@Param("id") String id);

    /** 随机一题；status 为空表示全库，excludeIds 为空表示不排除 */
    Problem selectRandomOne(@Param("status") String status,
                            @Param("excludeIds") List<String> excludeIds);

    /**
     * 随机抽题的候选数，与 {@link #selectRandomOne} 同一套条件——两处条件必须写成一样的，
     * 否则会出现「说从 3 道里抽、实际从 5 道里抽」。只用于回显提示，不参与选行。
     */
    long countCandidates(@Param("status") String status,
                         @Param("excludeIds") List<String> excludeIds);

    /**
     * 待复习队列：status='solved' 且 next_review_at &lt;= now，按到期时间升序。
     * 判据刻意不用「今天 23:59」，否则早上到期的题在中午看不到。
     */
    List<Problem> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 待复习总数：不受 limit 影响，纯索引区间扫描（idx_due 三列全覆盖） */
    long countDue(@Param("now") LocalDateTime now);

    /**
     * 批量加锁查询。批量操作要先读出「变更前的行」才能写复习流水与算 affected，
     * 排序后再加锁，避免两个批次以不同顺序拿锁互相等待。
     */
    List<Problem> selectByIdsForUpdate(@Param("ids") List<String> ids);

    /** 命中且未删的 id。affected 一律按它计，不按入参长度计 */
    List<String> selectExistingIds(@Param("ids") List<String> ids);

    /** 缺少任一目标标签的题 id：affected 按「题数」而非「新增关联行数」计 */
    List<String> selectIdsNeedTags(@Param("ids") List<String> ids,
                                   @Param("tagIds") List<String> tagIds,
                                   @Param("tagCount") int tagCount);

    /**
     * 批量标记已刷。首次（next_review_at 为 null）进复习池，
     * 重复刷只累加计数、保留原排期。
     */
    int markSolvedBatch(@Param("ids") List<String> ids,
                        @Param("now") LocalDateTime now,
                        @Param("firstReviewAt") LocalDateTime firstReviewAt);

    /** 批量取消已刷：只动 status='solved' 的行，history 字段保留 */
    int markUnsolvedBatch(@Param("ids") List<String> ids, @Param("now") LocalDateTime now);

    /** 批量逻辑删。@TableLogic 只作用于 MP 生成的方法，手写 SQL 要显式带条件 */
    int logicalDeleteBatch(@Param("ids") List<String> ids, @Param("now") LocalDateTime now);

    /** 刷新 updated_at，用于打标签这类不改变 status 的变更 */
    int touchBatch(@Param("ids") List<String> ids, @Param("now") LocalDateTime now);

    /**
     * 清空复习排期。不能靠 updateById：MP 默认 FieldStrategy.NOT_NULL，
     * 实体里把 nextReviewAt 置 null 后该列不会进 SET 子句，等于没清。
     */
    int clearReviewSchedule(@Param("id") String id, @Param("now") LocalDateTime now);

    /** 导入用：一次 upsert 一批题目（已存在则整行覆盖） */
    int upsertBatch(@Param("items") List<Problem> items);

    /** 导入 replace 模式用：物理清空（逻辑删会留下 deleted=1 的脏行占据唯一键） */
    int physicalDeleteAll();

    /**
     * 导入分类用：{@code @TableLogic} 会让 selectList 过滤掉已删行，
     * 而导入必须看到它们——否则同 id 的已删行会被当成「不存在」，
     * created/skipped 的计数就错了（数据本身靠 upsert 兜住）。
     */
    List<Problem> selectAllIncludingDeleted();
}
