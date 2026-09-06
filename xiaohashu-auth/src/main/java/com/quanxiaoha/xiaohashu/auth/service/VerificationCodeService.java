package com.quanxiaoha.xiaohashu.auth.service;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.auth.model.vo.verificationcode.SendVerificationCodeReqVO;

/**
 * 验证码服务接口
 * 该接口定义了验证码相关操作的规范，包括发送验证码等功能
 */
public interface VerificationCodeService {

    /**
     * 发送短信验证码方法
     * 该方法用于向用户发送短信验证码，通常用于用户注册、登录或密码重置等场景

     *
     * @param sendVerificationCodeReqVO 发送验证码请求的参数对象，包含接收手机号等信息
     * @return 返回一个响应对象，包含操作是否成功以及相关信息
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}

