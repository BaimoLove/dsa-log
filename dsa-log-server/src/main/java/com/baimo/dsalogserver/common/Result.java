package com.baimo.dsalogserver.common;

/**
 * 统一返回体：成功一律 code=SUCCESS，HTTP 状态码 200（不用 201/204，避免前端双分支）。
 *
 * <p>code 全站统一 UPPER_SNAKE：成功固定 {@link #SUCCESS_CODE}，失败取 {@link ErrorCode#name()}。
 * 不把失败码转成小写驼峰，是因为这样会多一层「枚举名 → 协议值」的映射要维护，
 * 而枚举名直接作协议值时日志、代码、前端三处的字符串天然一致，排查时不用查表。
 */
public record Result<T>(String code, String message, T data, String traceId) {

    /** 成功码。唯一真源，任何地方不要硬编码 "SUCCESS"。 */
    public static final String SUCCESS_CODE = "SUCCESS";

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "ok", data, TraceId.current());
    }

    public static Result<Void> success() {
        return new Result<>(SUCCESS_CODE, "ok", null, TraceId.current());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        String msg = (message == null || message.isBlank()) ? errorCode.defaultMessage() : message;
        return new Result<>(errorCode.name(), msg, null, TraceId.current());
    }
}
