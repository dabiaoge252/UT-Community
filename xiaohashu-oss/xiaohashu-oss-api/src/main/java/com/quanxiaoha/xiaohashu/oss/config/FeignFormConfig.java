package com.quanxiaoha.xiaohashu.oss.config;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: 犬小哈
 * @date: 2024/4/14 13:57
 * @version: v1.0.0
 * @description: TODO
 **/
@Configuration
public class FeignFormConfig {

    @Bean
    public Encoder feignFormEncoder() {
//SpringFormEncoder 是 Feign 提供的一个编码器，用于处理表单提交。
//它将对象编码为表单数据格式（如 application/x-www-form-urlencoded 或 multipart/form-data），以便在 HTTP 请求中使用。
        return new SpringFormEncoder();
    }
}

