package com.yunhe.website.common.exception;

import lombok.Getter;

/**
 * 业务异常：用于表示可预期的业务错误（如参数非法、资源不存在、状态冲突等），
 * 由全局异常处理器统一转换为友好提示。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    public BusinessException(String message) {
        this(-1, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException of(String message) {
        return new BusinessException(message);
    }

    public static BusinessException notFound(String resource, Long id) {
        return new BusinessException(404, resource + " 不存在：" + id);
    }
}
