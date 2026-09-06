package com.quanxiaoha.xiaohashu.auth.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-08-15 10:33
 * @description: 登录类型
 **/
@Getter
@AllArgsConstructor
public enum LoginTypeEnum {
    // 验证码登录方式
    VERIFICATION_CODE(1),
    // 密码登录方式
    PASSWORD(2);

    private final Integer value;  // 登录类型的值，使用final修饰表示不可变

    /**
     * 根据code值获取对应的枚举实例
     * @param code 登录类型的值
     * @return 对应的LoginTypeEnum实例，如果找不到则返回null
     */
    public static LoginTypeEnum valueOf(Integer code) {
        // 遍历所有枚举值
        for (LoginTypeEnum loginTypeEnum : LoginTypeEnum.values()) {
            // 比较传入的code与枚举值的value是否相等
            if (Objects.equals(code, loginTypeEnum.getValue())) {
                return loginTypeEnum;  // 找到匹配的枚举值则返回
            }
        }
        return null;  // 没有找到匹配的枚举值则返回null
    }

}

