package com.baimo.dsalogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baimo.dsalogserver.entity.ReviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReviewRecordMapper extends BaseMapper<ReviewRecord> {

    /** 批量写流水：批量已刷/取消已刷时一次落库，不逐条 insert */
    int insertBatch(@Param("records") List<ReviewRecord> records);

    /** 某题的复习历史，按时间倒序 */
    List<ReviewRecord> selectByProblemId(@Param("problemId") String problemId);

    /**
     * 今日「刷过的题数」：同一题刷两次只算一道，所以是 COUNT(DISTINCT problem_id)。
     * 与 reviewedToday（按流水条数）是两个不同口径，不要合并。
     */
    long countDistinctSolvedBetween(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /** 今日「复习了多少次」：按流水条数，同一题复习两次算两次 */
    long countReviewBetween(@Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    /** 导入用：流水只增不改，重复 id 直接忽略 */
    int insertIgnoreBatch(@Param("records") List<ReviewRecord> records);

    /** 导入 replace 模式用：物理清空 */
    int deleteAll();
}
