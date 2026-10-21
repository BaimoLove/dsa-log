package com.baimo.dsalogserver.common;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * traceId 读写。放在 MDC 里，日志格式与返回体共用同一份。
 */
public final class TraceId {

    public static final String KEY = "traceId";

    private TraceId() {
    }

    public static String current() {
        String id = MDC.get(KEY);
        return id == null ? "" : id;
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
