package com.baimo.dsalogserver.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper 扫描。故意不启用 MyBatis-Plus 分页插件：
 * 复杂查询带 GROUP BY/HAVING，插件生成的 COUNT 包装容易与主查询不一致，全站手写 COUNT + LIMIT。
 */
@Configuration
@MapperScan("com.baimo.dsalogserver.mapper")
public class MybatisPlusConfig {
}
