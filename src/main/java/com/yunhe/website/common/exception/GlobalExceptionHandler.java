package com.yunhe.website.common.exception;

import com.yunhe.website.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * 全局异常处理器。
 * <p>页面请求（Accept: text/html）返回错误页；其余请求返回统一 JSON 结构
 * （为将来 REST 接口预留）。</p>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常: {}", ex.getMessage());
        if (isHtmlRequest(request)) {
            return errorView(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return ResponseEntity.status(resolveStatus(ex.getCode()))
                .body(Result.failure(ex.getCode(), ex.getMessage()));
    }

    /** 参数校验失败（@Valid，含 @RequestBody 的 MethodArgumentNotValidException） */
    @ExceptionHandler(BindException.class)
    public Object handleValidation(BindException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "请求参数校验失败";
        }
        if (isHtmlRequest(request)) {
            return errorView(HttpStatus.BAD_REQUEST, message);
        }
        return ResponseEntity.badRequest().body(Result.failure(400, message));
    }

    /** 无权限访问 */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        if (isHtmlRequest(request)) {
            return errorView("error/403", HttpStatus.FORBIDDEN, "您没有权限执行此操作");
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.failure(403, "无权限访问"));
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception ex, HttpServletRequest request) {
        log.error("系统异常", ex);
        if (isHtmlRequest(request)) {
            return errorView(HttpStatus.INTERNAL_SERVER_ERROR, "系统开小差了，请稍后重试");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.failure(500, "系统内部错误"));
    }

    /** 业务码 → HTTP 状态：业务码本身是合法 HTTP 状态（如 404）时沿用，否则按 400 处理 */
    private HttpStatus resolveStatus(int code) {
        HttpStatus status = HttpStatus.resolve(code);
        return status != null ? status : HttpStatus.BAD_REQUEST;
    }

    private boolean isHtmlRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private ModelAndView errorView(HttpStatus status, String message) {
        return errorView("error/error", status, message);
    }

    private ModelAndView errorView(String viewName, HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView(viewName);
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("message", message);
        return mav;
    }
}
