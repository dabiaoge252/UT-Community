# RocketMQ 使用总结（零基础版，结合本项目）

> 更新时间：2026-08-05
> 适用人群：**没学过消息队列**的开发者。从"消息队列是干什么的"讲起，逐步深入到本项目代码，所有概念配大白话类比 + 逐行注释示例。

## 目录

1. [先看懂的：消息队列是干什么的](#1-先看懂的-消息队列是干什么的)
2. [RocketMQ 核心概念（大白话 + 类比）](#2-rocketmq-核心概念大白话--类比)
3. [一条消息的一生（payload / SendCallback 拆解）](#3-一条消息的一生payload--sendcallback-拆解)
4. [生产者：RocketMQTemplate 常用方法](#4-生产者rocketmqtemplate-常用方法)
5. [消费者：@RocketMQMessageListener](#5-消费者rocketmqmessagelistener)
6. [集群消费 vs 广播消费（本项目实战）](#6-集群消费-vs-广播消费本项目实战)
7. [延时消息（延迟双删用）](#7-延时消息延迟双删用)
8. [项目中的 3 个 Topic 完整案例](#8-项目中的-3-个-topic-完整案例)
9. [项目落地流程（从零到跑通）](#9-项目落地流程从零到跑通)
10. [令牌桶限流（Guava RateLimiter）](#10-令牌桶限流guava-ratelimiter)
11. [部署与排错](#11-部署与排错)

---

## 1. 先看懂的：消息队列是干什么的

**消息队列（Message Queue，MQ）** 就是一个"**中间仓库**"：A 系统把消息丢进仓库，B 系统从仓库里取。A、B 互不认识、互不等对方。

### 生活类比：快递柜

- 你（**生产者**）把包裹放进快递柜（**MQ**），不用等收件人下楼；
- 收件人（**消费者**）有空时来取件，取走即消费；
- 快递柜放在 A 和 B 之间，两人解耦。

### 为什么要用 MQ？三个核心价值

| 价值 | 解释 | 本项目例子 |
|------|------|-----------|
| **异步** | 发消息立刻返回，不用等后面耗时的处理 | 关注用户：接口先把关注关系写 Redis、发一条 MQ 就返回；落库（插关注表+粉丝表）由消费者异步慢慢做，接口响应快 |
| **解耦** | 生产者不关心谁消费、几个消费者；后续加新消费者不改生产者代码 | 笔记更新后广播一条"删缓存"消息，所有实例各删各的 |
| **削峰** | 瞬间消息太多时，消费者按自己的速率慢慢处理，避免打爆数据库 | `FollowUnfollowConsumer` 用令牌桶控制每秒消费数量 |

---

## 2. RocketMQ 核心概念（大白话 + 类比）

RocketMQ 的消息系统由 4 个角色组成：

```
生产者 Producer ──发消息──▶ Broker（存储） ──取消息──▶ 消费者 Consumer
        ▲                                          ▲
        │              NameServer（路由表）          │
        └─────────────知道 Broker 在哪──────────────┘
```

| 概念 | 大白话 | 类比 | 本项目 |
|------|--------|------|--------|
| **Producer** | 发消息的一方 | 寄件人 | 用 `RocketMQTemplate` 的代码 |
| **Consumer** | 收消息的一方 | 收件人 | 标了 `@RocketMQMessageListener` 的类 |
| **Broker** | 真正存消息的服务器（磁盘） | 快递柜 | 本地 `10911` 端口那个进程 |
| **NameServer** | 记录"消息该发到哪个 Broker"的路由表 | 快递公司的调度中心 | 本地 `9876` 端口那个进程 |
| **Topic** | 消息的分类（主题） | 哪个小区的快递柜 | `FollowUnfollowTopic`、`DeleteNoteLocalCacheTopic` |
| **Tag** | Topic 下再细分消息类型 | 快递柜上的"生鲜/普通"标签 | `Follow`（关注）/ `Unfollow`（取关） |
| **Group** | 消费组（一组消费者） | 一组收件人 | `xiaohashu_group` |

**消息流转一句话**：生产者把消息（Topic + Tag + 内容）发给 Broker 存起来，消费者去 Broker 按 Topic 拉取自己关心的消息来消费。

---

## 3. 一条消息的一生（payload / SendCallback 拆解）

### 3.1 完整生命周期

```
① 生产者构造"消息"（包含：发到哪个 Topic + 内容 payload）
② 生产者调用 rocketMQTemplate.syncSend / asyncSend 发送
③ Broker 收到并落盘存储（消息此刻才"真正发出去了"）
④ 消费者从 Broker 拉取/被推送消息，触发 onMessage()
⑤ onMessage 里处理业务（写库、删缓存...）→ 消费完成
```

### 3.2 几个让你困惑的"名词"，逐个拆解

#### payload —— 就是"消息里的实际内容"

- 全称：**消息负载**，翻译成人话就是**你真正想传的那份数据**。
- 发送时你塞什么，消费者就收到什么。可以是字符串、数字、对象。
- 类比：寄快递时**包裹里的东西**（Topic 是地址，payload 是物品）。

```java
// payload 可以是普通字符串
rocketMQTemplate.syncSend("MyTopic", "hello world");

// payload 可以是数字（比如笔记 ID）
rocketMQTemplate.syncSend("MyTopic", noteId);

// payload 可以是对象转成的 JSON 字符串（项目里常用）
rocketMQTemplate.asyncSend("MyTopic", JsonUtils.toJsonString(followUserMqDTO), callback);
```

> 本项目约定：payload 一律用**字符串**（普通字符串 或 JSON 字符串），消费者拿到后 `Long.valueOf(...)` 或 `JsonUtils.parseObject(...)` 还原，最简单通用。

#### Message —— 一条完整的"消息"对象

一条 RocketMQ 消息 = **消息头**（Topic、Tag、属性）+ **消息体（payload）**。发送前可以包一层 `Message`：

```java
Message<String> message = MessageBuilder
        .withPayload("json字符串")   // 把 payload 塞进消息体
        .build();
```

#### SendResult —— 同步发送后，Broker 给你的"回执"

类比挂号信的回执单。`syncSend` 会阻塞等待 broker 确认，返回一个 `SendResult`，里面包含：

- 消息 ID（唯一标识）
- 发送状态
- 消息落在哪个队列等

```java
SendResult sendResult = rocketMQTemplate.syncSend(topic, payload);
log.info("发送成功，消息 ID: {}", sendResult.getMsgId());
```

#### SendCallback —— 异步发送后的"来电通知"

`asyncSend` 发出后**主线程立刻返回**（不阻塞），Broker 处理完后再回调你。这个回调就是 `SendCallback` 接口，它规定了你必须实现两个方法：

| 方法 | 什么时候被调用 | 参数 |
|------|--------------|------|
| `onSuccess(SendResult)` | **发送成功**时调用 | 成功回执（同 SendResult） |
| `onException(Throwable)` | **发送失败**时调用 | 异常对象 |

类比：异步发送 = 点了外卖；`SendCallback` = 外卖小哥送达后打给你的电话——**成功**电话告诉你"到了"，**失败**电话告诉你"出问题了"。

```java
rocketMQTemplate.asyncSend(topic, payload, new SendCallback() {
    @Override
    public void onSuccess(SendResult sendResult) {
        log.info("==> MQ 发送成功，消息 ID: {}", sendResult.getMsgId());
    }

    @Override
    public void onException(Throwable throwable) {
        log.error("==> MQ 发送异常: ", throwable);
    }
});
```

### 3.3 同步 / 异步 / 单向，到底选哪个？

| 发送方式 | 发完就返回？ | 知道结果？ | 类比 | 用在哪 |
|---------|:---:|:---:|------|--------|
| `syncSend` | ❌ 阻塞等回执 | ✅ SendResult | 挂号信（要签收回执） | 需要确认成功的场景 |
| `asyncSend` | ✅ 立刻返回 | ✅ 通过回调知道 | 微信消息（送达后通知） | 不阻塞主线程、又想知道结果——**项目最常用** |
| `sendOneWay` | ✅ 立刻返回 | ❌ 完全不管 | 扔纸飞机 | 低可靠通知，最快 |

---

## 4. 生产者：RocketMQTemplate 常用方法

`RocketMQTemplate` 是 rocketmq-spring 提供的"**发消息的工具类**"，注入后直接用：

```java
@Resource
private RocketMQTemplate rocketMQTemplate; // 注入，Spring 自动管理
```

### 4.1 三种发送方式（逐个看真实代码）

**① 同步发送 syncSend**（阻塞，等 broker 回执）

```java
// 阻塞直到 broker 确认，返回 SendResult（含消息 ID）
SendResult result = rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
```

**② 异步发送 asyncSend**（不阻塞，回调通知结果）—— 项目主力

```java
rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
    @Override
    public void onSuccess(SendResult sendResult) { log.info("发送成功"); }
    @Override
    public void onException(Throwable throwable) { log.error("发送失败", throwable); }
});
```

**③ 单向发送 sendOneWay**（最快，不关心结果）

```java
rocketMQTemplate.sendOneWay(topic, payload);
```

### 4.2 发送带 Tag 的消息（`topic:tag` 写法）

Topic 和 Tag 用**冒号**拼接在目的地字符串里，消费者就能按 Tag 区分消息类型：

```java
// 目的地 = "FollowUnfollowTopic:Follow"
String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_FOLLOW;
rocketMQTemplate.asyncSend(destination, message, callback);
```

### 4.3 发送延时消息

`asyncSend` 的最后两个参数：**超时时间（毫秒）** 和 **延时级别**（只能选预设档位，1=1s、2=5s、3=10s...）：

```java
rocketMQTemplate.asyncSend(topic, message,
        new SendCallback() { ... },   // 回调
        3000,                         // 超时时间：3 秒内没发出就失败
        1                             // 延时级别：1 = 延时 1s 后再投递给消费者
);
```

---

## 5. 消费者：@RocketMQMessageListener

消费端三步：**写一个类 → 标注解告诉 RocketMQ"我要消费什么" → 实现 onMessage 写业务**。

```java
@Component                            // 1. 交给 Spring 管理
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group",            // 消费组名
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW, // 消费哪个 Topic
        messageModel = MessageModel.CLUSTERING,       // 消费模式：默认集群；广播填 BROADCASTING
        selectorExpression = "Follow || Unfollow"     // （可选）只消费这些 Tag
)
public class XxxConsumer implements RocketMQListener<Message> {

    @Override
    public void onMessage(Message message) {   // 2. 收到消息后执行这里
        // 3. 从消息里取出内容，写业务逻辑
        String body = new String(message.getBody());  // 消息体（payload 原始字节）
        String tags = message.getTags();              // 消息的 Tag
        ...
    }
}
```

### 5.1 注解参数逐个解释

| 参数 | 含义 | 本项目 |
|------|------|--------|
| `consumerGroup` | 消费组名。**同一 Topic**，同组内按模式协作 | `xiaohashu_group` |
| `topic` | 消费哪个主题的消息 | 3 个 Topic |
| `messageModel` | 集群（默认，组内竞争）/ 广播（组内每实例都消费） | 见第 6 节 |
| `selectorExpression` | 按 Tag 过滤（不写=消费全部 Tag） | 可写 `"Follow || Unfollow"` |

### 5.2 onMessage 的参数是什么？

- `RocketMQListener<Message>`：收到的是**原始消息对象**，用 `getBody()` 拿字节、`getTags()` 拿标签；
- `RocketMQListener<String>`：框架已帮你把消息体转成**字符串**，参数直接就是内容（比如 noteId 字符串），更省事——本项目广播删缓存消费者就是这种。

---

## 6. 集群消费 vs 广播消费（本项目实战）

同一个 Topic，同一个消费组，两种模式行为完全不同：

```
【CLUSTERING 集群模式】— 组内抢着消费，一条消息只被一个实例处理
  实例A ──┐
  实例B ──┼──▶ 消息被"抢走"一次（适合落库，避免重复写库）
  实例C ──┘

【BROADCASTING 广播模式】— 组内每个实例都消费同一条消息
  实例A ──▶ 收到 ✅
  实例B ──▶ 收到 ✅   （每条消息所有实例都会处理）
  实例C ──▶ 收到 ✅
```

| 场景 | 模式 | 为什么 |
|------|------|--------|
| 关注/取关落库（`FollowUnfollowConsumer`） | **CLUSTERING** | 一条关注消息只该落一次库，实例间竞争即可 |
| 删本地缓存（`DeleteNoteLocalCacheConsumer`） | **BROADCASTING** | 每个实例都有一份自己的 Caffeine 本地缓存，必须每个实例都删 |

> 排错提示：广播模式下，生产者"发送成功"日志只在**收到 HTTP 请求的那个实例**出现，其他实例要看**消费者日志**（详见《项目排错手册》第 14 条）。

---

## 7. 延时消息（延迟双删用）

**延时消息 = 消息发出后，过 N 秒才投递给消费者**。

- 为什么延时：更新笔记后，先删一次 Redis 缓存 → 更新 DB → **1 秒后**再删一次（第二次删掉"并发读回填的旧缓存"），这就是"延迟双删"保证缓存一致性；
- 限制：延时时间**不能随意指定**，只能用 broker 预设的 18 个级别：

```text
级别:   1    2    3    4    5    6    7    8    9    10 ...
延时:   1s   5s   10s  30s  1m   2m   3m   4m   5m   6m  ...（最长 2h）
```

本项目用 level=1（1 秒）：`asyncSend(topic, message, callback, 3000, 1)`。

---

## 8. 项目中的 3 个 Topic 完整案例

| Topic                            | 服务          | 干什么                                          | 模式 | 生产端                     | 消费端                                 |
| :------------------------------- | ------------- | ----------------------------------------------- | ---- | -------------------------- | -------------------------------------- |
| `DeleteNoteLocalCacheTopic`      | note          | 更新/删除/改可见性/置顶后，让所有实例删本地缓存 | 广播 | `syncSend`                 | `DeleteNoteLocalCacheConsumer`         |
| `DelayDeleteNoteRedisCacheTopic` | note          | 延时 1s 再删一次 Redis（延迟双删）              | 集群 | `asyncSend` + delayLevel=1 | `DelayDeleteNoteRedisCacheConsumer`    |
| `FollowUnfollowTopic`            | user-relation | 关注/取关异步落库                               | 集群 | `asyncSend` + Tag          | `FollowUnfollowConsumer`（令牌桶削峰） |

以关注为例串起整条链路（对应第 3 节的"消息一生"）：

```text
【生产端】RelationServiceImpl.follow
  写 Redis 关注 ZSET（Lua 原子操作）成功后
  → 构造 Message（payload = FollowUserMqDTO 转 JSON）
  → asyncSend("FollowUnfollowTopic:Follow", message, callback)  ← 发完接口立刻返回
        ↓ Broker 落盘存储
【消费端】FollowUnfollowConsumer（集群模式，实例间竞争）
  → onMessage 收到 → rateLimiter.acquire() 令牌桶削峰
  → message.getTags() 判断是 Follow
  → 编程式事务：插关注表 + 粉丝表
  → Lua 更新被关注者的粉丝 ZSET
```

---

## 9. 项目落地流程（从零到跑通）

**Step 1**：pom 引入依赖（版本由根 pom 管理，无需写版本号）：

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
</dependency>
```

**Step 2**：配置 name-server 地址（`application-dev.yml`）：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876 # NameServer 地址
```

**Step 3**：定义常量类，集中管理 Topic / Tag：

```java
public interface MQConstants {
    String TOPIC_FOLLOW_OR_UNFOLLOW = "FollowUnfollowTopic";
    String TAG_FOLLOW = "Follow";
    String TAG_UNFOLLOW = "Unfollow";
}
```

**Step 4**：生产端——注入 `RocketMQTemplate`，在业务代码里发送。

**Step 5**：消费端——写 `@RocketMQMessageListener` 类实现 `RocketMQListener`。

**Step 6**：启动验证——前置条件：本地 NameServer（9876）+ Broker（10911）已启动。启动后看消费者注册日志，调接口看生产/消费日志。

---

## 10. 令牌桶限流（Guava RateLimiter）

### 10.1 令牌桶是什么？

一个桶，**每秒钟自动往桶里放 N 个令牌**；请求必须先拿一个令牌才能执行，桶里没有令牌就得**等着**（或直接放弃）。所以无论消息来得多猛，消费速度最多就是每秒 N 个 —— 这就是"**削峰**"。

### 10.2 常用方法

| 方法 | 作用 |
|------|------|
| `RateLimiter.create(5.0)` | 创建每秒放 5 个令牌的桶 |
| `acquire()` | 拿 1 个令牌，拿不到就**阻塞等待** |
| `tryAcquire()` | 尝试拿，拿不到**立即返回 false**，不等待 |

### 10.3 本项目用法（消费者削峰）

```java
// 配置类：创建令牌桶，速率来自配置（默认 5000/秒）
@Configuration
@RefreshScope   // Nacos 改了 rate-limit 后自动重建令牌桶
public class FollowUnfollowMqConsumerRateLimitConfig {
    @Value("${mq-consumer.follow-unfollow.rate-limit}")
    private double rateLimit;

    @Bean
    @RefreshScope
    public RateLimiter rateLimiter() {
        return RateLimiter.create(rateLimit); // 每秒 rateLimit 个令牌
    }
}

// 消费者：先取令牌再干活，消息再多也匀速处理，不把数据库打爆
public void onMessage(Message message) {
    rateLimiter.acquire();   // 拿不到令牌就阻塞在这，等下一秒放令牌
    ... // 落库业务
}
```

> 注意：`RateLimiter` 存在**本机内存**里，多实例部署时各实例独立限流、不共享；速率动态调整依赖 Nacos 配置 + `@RefreshScope`（详见《Nacos配置动态Bean指南》）。

---

## 11. 部署与排错

### 11.1 本地启动（Windows）

```powershell
# 1. 启动 NameServer（路由中心）
.\mqnamesrv.cmd

# 2. 启动 Broker（存储服务）—— 必须带 -c 指定配置文件！否则改的 broker.conf 不生效
.\mqbroker.cmd -n 127.0.0.1:9876 -c ..\conf\broker.conf autoCreateTopicEnable=true
```

### 11.2 broker.conf 关键配置

```properties
# 数据目录挪到大磁盘（Windows 用正斜杠，否则不生效）
storePathRootDir=D:/.../rocketmq-store
# 磁盘水位阈值：磁盘用到该比例就拒绝写入（默认 75）
diskMaxUsedSpaceRatio=90
# CommitLog 保留时间：改小让旧日志尽快清理
fileReservedTime=12
```

### 11.3 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| `CODE: 14 service not available` | broker 所在磁盘使用率超过阈值（`CL: 0.97` = 97%） | 清磁盘 / 删旧 store / 调高 `diskMaxUsedSpaceRatio` |
| 改了 broker.conf 不生效 | 启动命令没带 `-c` | 启动加 `-c ..\conf\broker.conf` |
| 其他实例没有"发送成功"日志 | 生产者日志只在请求落到的实例打印（正常） | 看其他实例的**消费者日志** |
| `syncSend` 慢（几十~几百 ms） | 同步要等 broker 回执 + 磁盘落盘 | 改 `asyncSend` / `sendOneWay` |
| 延时时间不对 | 只能选预设 18 级档位 | 用 `delayLevel` 档位，不能自定义秒数 |

详细排错见《项目排错手册》。
