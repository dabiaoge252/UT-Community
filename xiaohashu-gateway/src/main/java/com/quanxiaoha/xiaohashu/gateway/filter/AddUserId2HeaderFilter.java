package com.quanxiaoha.xiaohashu.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.quanxiaoha.framework.common.constant.GlobalConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author: 犬小哈
 * @date: 2024/4/9 15:52
 * @version: v1.0.0
 * @description: 转发请求时，将用户 ID 添加到 Header 请求头中，透传给下游服务
 **/
@Component
@Slf4j
@Order(-90)
//GlobalFilter : 这是一个全局过滤器接口，会对所有通过网关的请求生效。
public class AddUserId2HeaderFilter implements GlobalFilter {

/* ServerWebExchange exchange：表示当前的 HTTP 请求和响应的上下文，包括请求头、请求体、响应头、响应体等信息。可以通过它来获取和修改请求和响应。
GatewayFilterChain chain：代表网关过滤器链，通过调用 chain.filter(exchange) 方法可以将请求传递给下一个过滤器进行处理。*/
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("==================> TokenConvertFilter");

        // 用户 ID
        Long userId = null;
        try {
            // 获取当前登录用户的 ID
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            // 若没有登录，则直接放行
            return chain.filter(exchange);
        }

        log.info("## 当前登录的用户 ID: {}", userId);

        Long finalUserId = userId;
/*        mutate() = “复制当前对象的所有配置，返回一个可修改的 Builder，从中 build 出一个新对象，原对象不动”。
        本质是不可变对象的派生（copy-on-write），不是就地修改。*/
        ServerWebExchange newExchange = exchange.mutate()
                .request(builder -> builder.header(GlobalConstants.USER_ID, String.valueOf(finalUserId))) // 将用户 ID 设置到请求头中
                .build();
        // 将请求传递给过滤器链中的下一个过滤器进行处理。没有对请求进行任何修改。
        return chain.filter(newExchange);
    }
}

