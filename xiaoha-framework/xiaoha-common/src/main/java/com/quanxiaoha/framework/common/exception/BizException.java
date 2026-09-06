package com.quanxiaoha.framework.common.exception;

import lombok.Getter;
import lombok.Setter;

/**
 * 自定义业务异常类
 * 继承自RuntimeException，用于处理业务逻辑中的异常情况
 */
@Getter
@Setter
public class BizException extends RuntimeException {
    // 异常码，用于标识具体的异常类型
    private String errorCode;
    // 错误信息，用于描述异常的具体内容
    private String errorMessage;

    /**
     * 构造方法
     * @param baseExceptionInterface 提供异常码和错误信息的接口
     * 通过该接口获取异常码和错误信息，并初始化异常对象
     */
    public BizException(BaseExceptionInterface baseExceptionInterface) {
        this.errorCode = baseExceptionInterface.getErrorCode();
        this.errorMessage = baseExceptionInterface.getErrorMessage();
    }
}

