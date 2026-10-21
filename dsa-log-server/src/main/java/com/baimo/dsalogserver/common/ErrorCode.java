package com.baimo.dsalogserver.common;

/**
 * 业务错误码。code 直接作为返回体 code，httpStatus 决定 HTTP 状态码。
 */
public enum ErrorCode {

    VALIDATION_FAILED(400, "参数校验失败"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "状态冲突"),
    UNDO_EXPIRED(409, "撤销已过期"),
    UNDO_ALREADY_DONE(409, "该操作已撤销过"),
    PAYLOAD_TOO_LARGE(413, "数据体积超限"),
    IMPORT_INVALID(422, "导入数据不合法"),
    INTERNAL_ERROR(500, "服务内部错误");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
