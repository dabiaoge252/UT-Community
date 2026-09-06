package com.quanxiaoha.framework.biz.context.interceptor;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.constant.GlobalConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * @author: 犬小哈
 * @date: 2024/4/14 17:48
 * @version: v1.0.0
 * @description: Feign 请求拦截器
 **/
@Slf4j
public class FeignRequestInterceptor implements RequestInterceptor {

    /**
     * 实现 RequestInterceptor 接口的 apply 方法，用于在 Feign 请求发送前进行拦截处理
     * @param requestTemplate Feign 请求模板，可以修改请求头、请求体等
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        // 获取当前上下文中的用户 ID
        Long userId = LoginUserContextHolder.getUserId();//静态方法不需要 new、也不需要 Spring 注入
//
        // 若不为空，则添加到请求头中
        if (Objects.nonNull(userId)) {
            requestTemplate.header(GlobalConstants.USER_ID, String.valueOf(userId));
            log.info("########## feign 请求设置请求头 userId: {}", userId);
        }
    }
}

