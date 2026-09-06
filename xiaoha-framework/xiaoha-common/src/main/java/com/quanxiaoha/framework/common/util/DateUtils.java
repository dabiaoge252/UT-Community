package com.quanxiaoha.framework.common.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2025-08-14 16:27
 * @description: 日期工具类
 **/
public class DateUtils {

    /**
     * LocalDateTime 转时间戳
     *
     * @param localDateTime
     * @return
     */
    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}

