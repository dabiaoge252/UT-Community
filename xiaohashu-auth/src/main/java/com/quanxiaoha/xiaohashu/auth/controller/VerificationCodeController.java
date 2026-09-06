package com.quanxiaoha.xiaohashu.auth.controller;

import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.quanxiaoha.xiaohashu.auth.service.VerificationCodeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


/**
 * 验证码控制器
 * 提供验证码相关的HTTP接口服务
 */
@RestController
@Slf4j
public class VerificationCodeController {

    /**
     * 注入验证码服务接口
     * 用于处理验证码相关的业务逻辑
     */
    @Resource
    private VerificationCodeService verificationCodeService;

    /**
     * 发送短信验证码接口
     * @PostMapping 指定HTTP请求方法为POST，访问路径为/verification/code/send
     * @ApiOperationLog 记录接口操作日志，描述为"发送短信验证码"
     * @param sendVerificationCodeReqVO 发送验证码的请求参数，使用@Validated进行参数校验
     * @return 返回Response对象，包含操作结果
     */
    @PostMapping("/verification/code/send")
    @ApiOperationLog(description = "发送短信验证码")
    public Response<?> send(@Validated @RequestBody SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        return verificationCodeService.send(sendVerificationCodeReqVO);
    }

}

