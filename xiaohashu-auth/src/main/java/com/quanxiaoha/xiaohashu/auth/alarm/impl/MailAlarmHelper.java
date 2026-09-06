package com.quanxiaoha.xiaohashu.auth.alarm.impl;

import com.quanxiaoha.xiaohashu.auth.alarm.AlarmInterface;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailAlarmHelper implements AlarmInterface {

    /**
     * 邮件发送告警信息
     *
     * @param message 告警信息内容，类型为String
     * @return 返回boolean类型，表示发送是否成功
     */
    @Override
    public boolean send(String message) {
        //在 SLF4J日志框架中，{} 是一个占位符，运行时会被 message 变量的值自动替换。
        log.info("==> 【邮件告警】：{}", message);

        // 业务逻辑...

        return true;
    }
}

