package com.quanxiaoha.xiaohashu.user.biz.runner;


import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.quanxiaoha.framework.common.util.JsonUtils;

import com.quanxiaoha.xiaohashu.user.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.PermissionDO;
import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.RoleDO;
import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.RolePermissionDO;
import com.quanxiaoha.xiaohashu.user.biz.domain.mapper.PermissionDOMapper;
import com.quanxiaoha.xiaohashu.user.biz.domain.mapper.RoleDOMapper;
import com.quanxiaoha.xiaohashu.user.biz.domain.mapper.RolePermissionDOMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author: 犬小哈
 * @date: 2024/6/4 16:41
 * @version: v1.0.0
 * @description: 推送角色权限数据到 Redis 中
 **/
@Component
@Slf4j
@Transactional
public class PushRolePermissions2RedisRunner implements ApplicationRunner {

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private RoleDOMapper roleDOMapper;
    @Resource
    private PermissionDOMapper permissionDOMapper;
    @Resource
    private RolePermissionDOMapper rolePermissionDOMapper;

    // 权限同步标记 Key
    private static final String PUSH_PERMISSION_FLAG = "push.permission.flag";

    /**
 * 在应用启动时执行的角色权限数据同步到Redis的任务
 * @param args 应用启动参数
 */
    @Override
    public void run(ApplicationArguments args) {
    // 记录服务启动开始同步角色权限数据的日志
        log.info("==> 服务启动，开始同步角色权限数据到 Redis 中...");

        try {
            //利用原子锁避免频繁同步数据
            // 是否能够同步数据: 原子操作，只有在键 PUSH_PERMISSION_FLAG 不存在时，才会设置该键的值为 "1"，并设置过期时间为 1 天
            boolean canPushed = redisTemplate.opsForValue().setIfAbsent(PUSH_PERMISSION_FLAG, "1", 1, TimeUnit.DAYS);

            // 如果无法同步权限数据
            if (!canPushed) {
                log.warn("==> 角色权限数据已经同步至 Redis 中，不再同步...");
                return;
            }

            // 查询出所有角色
            List<RoleDO> roleDOS = roleDOMapper.selectEnabledList();

            if (CollUtil.isNotEmpty(roleDOS)) {
                // 拿到所有角色的 ID
                List<Long> roleIds = roleDOS.stream().map(RoleDO::getId).toList();

                // 根据角色 ID, 批量查询出所有角色对应的权限
                List<RolePermissionDO> rolePermissionDOS = rolePermissionDOMapper.selectByRoleIds(roleIds);
                // 按角色 ID 分组, 每个角色 ID 对应多个权限 ID
//                .collect(...)：终结操作，将 Stream 中的元素收集成一个新的数据结构（这里是 Map）
                //Collectors.groupingBy:第一个参数：分类函数:相同的RoleId 会被分到同一组
                //第二个参数：下游收集器（Downstream Collector）决定了每组内的元素如何处理
                //下游收集器，作用于 groupingBy 分好组的每个组内元素
                //Collectors.toList() 表示将映射后的结果收集到一个 List 中
                Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOS.stream().collect(
                        Collectors.groupingBy(RolePermissionDO::getRoleId,
                                Collectors.mapping(RolePermissionDO::getPermissionId, Collectors.toList()))
                );

                // 查询 APP 端所有被启用的权限
                List<PermissionDO> permissionDOS = permissionDOMapper.selectAppEnabledList();
                // 权限 ID - 权限 DO
                //permissionDO -> permissionDO:输入输出都是permissionDO。 Lambda要有输入输出，不能只写permissionDO
                Map<Long, PermissionDO> permissionIdDOMap = permissionDOS.stream().collect(
                        Collectors.toMap(PermissionDO::getId, permissionDO -> permissionDO)
                );

                // 组织 角色key-权限 关系:common_user:发表评论，发表帖子
                //Maps.newHashMap() 本质上就是 new HashMap<>()
                Map<String, List<String>> roleIdPermissionDOMap = Maps.newHashMap();

                // 循环所有角色
                roleDOS.forEach(roleDO -> {
                    // 当前角色 ID
                    Long roleId = roleDO.getId();
                    String roleKey = roleDO.getRoleKey();
                    // 当前角色 ID 对应的权限 ID 集合
                    List<Long> permissionIds = roleIdPermissionIdsMap.get(roleId);
                    if (CollUtil.isNotEmpty(permissionIds)) {
                        List<String> permissionKeys = Lists.newArrayList();
                        permissionIds.forEach(permissionId -> {
                            // 根据权限 ID 获取具体的权限 DO 对象
                            PermissionDO permissionDO = permissionIdDOMap.get(permissionId);
                            permissionKeys.add(permissionDO.getPermissionKey());

                        });
                        roleIdPermissionDOMap.put(roleKey, permissionKeys);
                    }
                });

                // 同步至 Redis 中，方便后续网关查询鉴权使用
                roleIdPermissionDOMap.forEach((roleKey, permissions) -> {
                    String key = RedisKeyConstants.buildRolePermissionsKey(roleKey);
                    redisTemplate.opsForValue().set(key, JsonUtils.toJsonString(permissions));
                });
            }

            log.info("==> 服务启动，成功同步角色权限数据到 Redis 中...");
        } catch (Exception e) {
            log.error("==> 同步角色权限数据到 Redis 中失败: ", e);
        }

    }
}

