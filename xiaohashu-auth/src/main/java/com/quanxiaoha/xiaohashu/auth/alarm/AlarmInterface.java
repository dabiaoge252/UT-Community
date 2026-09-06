package com.quanxiaoha.xiaohashu.auth.alarm;

/**
 * 告警接口，定义了发送告警信息的方法
 * 实现此接口的类需要提供具体的告警发送逻辑
 */
public interface AlarmInterface {

    /**
     * 发送告警信息
     *
     * @param message 要发送的告警信息内容
     * @return 发送成功返回true，发送失败返回false
     */
    boolean send(String message);
}

