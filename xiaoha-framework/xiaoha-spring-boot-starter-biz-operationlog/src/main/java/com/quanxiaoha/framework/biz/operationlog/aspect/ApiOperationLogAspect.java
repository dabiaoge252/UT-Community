
package com.quanxiaoha.framework.biz.operationlog.aspect;

import com.quanxiaoha.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

@Aspect// 声明这是一个切面类
@Slf4j// 日志
public class ApiOperationLogAspect {

    /** 以自定义 @ApiOperationLog 注解为切点，凡是添加 @ApiOperationLog 的方法，都会执行环绕中的代码 */
    @Pointcut("@annotation(com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog)")// 切点
    public void apiOperationLog() {}

    /**
     * 环绕通知方法，用于拦截并记录API操作日志

 * 该方法会在被注解@ApiOperationLog标记的方法执行前后执行
     * @param joinPoint 连接点，包含目标方法的信息
     * @return 目标方法的执行结果
     * @throws Throwable 目标方法执行时可能抛出的异常
     */
    @Around("apiOperationLog()")// 环绕通知，拦截所有被@ApiOperationLog注解标记的方法
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 请求开始时间
        long startTime = System.currentTimeMillis();

        // 获取被请求的类和方法
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // 请求入参
        Object[] args = joinPoint.getArgs();
        // 入参转 JSON 字符串
        String argsJsonStr = Arrays.stream(args).map(toJsonStr()).collect(Collectors.joining(", "));

        // 功能描述信息
        String description = getApiOperationLogDescription(joinPoint);

        // 打印请求相关参数
        log.info("====== 请求开始: [{}], 入参: {}, 请求类: {}, 请求方法: {} =================================== ",
                description, argsJsonStr, className, methodName);

        // 执行切点方法
        Object result = joinPoint.proceed();

        // 执行耗时
        long executionTime = System.currentTimeMillis() - startTime;

        // 打印出参等相关信息
        log.info("====== 请求结束: [{}], 耗时: {}ms, 出参: {} =================================== ",
                description, executionTime, JsonUtils.toJsonString(result));

        return result;
    }

    /**
     * 获取注解的描述信息
     * @param joinPoint 连接点，包含方法调用的各种信息
     * @return 返回注解中描述的信息内容
     */
    private String getApiOperationLogDescription(ProceedingJoinPoint joinPoint) {
        // 1. 从 ProceedingJoinPoint 获取 MethodSignature
    // 通过连接点获取方法签名，包含方法名、参数类型等信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // 2. 使用 MethodSignature 获取当前被注解的 Method
    // 从方法签名中获取方法对象，以便后续获取注解信息
        Method method = signature.getMethod();

        // 3. 从 Method 中提取 LogExecution 注解
    // 获取方法上的 ApiOperationLog 注解实例
        ApiOperationLog apiOperationLog = method.getAnnotation(ApiOperationLog.class);

        // 4. 从 ApiOperationLog 注解中获取 description 属性
    // 返回注解中定义的描述信息
        return apiOperationLog.description();
    }

    /**
     * 转 JSON 字符串
     * @return
     */
    private Function<Object, String> toJsonStr() {
        return JsonUtils::toJsonString;
    }

}
