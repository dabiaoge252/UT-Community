package com.quanxiaoha.xiaohashu.user.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-08-15 10:33
 * @description: 性别
 **/
@Getter
@AllArgsConstructor
public enum SexEnum {

    WOMAN(0),
    MAN(1);

    private final Integer value;

/**
 * 验证给定的整数值是否是有效的性别枚举值
 * @param value 需要验证的整数值
 * @return 如果值存在于SexEnum枚举中则返回true，否则返回false
 */
    public static boolean isValid(Integer value) {
    // 遍历SexEnum枚举的所有值
        for (SexEnum loginTypeEnum : SexEnum.values()) {
        // 检查当前枚举值与给定的value是否相等
            if (Objects.equals(value, loginTypeEnum.getValue())) {
            // 如果找到匹配的枚举值，返回true
                return true;
            }
        }
    // 如果没有找到匹配的枚举值，返回false
        return false;
    }

}

