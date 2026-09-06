package com.quanxiaoha.xiaohashu.count.biz;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: 犬小哈
 * @date: 2026/8/11
 * @version: v1.0.0
 * @description: 造数测试：注册 100 个账号，全部关注 userId=5 并点赞指定笔记，userId=5 随机关注其中 20 人
 * <p>
 * 使用方式：先启动全部服务（Nacos / Redis / RocketMQ / user / note / relation / count），再直接运行本测试类。
 * <p>
 * 说明：本测试绕过网关，直接调用各服务端口，并通过请求头「userId」模拟登录用户
 * （对应 HeaderUserId2ContextFilter 读取 userId 请求头写入 ThreadLocal 的机制），因此无需真实登录获取 Token。
 **/
public class SeedDataTests {

    private static final Logger log = LoggerFactory.getLogger(SeedDataTests.class);

    // ============================== 可配置参数 ==============================
    // 各服务直连地址（服务端口见各自 application.yml）
    private static final String USER_SERVICE_URL = "http://localhost:8082";      // 用户服务
    private static final String NOTE_SERVICE_URL = "http://localhost:8086";      // 笔记服务
    private static final String RELATION_SERVICE_URL = "http://localhost:8089";  // 用户关系服务

    // 目标数据
    private static final long TARGET_USER_ID = 5L;                       // 被 100 个账号关注的用户
    private static final long TARGET_NOTE_ID = 2084842319915253837L;     // 被 100 个账号点赞的笔记
    private static final int REGISTER_COUNT = 100;                       // 注册账号数量
    private static final int USER5_FOLLOW_COUNT = 20;                    // userId=5 随机关注的人数
    private static final int THREAD_POOL_SIZE = 16;                      // 点赞/关注并发线程数
    // =======================================================================

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 匹配响应 JSON 中的 data 字段（注册接口返回的 userId）
    private static final Pattern DATA_PATTERN = Pattern.compile("\"data\"\\s*:\\s*(\\d+)");

    /**
     * 造数主流程
     */
    @Test
    void seedData() throws Exception {
        // 1. 注册 100 个账号，拿到 100 个 userId
        List<Long> userIds = registerUsers(REGISTER_COUNT);
        log.info("==> 注册完成，共 {} 个账号, userIds: {}", userIds.size(), userIds);

        // 2. 100 个账号全部点赞笔记 + 全部关注 userId=5（并发执行）
        likeAndFollow(userIds);

        // 3. userId=5 随机关注其中 20 人
        followByUser5(userIds);

        log.info("==> 造数完成！可去 count 服务日志观察 MQ 消费、Redis 计数变化");
    }

    /**
     * 注册账号
     *
     * @param count 注册数量
     * @return 注册成功的 userId 集合
     */
    private List<Long> registerUsers(int count) throws IOException, InterruptedException {
        List<Long> userIds = new ArrayList<>();
        Set<String> phoneSet = new HashSet<>(); // 保证手机号不重复
        Random random = new Random();

        int successCount = 0;
        while (successCount < count) {
            // 生成 11 位手机号：139 + 8 位随机数字
            String phone = "139" + String.format("%08d", random.nextInt(100000000));
            if (!phoneSet.add(phone)) {
                continue; // 手机号已生成过，重新生成
            }

            // 调用用户服务注册接口
            String response = post(USER_SERVICE_URL + "/user/register", null,
                    "{\"phone\":\"" + phone + "\"}");
            Long userId = parseData(response);

            if (userId != null) {
                userIds.add(userId);
                successCount++;
                log.info("==> 注册成功, phone: {}, userId: {}", phone, userId);
            } else {
                log.error("==> 注册失败, phone: {}, response: {}", phone, response);
            }
        }
        return userIds;
    }

    /**
     * 100 个账号并发执行：点赞笔记 + 关注 userId=5
     */
    private void likeAndFollow(List<Long> userIds) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(userIds.size());
        AtomicInteger likeSuccess = new AtomicInteger();
        AtomicInteger followSuccess = new AtomicInteger();

        for (Long userId : userIds) {
            executor.submit(() -> {
                try {
                    // 点赞笔记
                    String likeResp = post(NOTE_SERVICE_URL + "/note/like", String.valueOf(userId),
                            "{\"id\":" + TARGET_NOTE_ID + "}");
                    if (likeResp.contains("\"success\":true")) {
                        likeSuccess.incrementAndGet();
                    } else {
                        log.error("==> 用户 {} 点赞失败, response: {}", userId, likeResp);
                    }

                    // 关注 userId=5
                    String followResp = post(RELATION_SERVICE_URL + "/relation/follow", String.valueOf(userId),
                            "{\"followUserId\":" + TARGET_USER_ID + "}");
                    if (followResp.contains("\"success\":true")) {
                        followSuccess.incrementAndGet();
                    } else {
                        log.error("==> 用户 {} 关注 {} 失败, response: {}", userId, TARGET_USER_ID, followResp);
                    }
                } catch (Exception e) {
                    log.error("==> 用户 {} 点赞/关注异常", userId, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.MINUTES);
        executor.shutdown();
        log.info("==> 点赞成功 {} 个，关注成功 {} 个", likeSuccess.get(), followSuccess.get());
    }

    /**
     * userId=5 随机关注其中 20 人
     */
    private void followByUser5(List<Long> userIds) throws IOException, InterruptedException {
        // 打乱顺序，取前 N 个作为随机关注对象
        List<Long> shuffled = new ArrayList<>(userIds);
        Collections.shuffle(shuffled);
        List<Long> targets = shuffled.subList(0, Math.min(USER5_FOLLOW_COUNT, shuffled.size()));

        int successCount = 0;
        for (Long targetUserId : targets) {
            String response = post(RELATION_SERVICE_URL + "/relation/follow", String.valueOf(TARGET_USER_ID),
                    "{\"followUserId\":" + targetUserId + "}");
            if (response.contains("\"success\":true")) {
                successCount++;
                log.info("==> userId=5 关注用户 {} 成功", targetUserId);
            } else {
                log.error("==> userId=5 关注用户 {} 失败, response: {}", targetUserId, response);
            }
        }
        log.info("==> userId=5 共关注 {} 人", successCount);
    }

    /**
     * 发送 POST 请求（JSON 请求体），可选携带 userId 请求头模拟登录用户
     */
    private String post(String url, String userIdHeader, String jsonBody) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (userIdHeader != null) {
            builder.header("userId", userIdHeader);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * 从响应 JSON 中解析 data 字段（数字类型），解析失败返回 null
     */
    private Long parseData(String response) {
        Matcher matcher = DATA_PATTERN.matcher(response);
        if (matcher.find()) {
            return Long.valueOf(matcher.group(1));
        }
        return null;
    }
}
