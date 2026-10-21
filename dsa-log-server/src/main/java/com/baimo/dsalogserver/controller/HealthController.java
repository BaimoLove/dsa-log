package com.baimo.dsalogserver.controller;

import com.baimo.dsalogserver.common.Result;
import com.baimo.dsalogserver.common.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。给 nginx/定时任务用，返回 db 与 redis 的真实连通性。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;

    @Value("${app.version:0.0.1}")
    private String version;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "up");
        body.put("db", checkDb());
        body.put("redis", checkRedis());
        body.put("version", version);
        body.put("now", System.currentTimeMillis());
        body.put("zone", TimeUtil.ZONE.getId());
        return Result.ok(body);
    }

    private String checkDb() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2) ? "ok" : "down";
        } catch (Exception e) {
            return "down";
        }
    }

    private String checkRedis() {
        RedisConnectionFactory factory = redisConnectionFactory.getIfAvailable();
        if (factory == null) {
            return "absent";
        }
        try (RedisConnection conn = factory.getConnection()) {
            conn.ping();
            return "ok";
        } catch (Exception e) {
            return "down";
        }
    }
}
