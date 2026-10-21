package com.baimo.dsalogserver.common;

/**
 * 业务异常。由 GlobalExceptionHandler 统一转成 Result。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static BizException notFound(String what) {
        // 中文不空格：消息直接给用户看，拼接处留空格会变成「题单 不存在」
        return new BizException(ErrorCode.NOT_FOUND, what + "不存在");
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    public static BizException invalid(String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, message);
    }

    /** 单次操作的数据量超限，用 413 与「参数格式不对」的 400 区分开 */
    public static BizException payloadTooLarge(String message) {
        return new BizException(ErrorCode.PAYLOAD_TOO_LARGE, message);
    }
}
