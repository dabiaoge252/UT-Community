package com.quanxiaoha.framework.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.junit.platform.commons.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2026-08-14 16:27
 * @description: JSON 工具类
 * 该工具类提供了对象与JSON字符串之间的转换功能，使用Jackson库实现。
 * 包含了针对Java 8时间类型的特殊处理，以及防止序列化和反序列化时的一些常见问题。
 **/
public class JsonUtils {

    // 使用静态ObjectMapper实例，确保整个应用中共享同一个实例，提高性能
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 静态初始化块，在类加载时执行，用于配置ObjectMapper
    static {
        // 配置反序列化时遇到未知属性不报错
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 配置序列化时遇到空对象不报错
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        OBJECT_MAPPER.registerModules(new JavaTimeModule()); // 解决 LocalDateTime 的序列化问题
    }

    /**
     * 初始化：统一使用 Spring Boot 个性化配置的 ObjectMapper
     *
     * @param objectMapper
     */
    public static void init(ObjectMapper objectMapper) {
        OBJECT_MAPPER = objectMapper;
    }

    /**
     *  将对象转换为 JSON 字符串
     * @param obj
     * @return
     */
    @SneakyThrows
    public static String toJsonString(Object obj) {
        return OBJECT_MAPPER.writeValueAsString(obj);
    }

    /**
     * 将 JSON 字符串转换为对象
     *
     * @param jsonStr
     * @param clazz
     * @return
     * @param <T>
     */
    @SneakyThrows
    public static <T> T parseObject(String jsonStr, Class<T> clazz) {
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }

        return OBJECT_MAPPER.readValue(jsonStr, clazz);
    }
    /**
     * 将 JSON 字符串转换为 Map
     * @param jsonStr
     * @param keyClass
     * @param valueClass
     * @return
     * @param <K>
     * @param <V>
     * @throws Exception
     */
    public static <K, V> Map<K, V> parseMap(String jsonStr, Class<K> keyClass, Class<V> valueClass) throws Exception {
        // 创建 TypeReference，指定泛型类型
        TypeReference<Map<K, V>> typeRef = new TypeReference<Map<K, V>>() {
        };

        // 将 JSON 字符串转换为 Map
        return OBJECT_MAPPER.readValue(jsonStr, OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
    }
    /**
     * 将 JSON 字符串解析为指定类型的 List 对象
     *
     * @param jsonStr
     * @param clazz
     * @return
     * @param <T>
     * @throws Exception
     */
    public static <T> List<T> parseList(String jsonStr, Class<T> clazz) throws Exception {
        // 使用 TypeReference 指定 List<T> 的泛型类型
        return OBJECT_MAPPER.readValue(jsonStr, new TypeReference<List<T>>() {
            @Override
            public CollectionType getType() {
                return OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz);
            }
        });
    }

}
