package com.baimo.dsalogserver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 题单表访问。M3.5 只用到存在性校验，完整 CRUD 随 M5.5 补齐。
 */
@Mapper
public interface ProblemListMapper {

    /** 未删的题单数：>0 表示可用。不做 selectOne，避免调用方误用已删行 */
    int countAlive(@Param("id") String id);
}
