package com.quanxiaoha.xiaohashu.data.align.job;

import com.quanxiaoha.xiaohashu.data.align.config.TableConfig;
import com.quanxiaoha.xiaohashu.data.align.constant.TableConstants;
import com.quanxiaoha.xiaohashu.data.align.domain.mapper.CreateTableMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @author: 犬小哈
 * @date: 2024/4/6 15:51
 * @version: v1.0.0
 * @description: 定时任务：自动创建日增量计数变更表
 * <p>
 * 注意：该类不能加 @RefreshScope！@RefreshScope 会生成 CGLIB 代理，
 * 导致 XXL-Job 反射扫描不到方法上的 @XxlJob 注解，handler 注册失败（报 not found）。
 * 需要动态刷新的配置（如 table.shards）统一放在 TableConfig 中，本类注入使用。
 **/
@Component
public class CreateTableXxlJob {

    @Resource
    private TableConfig tableConfig;

    @Resource
    private CreateTableMapper createTableMapper;

    /**
     * 1、简单任务示例（Bean模式）
     */
    @XxlJob("createTableJobHandler")
    public void createTableJobHandler() throws Exception {
        // 表后缀
        String date = LocalDate.now().plusDays(1) // 明日的日期
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串

        XxlJobHelper.log("## 开始创建日增量数据表，日期: {}...", date);

        // 动态读取分片数（Nacos 改配置后自动生效）
        int tableShards = tableConfig.getShards();

        if (tableShards > 0) {
            for (int hashKey = 0; hashKey < tableShards; hashKey++) {
                // 表名后缀
                String tableNameSuffix = TableConstants.buildTableNameSuffix(date, hashKey);

                // 创建表
                createTableMapper.createDataAlignFollowingCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignFansCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNoteCollectCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignUserCollectCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignUserLikeCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNoteLikeCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNotePublishCountTempTable(tableNameSuffix);
            }
        }

        XxlJobHelper.log("## 结束创建日增量数据表，日期: {}...", date);
    }

}

