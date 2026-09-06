package com.quanxiaoha.xiaohashu.auth.alarm;

import com.quanxiaoha.xiaohashu.auth.alarm.impl.MailAlarmHelper;
import com.quanxiaoha.xiaohashu.auth.alarm.impl.SmsAlarmHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@RefreshScope 注解是 Spring Cloud 提供的一个注解，
//用于实现配置动态刷新功能。当配置中心的配置发生变化时，标注了 @RefreshScope 的 Bean 会重新加载最新的配置，而无需重启应用。
@RefreshScope

public class AlarmConfig {

    @Value("${alarm.type}")
    private String alarmType;

    @Bean
    @RefreshScope
    public AlarmInterface alarmHelper() {
        // 根据配置文件中的告警类型，初始化选择不同的告警实现类
        if (StringUtils.equals("sms", alarmType)) {
            return new SmsAlarmHelper();
        } else if (StringUtils.equals("mail", alarmType)) {
            return new MailAlarmHelper();
        } else {
            throw new IllegalArgumentException("错误的告警类型...");
        }
    }
}

