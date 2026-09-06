package com.quanxiaoha.framework.biz.operationlog.config;

import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLogAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 自动配置类，用于配置API操作日志功能
 * 该类被Spring Boot的自动配置机制识别，用于注册API操作日志的切面Bean
 */
@AutoConfiguration// 自动配置类
public class ApiOperationLogAutoConfiguration {

    /**
     * 创建并注册ApiOperationLogAspect Bean
     * 该切面用于拦截和处理API操作日志的记录
     *
     * @return ApiOperationLogAspect 实例
     */
    @Bean// 声明一个Bean，Spring容器会管理这个Bean的生命周期
    public ApiOperationLogAspect apiOperationLogAspect() {
        return new ApiOperationLogAspect();
    }
}
