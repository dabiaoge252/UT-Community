package com.quanxiaoha.xiaohashu.auth.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author: 犬小哈
 * @Date: 2025/10/12 17:24
 * @Version: v1.0.0
 * @Description: 阿里云 Access Key 配置
 **/
@ConfigurationProperties(prefix = "aliyun")
@Component
@Data
public class AliyunAccessKeyProperties {
    private String accessKeyId;
    private String accessKeySecret;
}

