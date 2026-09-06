package com.quanxiaoha.xiaohashu.data.align.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @author: 犬小哈
 * @description: 数据对齐表配置（支持 Nacos 动态刷新）
 * <p>
 * 为什么单独拆一个类：@RefreshScope 不能加在 XXL-Job 的 Job 类上
 * （@RefreshScope 会生成 CGLIB 代理，导致 XXL-Job 反射扫描不到 @XxlJob 注解，handler 注册失败）。
 * 所以把需要动态刷新的配置放在本类中，Job 类注入本类使用，改配置即可热生效。
 **/
@Component
@RefreshScope
@Data
public class TableConfig {

    /**
     * 日增量表分片数
     */
    @Value("${table.shards}")
    private int shards;
}
