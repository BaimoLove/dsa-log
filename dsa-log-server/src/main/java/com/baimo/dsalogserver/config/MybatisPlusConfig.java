package com.baimo.dsalogserver.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper 扫描 + MyBatis-Plus 插件链。
 *
 * <p>分页插件对本项目是安全的：多标签 AND 的 {@code GROUP BY ... HAVING COUNT(DISTINCT)}
 * 写在 {@code p.id IN (...)} 子查询内部，SQL 顶层既没有 GROUP BY 也没有 JOIN，
 * 插件优化后的 count 就是 {@code SELECT COUNT(*) FROM problem p WHERE ...}，
 * 与主查询口径天然一致，不存在 JOIN 被优化掉导致总数偏小的问题。
 *
 * <p>两个上限是刻意设的防御：{@code maxLimit=100} 与 {@code ProblemQuery.safeSize()} 的夹取一致，
 * 防止有人绕过 DTO 直接传 size；{@code overflow=false} 表示页码越界返回空集而不是跳回第一页，
 * 由前端决定是否回到首页，后端不替它做决定。
 */
@Configuration
@MapperScan("com.baimo.dsalogserver.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
