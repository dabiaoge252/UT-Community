package com.quanxiaoha.xiaohashu.auth.model.vo.user;

import com.quanxiaoha.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:17
 * @version: v1.0.0
 * @description: 用户登录（支持密码或验证码两种方式）
 * @annotation:
 *   @Data - Lombok注解，自动生成getter、setter、toString等方法
 *   @AllArgsConstructor - Lombok注解，生成全参数构造方法
 *   @NoArgsConstructor - Lombok注解，生成无参构造方法
 *   @Builder - Lombok注解，构建者模式注解，用于创建对象
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserLoginReqVO {

    /**
     * 手机号字段
     * @annotation:
     *   @NotBlank - 校验注解，要求该字段不能为空
     *   @PhoneNumber - 自定义校验注解，用于验证手机号格式
     */
    @NotBlank(message = "手机号不能为空")
    @PhoneNumber
    private String phone;

    /**
     * 验证码字段
     * 用于手机号验证码登录方式
     */
    private String code;

    /**
     * 密码字段
     * 用于账号密码登录方式
     */
    private String password;

    /**
     * 登录类型：手机号验证码，或者是账号密码
     */
    @NotNull(message = "登录类型不能为空")
    private Integer type;
}

