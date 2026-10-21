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
