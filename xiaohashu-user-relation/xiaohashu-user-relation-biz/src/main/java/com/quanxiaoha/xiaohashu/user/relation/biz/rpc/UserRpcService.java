package com.quanxiaoha.xiaohashu.user.relation.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUserByIdReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * @author: 犬小哈
 * @date: 2024/4/13 23:29
 * @version: v1.0.0
 * @description: 用户服务
 **/
@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 根据用户 ID 查询用户信息的方法
     * 该方法通过调用远程服务接口来获取用户数据
     * @param userId 用户ID，用于查询指定用户的信息
     * @return FindUserByIdRspDTO 包含用户信息的响应对象，如果查询失败则返回null
     */
    public FindUserByIdRspDTO findById(Long userId) {
    // 创建查询请求对象
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
    // 设置查询条件（用户ID）
        findUserByIdReqDTO.setId(userId);
    // 调用远程服务接口查询用户信息
        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);
    // 检查响应是否成功且数据不为空
        if (!response.isSuccess() || Objects.isNull(response.getData())) {
        // 如果响应不成功或数据为空，则返回null
            return null;
        }
    // 返回查询到的用户数据
        return response.getData();
    }

    /**
     * 批量查询用户信息
     *

 * 该方法用于根据用户ID列表批量查询用户信息，通过调用Feign客户端接口实现
 *
     * @param userIds 用户ID列表，用于查询多个用户的信息
     * @return 返回用户信息列表，如果查询失败或结果为空则返回null
     */
    public List<FindUserByIdRspDTO> findByIds(List<Long> userIds) {
    // 创建查询请求对象
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO();
    // 设置要查询的用户ID列表
        findUsersByIdsReqDTO.setIds(userIds);
    // 调用Feign客户端接口查询用户信息
        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUsersByIdsReqDTO);
    // 检查响应是否成功，数据是否为空
        if (!response.isSuccess() || Objects.isNull(response.getData()) || CollUtil.isEmpty(response.getData())) {
        // 如果响应不成功或数据为空，则返回null
            return null;
        }
    // 返回查询到的用户信息列表
        return response.getData();
    }



}

