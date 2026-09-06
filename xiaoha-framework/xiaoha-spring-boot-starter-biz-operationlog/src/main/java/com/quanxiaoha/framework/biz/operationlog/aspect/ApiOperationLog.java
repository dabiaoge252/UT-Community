package com.quanxiaoha.framework.biz.operationlog.aspect;

import java.lang.annotation.*;

/**
 * 自定义注解：ApiOperationLog
 * 用于标注API接口的操作日志记录
 * 该注解可以应用于方法级别，在运行时保留
 */
@Retention(RetentionPolicy.RUNTIME)  // 注解的保留策略，在运行时有效
@Target({ElementType.METHOD})      // 注解的目标，用于标注方法
@Documented                        // 表明注解会被包含在JavaDoc中
public @interface ApiOperationLog {  // 定义一个名为ApiOperationLog的注解
    /**
     * API 功能描述
     * 用于描述该API接口的主要功能
     * @return 返回API功能的描述字符串，默认值为空字符串
     */
    String description() default "";  // 定义一个名为description的属性，类型为String，默认值为空字符串

}
