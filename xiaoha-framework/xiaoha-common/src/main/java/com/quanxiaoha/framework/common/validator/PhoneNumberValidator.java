package com.quanxiaoha.framework.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * @author: 犬小哈
 * @date: 2024/4/15 22:23
 * @version: v1.0.0
 * @description: TODO
 * phoneNumber：需要验证的字符串，即被注解的属性值。
 * context：提供了一些校验的上下文信息，通常用来设置错误消息等。
 **/
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    @Override
    public void initialize(PhoneNumber constraintAnnotation) {
        // initialize 方法是用来执行初始化操作的。这个方法在校验器实例化后会被调用，通常用来读取注解中的参数来设置校验器的初始状态。
    }

    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext context) {
        // 校验逻辑：正则表达式判断手机号是否为 11 位数字
        return phoneNumber != null && phoneNumber.matches("\\d{11}");
    }
}

