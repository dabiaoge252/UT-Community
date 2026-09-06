package com.quanxiaoha.xiaohashu.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Spring Boot应用程序的主启动类
 * 使用@SpringBootApplication注解标记这是一个Spring Boot应用程序
 * 使用@MapperScan注解指定MyBatis Mapper接口的扫描路径，用于自动注册Mapper接口
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.quanxiaoha.xiaohashu")
public class XiaohashuAuthApplication {

    /**
     * 程序的主入口方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动Spring Boot应用程序
        SpringApplication.run(XiaohashuAuthApplication.class, args);
    }

}
