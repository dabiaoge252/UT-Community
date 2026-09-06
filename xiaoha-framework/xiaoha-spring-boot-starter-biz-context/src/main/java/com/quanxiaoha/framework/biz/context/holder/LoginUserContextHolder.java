package com.quanxiaoha.framework.biz.context.holder;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.quanxiaoha.framework.common.constant.GlobalConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author: 犬小哈
 * @date: 2025/4/9 18:19
 * @version: v1.0.0
 * @description: 登录用户上下文， ThreadLocal 存取器
 **/
public class LoginUserContextHolder {

    // 初始化一个 ThreadLocal 变量
    //ThreadLocal.withInitial(HashMap::new)：使用 ThreadLocal 的 withInitial 方法，
    //传入一个 HashMap 的构造方法引用，初始化每个线程的 ThreadLocal 变量时都会创建一个新的 HashMap 实例。
    //之所以用 HashMap 数据类型，是为了方便后续扩展，如果还有新的数据，只接往里面添加即可。
    //ThreadLocal 是 Java 中用于创建线程局部变量的工具，每个线程都有自己的独立变量副本，不会相互干扰。
    //TransmittableThreadLocal 是阿里巴巴开源的一个工具类，它扩展了 ThreadLocal，使得线程池中的线程也能正确地传递 ThreadLocal 变量。
    private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
            = TransmittableThreadLocal.withInitial(HashMap::new);


    /**
     * 设置用户 ID
     *
     * @param value
     */
    public static void setUserId(Object value) {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.get().put(GlobalConstants.USER_ID, value);
    }//get获取hashmap,put设置用户id
       /**
     * 获取用户 ID
     *
     * @return
     */
    public static Long getUserId() {
        Object value = LOGIN_USER_CONTEXT_THREAD_LOCAL.get().get(GlobalConstants.USER_ID);
        if (Objects.isNull(value)) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    /**
     * 删除 ThreadLocal
     */
    public static void remove() {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
    }

}
