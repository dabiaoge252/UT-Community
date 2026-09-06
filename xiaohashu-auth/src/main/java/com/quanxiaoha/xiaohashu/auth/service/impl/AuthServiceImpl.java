package com.quanxiaoha.xiaohashu.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.common.base.Preconditions;
import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.auth.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.auth.enums.LoginTypeEnum;
import com.quanxiaoha.xiaohashu.auth.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.auth.model.vo.user.UpdatePasswordReqVO;
import com.quanxiaoha.xiaohashu.auth.model.vo.user.UserLoginReqVO;
import com.quanxiaoha.xiaohashu.auth.rpc.UserRpcService;
import com.quanxiaoha.xiaohashu.auth.service.AuthService;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByPhoneRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: TODO
 **/
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private UserRpcService userRpcService;
    /**
     * 登录与注册方法
     * 支持验证码登录和密码登录两种方式

 *
     * @param userLoginReqVO 用户登录请求参数对象，包含手机号、登录类型等信息
     * @return 返回Response对象，包含登录结果信息
     */
    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
    // 从请求参数中获取手机号和登录类型
        String phone = userLoginReqVO.getPhone();
        Integer type = userLoginReqVO.getType();

    // 将数字类型的登录类型转换为枚举类型
        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);

        Long userId = null;

        // 判断登录类型
        switch (loginTypeEnum) {
            case VERIFICATION_CODE: // 验证码登录
                String verificationCode = userLoginReqVO.getCode();

                //利用Guava库中Preconditions下方法校验入参验证码是否为空，在条件不满足时抛出IllegalArgumentException异常
                //GlobalExceptionHandler捕获IllegalArgumentException 异常
                Preconditions.checkArgument(StringUtils.isNotBlank(verificationCode), "验证码不能为空");

                // 构建验证码 Redis Key
                String key = RedisKeyConstants.buildVerificationCodeKey(phone);
                // 查询存储在 Redis 中该用户的登录验证码
                String sentCode = (String) redisTemplate.opsForValue().get(key);

                // 判断用户提交的验证码，与 Redis 中的验证码是否一致
                if (!StringUtils.equals(verificationCode, sentCode)) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }
                // RPC: 调用用户服务，注册用户
                Long userIdTmp = userRpcService.registerUser(phone);

                // 若调用用户服务，返回的用户 ID 为空，则提示登录失败
                if (Objects.isNull(userIdTmp)) {
                    throw new BizException(ResponseCodeEnum.LOGIN_FAIL);
                }
                userId = userIdTmp;
                break;
            case PASSWORD: // 密码登录
                String password = userLoginReqVO.getPassword();
                // RPC: 调用用户服务，通过手机号查询用户
                FindUserByPhoneRspDTO findUserByPhoneRspDTO = userRpcService.findUserByPhone(phone);

                // 判断该手机号是否注册
                if (Objects.isNull(findUserByPhoneRspDTO)) {
                    throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
                }

                // 拿到密文密码
                String encodePassword = findUserByPhoneRspDTO.getPassword();

                // 匹配密码是否一致
                boolean isPasswordCorrect = passwordEncoder.matches(password, encodePassword);

                // 如果不正确，则抛出业务异常，提示用户名或者密码不正确
                if (!isPasswordCorrect) {
                    throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }

                userId = findUserByPhoneRspDTO.getId();

                break;
            default:
                break;
        }

        // SaToken 登录用户，并返回 token 令牌
        // SaToken 登录用户, 入参为用户 ID
        StpUtil.login(userId);

        // 获取 Token 令牌
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        // 返回 Token 令牌
        return Response.success(tokenInfo.tokenValue);

    }


    /**
     * 退出登录
     *
     * @return
     */
    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();

        log.info("==> 用户退出登录, userId: {}", userId);

        threadPoolTaskExecutor.submit(() -> {
            Long userId2 = LoginUserContextHolder.getUserId();
            log.info("==> 异步线程中获取 userId: {}", userId2);
        });

        // 退出登录 (指定用户 ID)
        StpUtil.logout(userId);

        return Response.success();
    }
    /**
     * 修改密码
     * 该方法用于处理用户修改密码的请求，包含密码加密和更新用户信息的功能

 *
     * @param updatePasswordReqVO 包含新密码的请求对象
     * @return 返回操作结果，成功时返回成功响应
     */
    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        // 新密码 - 从请求对象中获取用户输入的新密码
        String newPassword = updatePasswordReqVO.getNewPassword();
        // 密码加密 - 使用密码编码器对新密码进行加密处理，确保安全性
        String encodePassword = passwordEncoder.encode(newPassword);
        // RPC: 调用用户服务：更新密码
        userRpcService.updatePassword(encodePassword);

    // 返回操作成功响应
        return Response.success();
    }

}
