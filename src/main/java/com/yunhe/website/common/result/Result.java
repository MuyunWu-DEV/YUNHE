package com.yunhe.website.common.result;

import java.io.Serializable;
import lombok.Getter;

/**
 * 统一响应体：所有 REST 接口均返回该结构，保证前后端契约一致。
 *
 * @param <T> 业务数据类型
 */
@Getter
public class Result<T> implements Serializable {

    /** 业务状态码：0 表示成功，非 0 表示失败 */
    private final int code;

    /** 提示信息 */
    private final String message;

    /** 业务数据 */
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(0, message, data);
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> failure(String message) {
        return new Result<>(-1, message, null);
    }
}
