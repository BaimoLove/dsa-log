package com.baimo.dsalogserver.common;

/**
 * 统一返回体：成功一律 code=OK，HTTP 状态码 200（不用 201/204，避免前端双分支）。
 */
public record Result<T>(String code, String message, T data, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", "ok", data, TraceId.current());
    }

    public static Result<Void> ok() {
        return new Result<>("OK", "ok", null, TraceId.current());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        String msg = (message == null || message.isBlank()) ? errorCode.defaultMessage() : message;
        return new Result<>(errorCode.name(), msg, null, TraceId.current());
    }
}
