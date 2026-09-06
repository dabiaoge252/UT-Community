package com.quanxiaoha.xiaohashu.gateway.auth;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: 犬小哈
 * @date: 2025/6/13 14:48
 * @version: v1.0.0
 * @description: [Sa-Token 权限认证] 配置类
 **/
@Configuration
public class SaTokenConfigure {
    // 注册 Sa-Token全局过滤器，用于处理所有请求的认证和授权
    @Bean // 将此方法的返回对象注册为Spring容器中的一个bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址，配置需要拦截的请求路径
                .addInclude("/**")    /* 拦截全部path */  // 设置拦截所有路径，/**表示匹配所有路径
                // 鉴权方法：每次访问进入，用于处理认证逻辑
                .setAuth(obj -> {
                    // 登录校验，配置路由匹配和登录验证规则
                    SaRouter.match("/**") // 拦截所有路由
                            .notMatch("/auth/login") // 排除登录接口，不进行登录验证
                            .notMatch("/auth/verification/code/send") // 排除验证码发送接口，不进行登录验证
                            .check(r -> StpUtil.checkLogin()) // 校验是否登录，未登录则抛出异常
                    ;

                    // 权限认证 -- 不同模块, 校验不同权限
                    //SaRouter.match("/auth/user/logout", r -> StpUtil.checkPermission("user"));
//                    SaRouter.match("/auth/user/logout", r -> StpUtil.checkRole("admin"));
//                    SaRouter.match("/auth/user/logout", r -> StpUtil.checkPermission("app:note:delete"));
                      SaRouter.match("/auth/logout", r -> StpUtil.checkPermission("app:note:publish"));

                    // SaRouter.match("/goods/**", r -> StpUtil.checkPermission("goods"));
                    // SaRouter.match("/orders/**", r -> StpUtil.checkPermission("orders"));

                    // 更多匹配 ...  */
                })
                // 异常处理方法：每次setAuth函数出现异常时进入
                .setError(e -> {
                    // return SaResult.error(e.getMessage());
                    // 手动抛出异常，抛给全局异常处理器
                    if (e instanceof NotLoginException) { // 未登录异常
                        throw new NotLoginException(e.getMessage(), null, null);
                    } else if (e instanceof NotPermissionException || e instanceof NotRoleException) { // 权限不足，或不具备角色，统一抛出权限不足异常
                        throw new NotPermissionException(e.getMessage());
                    } else { // 其他异常，则抛出一个运行时异常
                        throw new RuntimeException(e.getMessage());
                    }
                })
                ;
    }
}

