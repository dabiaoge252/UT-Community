package com.quanxiaoha.xiaohashu.kv.biz.exception;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;

import com.quanxiaoha.xiaohashu.kv.biz.enums.ResponseCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;

/**
 * 全局异常处理器
 * 使用@ControllerAdvice注解标记此类为全局异常处理类
 * 使用@Slf4j注解提供日志支持
 */
@ControllerAdvice//Spring Boot 中使用 @ControllerAdvice 注解，来定义全局异常捕获器
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获自定义业务异常

     * @ExceptionHandler注解指定处理的异常类型为BizException
     * @ResponseBody注解使方法返回值直接作为响应体
     * @param request HttpServletRequest对象，包含请求信息
     * @param e 业务异常对象
     * @return 返回统一的响应结果对象
     */
    @ExceptionHandler({ BizException.class })
    @ResponseBody
    public Response<Object> handleBizException(HttpServletRequest request, BizException e) {
        // 记录警告日志，包含请求URI、错误码和错误信息
        log.warn("{} request fail, errorCode: {}, errorMessage: {}", request.getRequestURI(), e.getErrorCode(), e.getErrorMessage());
        // 返回失败响应，封装业务异常信息
        return Response.fail(e);
    }

    /**
     * 捕获参数校验异常

     * 处理@Valid注解校验失败时的异常
     * @param request HttpServletRequest对象，包含请求信息
     * @param e 方法参数校验异常对象
     * @return 返回统一的响应结果对象，包含参数校验错误信息
     */
    @ExceptionHandler({ MethodArgumentNotValidException.class })
    @ResponseBody
    public Response<Object> handleMethodArgumentNotValidException(HttpServletRequest request, MethodArgumentNotValidException e) {
        // 参数错误异常码
        String errorCode = ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode();

        // 获取 BindingResult
        BindingResult bindingResult = e.getBindingResult();

        StringBuilder sb = new StringBuilder();

        // 获取校验不通过的字段，并组合错误信息，格式为： email 邮箱格式不正确, 当前值: '123124qq.com';
        Optional.ofNullable(bindingResult.getFieldErrors()).ifPresent(errors -> {
            errors.forEach(error ->
                    sb.append(error.getField())
                            .append(" ")
                            .append(error.getDefaultMessage())
                            .append(", 当前值: '")
                            .append(error.getRejectedValue())
                            .append("'; ")

            );
        });

        // 错误信息
        String errorMessage = sb.toString();

        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);

        return Response.fail(errorCode, errorMessage);
    }
    /**
     * 捕获 guava 参数校验异常
     * @return
     */
    @ExceptionHandler({ IllegalArgumentException.class })
    @ResponseBody
    public Response<Object> handleIllegalArgumentException(HttpServletRequest request, IllegalArgumentException e) {
        // 参数错误异常码
        String errorCode = ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode();

        // 错误信息
        String errorMessage = e.getMessage();

        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);

        return Response.fail(errorCode, errorMessage);
    }

    /**
     * 其他类型异常
     * @param request
     * @param e
     * @return
     */
    @ExceptionHandler({ Exception.class })
    @ResponseBody
    public Response<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error, ", request.getRequestURI(), e);
        return Response.fail(ResponseCodeEnum.SYSTEM_ERROR);
    }
}

