package com.baimo.dsalogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baimo.dsalogserver.entity.ReviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewRecordMapper extends BaseMapper<ReviewRecord> {

    /** 批量写流水：批量已刷/取消已刷时一次落库，不逐条 insert */
    int insertBatch(@Param("records") List<ReviewRecord> records);
}
