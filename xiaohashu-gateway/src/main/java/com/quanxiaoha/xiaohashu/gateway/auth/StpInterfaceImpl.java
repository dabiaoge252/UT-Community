package com.quanxiaoha.xiaohashu.gateway.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.gateway.constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import cn.hutool.core.collection.CollUtil;
import java.util.List;

/**
 * @author: 犬小哈
 * @date: 2025/4/5 18:04
 * @version: v1.0.0
 * @description: 自定义权限验证接口扩展
 * 该类实现了StpInterface接口，用于提供用户权限和角色的验证功能
 * 使用Redis进行数据存储，通过Jackson进行JSON序列化和反序列化
 * 包含两个主要方法：获取用户权限列表和获取用户角色列表
 **/
@Component
@Slf4j
public class StpInterfaceImpl implements StpInterface {
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    // 注入Jackson的ObjectMapper，用于JSON序列化和反序列化
    @Resource
    private ObjectMapper objectMapper;


    /**
     * 获取用户权限列表
     * 根据用户ID，从Redis中查询用户角色，再根据角色查询对应的权限
     * @param loginId 用户登录ID
     * @param loginType 登录类型
     * @return 用户权限列表，每个元素为一个权限标识
     */
    @SneakyThrows // Lombok注解，用于抛出受检异常而无需在方法签名上声明throws
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 记录日志，开始获取用户权限列表
        log.info("## 获取用户权限列表, loginId: {}", loginId);

        // 构建 用户-角色 Redis Key
        String userRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));

        // 根据 userID ，从 Redis 中获取该用户的角色集合
        String useRolesValue = redisTemplate.opsForValue().get(userRolesKey);

        // 如果角色值为空，直接返回null
        if (StringUtils.isBlank(useRolesValue)) {
            return null;
        }

        // 将 JSON 字符串转换为 List<String> 角色集合
        //.readValue(JSON 源,目标类型的引用)

/*      new TypeReference<>() {}: Jackson 提供的泛型类型捕获技巧
        因为 Java 的泛型会在运行时被擦除，List<String>.class 这种写法不合法
        TypeReference<List<String>> 通过匿名内部类的方式保留泛型信息，让 Jackson 知道要反序列化成 List<String>，而不是 List<Object>
        <> 里的泛型由编译器推断，实际上等价于new TypeReference<List<String>>() {}*/
        List<String> userRoleKeys = objectMapper.readValue(useRolesValue, new TypeReference<>() {});

        if (CollUtil.isNotEmpty(userRoleKeys)) {
        // 如果用户角色不为空，则查询这些角色对应的权限
            // 查询这些角色对应的权限
            // 构建 角色-权限 Redis Key 集合
            List<String> rolePermissionsKeys = userRoleKeys.stream()
                    .map(RedisKeyConstants::buildRolePermissionsKey)
                    .toList();

            // 通过 key 集合批量查询权限，提升查询性能。
            //.multiGet()从 Redis 中一次性获取 rolePermissionsKeys 这个集合里所有 key 的值，结果存到 rolePermissionsValues 列表中。
            List<String> rolePermissionsValues = redisTemplate.opsForValue().multiGet(rolePermissionsKeys);

            if (CollUtil.isNotEmpty(rolePermissionsValues)) {
                List<String> permissions = Lists.newArrayList();

                // 遍历所有角色的权限集合，统一添加到 permissions 集合中
                rolePermissionsValues.forEach(jsonValue -> {
                    try {
                        // 将 JSON 字符串转换为 List<String> 权限集合
                        List<String> rolePermissions = objectMapper.readValue(jsonValue, new TypeReference<>() {});
                        permissions.addAll(rolePermissions);
                    } catch (JsonProcessingException e) {
                        log.error("==> JSON 解析错误: ", e);
                    }
                });

                // 返回此用户所拥有的权限
                return permissions;
            }
        }
        return null;
    }

    /**
     * 获取用户角色
     *
     * @param loginId
     * @param loginType
     * @return RoleList
     */
//  @SneakyThrows是 Lombok 提供的一个注解，它的主要作用是在代码中“偷偷”地抛出受检异常，而无需在方法签名上显式声明 throws。
    @SneakyThrows
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        log.info("## 获取用户角色列表, loginId: {}", loginId);

        // 构建 用户-角色 Redis Key
        String userRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));

        // 根据用户 ID ，从 Redis 中获取该用户的角色集合
        String useRolesValue = redisTemplate.opsForValue().get(userRolesKey);

        if (StringUtils.isBlank(useRolesValue)) {
            return null;
        }

        // 将 JSON 字符串转换为 List<String> 集合
        return objectMapper.readValue(useRolesValue, new TypeReference<>() {});
    }

}

