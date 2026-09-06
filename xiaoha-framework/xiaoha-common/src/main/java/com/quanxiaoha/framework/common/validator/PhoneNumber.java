package com.quanxiaoha.framework.common.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * @author: 犬小哈
 * @date: 2024/4/15 22:22
 * @version: v1.0.0
 * @description: 自定义手机号校验注解
 **/
//@Target 注解用于指定自定义注解可以应用的 Java 元素类型。
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER })
//@Retention 注解用于指定自定义注解的保留策略。RetentionPolicy.RUNTIME 表示该注解在运行时仍然可用（可以通过反射机制访问）。
@Retention(RetentionPolicy.RUNTIME)
//@Constraint 注解用于指定关联的验证器类。
//在 @PhoneNumber 中，validatedBy 属性指向 PhoneNumberValidator.class，即自定义注解 @PhoneNumber 使用 PhoneNumberValidator 类进行校验。
@Constraint(validatedBy = PhoneNumberValidator.class)
public @interface PhoneNumber {

    String message() default "手机号格式不正确, 需为 11 位数字";
    //指定该校验规则属于哪个校验组
    Class<?>[] groups() default {};
    //携带校验失败的元数据信息（负载），通常用于客户端处理。定义错误等级
    Class<? extends Payload>[] payload() default {};
}

