package com.quanxiaoha.xiaohashu.auth.sms;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: 犬小哈
 * @Date: 2025/10/12 17:29
 * @Version: v1.0.0
 * @Description: 短信发送客户端
 **/
@Configuration
@Slf4j
public class AliyunSmsClientConfig {

    @Resource
    private AliyunAccessKeyProperties aliyunAccessKeyProperties;

    @Bean
    public com.aliyun.dypnsapi20170525.Client smsClient() {
        try {
            com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();

            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                    .setCredential(credential);

            // Endpoint 请参考 https://api.aliyun.com/product/Dypnsapi
            config.endpoint = "dypnsapi.aliyuncs.com";

            config.accessKeyId = aliyunAccessKeyProperties.getAccessKeyId(); // 必填
            config.accessKeySecret = aliyunAccessKeyProperties.getAccessKeySecret(); // 必填

            return new com.aliyun.dypnsapi20170525.Client(config);
        } catch (Exception e) {
            log.error("初始化阿里云短信发送客户端错误: ", e);
            return null;
        }
    }
}

