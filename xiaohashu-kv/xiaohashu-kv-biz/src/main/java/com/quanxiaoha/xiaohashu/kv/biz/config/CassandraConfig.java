package com.quanxiaoha.xiaohashu.kv.biz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;

/**
 * @author: 犬小哈
 * @date: 2024/7/14 16:03
 * @version: v1.0.0
 * @description: Cassandra 配置类
 **/
@Configuration
public class CassandraConfig extends AbstractCassandraConfiguration {
//AbstractCassandraConfiguration 是 Spring Data Cassandra 提供的一个抽象基类，它包含了一些默认的方法实现，用于配置 Cassandra 连接。
    @Value("${spring.cassandra.keyspace-name}")
    private String keySpace;

    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.cassandra.port}")
    private int port;

    /*
     * Provide a keyspace name to the configuration.
     */
    @Override
    public String getKeyspaceName() {
        return keySpace;
    }

    @Override
    public String getContactPoints() {
        return contactPoints;
    }

    @Override
    public int getPort() {
        return port;
    }
}
