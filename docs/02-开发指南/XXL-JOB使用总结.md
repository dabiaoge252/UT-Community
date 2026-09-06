# XXL-JOB 使用总结 —— 官方文档 + 项目落地版

> 更新时间：2026-08-21
> 内容依据：**XXL-JOB 官方文档**（[https://www.xuxueli.com/xxl-job/](https://www.xuxueli.com/xxl-job/)）+ 本项目（data-align 服务，`xxl-job-core` 3.4.2）的真实使用。
> 适用人群：**没系统学过 XXL-JOB** 的开发者，读完能理解"它是什么、怎么部署、怎么用、本项目怎么落地"。

## 目录

1. [先读 30 秒：什么是 XXL-JOB](#intro)
2. [一、整体架构：调度中心 + 执行器 + 任务](#ch1)
3. [二、调度中心（xxl-job-admin）](#ch2)
4. [三、执行器（EmbedServer + XxlJobSpringExecutor）](#ch3)
5. [四、任务运行模式：Bean 模式 vs GLUE 模式](#ch4)
6. [五、路由策略（10 种，重点：分片广播）](#ch5)
7. [六、高级配置：阻塞处理 / 超时 / 失败重试 / 告警](#ch6)
8. [七、XxlJobHelper 常用方法](#ch7)
9. [八、本项目落地：data-align 的 9 个 Job](#ch8)
10. [九、常见坑](#ch9)
11. [十、快速参考表](#ch10)
12. [参考](#ch11)

---

## 先读 30 秒：什么是 XXL-JOB

定时任务谁都会写：Spring 的 `@Scheduled(cron = "0 0 2 * * ?")` 一行搞定。但**只有一个 JVM、单机执行**时，问题随之而来：

| 问题 | 说明 |
|------|------|
| **重复执行** | 项目部署多实例后，每个实例的 `@Scheduled` 都会跑一次同一任务 → 数据重复处理 |
| **单点故障** | 只有一台机器执行，它挂了任务就没人跑 |
| **没法管理** | 改执行时间要改代码重启；看不到执行日志、失败没法告警重试 |
| **跑不完** | 大数据量任务一台机器扛不住 |

**XXL-JOB** = 美团点评开源的**分布式任务调度平台**，核心设计目标"开发迅速、学习简单、轻量级、易扩展"。它把"**什么时候跑、跑哪个、谁来跑、跑得怎样**"全部收归到一个独立的**调度中心**统一管理：

- **调度中心**：一个独立部署的 Web 管理后台（负责任务 CRUD、Cron 调度、日志、告警）
- **执行器**：嵌在你业务服务里的一段代码（负责接收调度请求、真正执行业务逻辑）
- **任务**：一段具体的业务逻辑（`@XxlJob("xxx")` 标注的方法）

一句话：**XXL-JOB = 把"定时"和"执行"拆开——调度中心只管按点发指令，执行器集群谁收到谁干活，天然解决多实例重复执行 + 可管理 + 可水平扩展。**

---

## <a id="ch1"></a>一、整体架构：调度中心 + 执行器 + 任务

### 1.1 三个角色

```
┌─────────────────┐      ① 执行器自动注册       ┌──────────────────────┐
│   调度中心        │ ◄────────────────────────── │   执行器（业务服务）   │
│  xxl-job-admin  │                            │  如 data-align 服务   │
│                 │       ② 到达 Cron，下发任务   │                       │
│  · 任务管理      │ ──────────────────────────►  │  · EmbedServer 端口   │
│  · 调度日志      │       ③ 线程池执行任务        │  · @XxlJob 任务方法    │
│  · 执行器管理    │ ◄────────────────────────── │  · 结果上报 / 日志文件  │
│                 │       ④ 执行结果主动上报       │                       │
└─────────────────┘                            └──────────────────────┘
        ▲ ⑤ 用户在后台点"查看日志" → 调度中心请求执行器读日志文件返回
```

| 角色 | 是什么 | 职责 | 部署方式 |
|------|--------|------|---------|
| **调度中心**（`xxl-job-admin`） | Spring Boot Web 应用 | 任务管理、按 Cron 触发调度、调度日志、失败告警、执行器管理 | **独立部署**（可集群，靠 DB 锁保证一次调度只触发一次） |
| **执行器**（`EmbedServer`） | 嵌入业务服务 | 自动注册到调度中心、接收调度请求、线程池执行任务、结果上报、日志服务 | 随业务服务部署（**可集群**） |
| **任务** | `@XxlJob` 标注的方法 | 具体业务逻辑 | 在业务服务里 |

### 1.2 调度执行全流程（官方文档流程）

1. 执行器启动后根据配置的调度中心地址**自动注册**；
2. 到达任务触发条件（Cron / 固定间隔 / 固定延时 / API 事件触发），调度中心下发任务；
3. 执行器基于**线程池**执行任务，把执行结果放入内存队列、把执行日志写入日志文件；
4. 执行器消费内存队列中的执行结果，**主动上报**给调度中心；
5. 用户在后台查看任务日志时，调度中心请求执行器，执行器读取日志文件返回日志详情。

### 1.3 一致性保障（官方）

调度中心通过 **DB 锁**保证集群分布式调度的一致性——**一次任务调度只会触发一次执行**；调度线程池多线程触发调度，确保调度精确执行、不被堵塞。

---

## <a id="ch2"></a>二、调度中心（xxl-job-admin）

> **这是什么**：XXL-JOB 源码里的 `xxl-job-admin` 模块，一个独立的 Spring Boot Web 应用，是任务的"总控台"。需要单独部署（默认 `http://localhost:8080/xxl-job-admin`，默认账号 `admin/123456`）。

### 2.1 部署步骤（官方）

1. 从 GitHub 拉源码（[xuxueli/xxl-job](https://github.com/xuxueli/xxl-job)），执行 `doc/db/tables_xxl_job.sql` 初始化调度数据库（`xxl_job`）；
2. 配置 `application.properties`（端口、数据源、`xxl.job.accessToken` 等），Maven 打包部署 `xxl-job-admin`；
3. 启动后访问管理后台，先建"执行器"，再建"任务"。

### 2.2 核心菜单

| 菜单 | 作用 |
|------|------|
| **执行器管理** | 维护执行器（按 `AppName` 分组）；执行器自动注册后可在线查看在线机器 |
| **任务管理** | 任务的 CRUD：Cron、路由策略、阻塞处理策略、超时/重试、JobHandler 匹配等 |
| **调度日志** | 查看每次调度结果（成功/失败/执行耗时），可滚动查看执行器输出的完整日志 |
| **运行报表** | 任务数量、调度次数、执行器数量、调度成功分布图等 |

---

## <a id="ch3"></a>三、执行器（EmbedServer + XxlJobSpringExecutor）

> **这是什么**：执行器是**嵌在业务服务里**的组件，负责和调度中心通信。它内置一个 Netty 服务（`EmbedServer`，默认端口 9999），等调度中心来连。

### 3.1 接入四步（官方 + 本项目对照）

**① 加依赖**（本项目在根 pom 统一管理版本 3.4.2，[data-align/pom.xml](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/pom.xml#L85)）：

```xml
<dependency>
    <groupId>com.xuxueli</groupId>
    <artifactId>xxl-job-core</artifactId>
</dependency>
```

**② 配 `XxlJobSpringExecutor` Bean**（本项目 [XxlJobConfig.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/config/XxlJobConfig.java#L26-L38)）：

```java
@Bean
public XxlJobSpringExecutor xxlJobExecutor() {
    XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
    executor.setAdminAddresses(xxlJobProperties.getAdminAddresses()); // 调度中心地址
    executor.setAppname(xxlJobProperties.getAppName());               // 执行器名称（与调度中心 AppName 对应）
    executor.setIp(xxlJobProperties.getIp());                         // 注册 IP（空则自动获取）
    executor.setPort(xxlJobProperties.getPort());                     // Netty 通信端口
    executor.setAccessToken(xxlJobProperties.getAccessToken());       // 通讯令牌（双方一致才允许通讯）
    executor.setLogPath(xxlJobProperties.getLogPath());               // 执行日志存放路径
    executor.setLogRetentionDays(xxlJobProperties.getLogRetentionDays()); // 日志保留天数
    return executor;
}
```

**③ 配置项**（本项目 [XxlJobProperties.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/config/XxlJobProperties.java)，前缀 `xxl.job`）：

| 配置 | 作用 |
|------|------|
| `xxl.job.admin.addresses` | 调度中心地址列表（逗号分隔，支持集群） |
| `xxl.job.accessToken` | 通讯令牌，调度中心与执行器**相同才允许通讯**（都为空则关闭校验） |
| `xxl.job.executor.appname` | 执行器 AppName，与调度中心"执行器管理"里配置的一致 |
| `xxl.job.executor.ip` | 执行器注册 IP（空 = 自动获取） |
| `xxl.job.executor.port` | Netty 通信端口（本项目日志显示 10001） |
| `xxl.job.executor.logpath` | 执行日志路径 |
| `xxl.job.executor.logretentiondays` | 日志保留天数（默认 30） |

**④ 在调度中心添加执行器**：AppName 填一致的值，执行器启动后会自动注册，后台能看到在线机器。

> 启动成功的标志：日志出现 `xxl-job remoting server start success ... port = 10001` 和 `xxl-job register jobhandler success, name:xxx`（本项目 [data-align 日志](file:///d:/java/xiaohashu/xiaohashu/logs/data-align.2026-08-12-0.log#L68)）。

---

## <a id="ch4"></a>四、任务运行模式：Bean 模式 vs GLUE 模式

| 模式 | 代码在哪 | 特点 | 本项目 |
|------|---------|------|--------|
| **Bean 模式** | 代码在**执行器服务**里，`@XxlJob("handlerName")` 标注方法 | 编译期就存在，随服务发布；JobHandler 名与调度中心"JobHandler"属性匹配 | ✅ 全部 9 个 Job 都用 |
| **GLUE 模式(Java)** | 代码以 **Groovy 源码**形式维护在**调度中心** | 在线编辑、实时编译生效，免部署上线；支持 30 个版本历史回溯；可注入执行器里的 Bean | ❌ 未用 |
| **GLUE 模式(脚本)** | Shell / Python / PHP / NodeJS / PowerShell 源码维护在调度中心 | 无需写 Java，在线编辑脚本任务 | ❌ 未用 |

**Bean 模式任务写法**（本项目 [CreateTableXxlJob.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/job/CreateTableXxlJob.java#L36-L64)）：

```java
@Component
public class CreateTableXxlJob {
    @XxlJob("createTableJobHandler")          // 方法名 = 调度中心配置的 JobHandler 名
    public void createTableJobHandler() throws Exception {
        XxlJobHelper.log("## 开始创建日增量数据表，日期: {}...", date);
        // ... 业务逻辑
    }
}
```

---

## <a id="ch5"></a>五、路由策略（10 种，重点：分片广播）

> **这是什么**：执行器**集群部署**时，一次调度该把任务发给哪台机器？由"路由策略"决定。这是 XXL-JOB 最核心的配置之一。

| 路由策略 | 行为 | 典型场景 |
|---------|------|---------|
| **第一个**（FIRST） | 固定选第一台机器 | 指定单机执行 |
| **最后一个**（LAST） | 固定选最后一台机器 | 指定单机执行 |
| **轮询**（ROUND） | 按顺序轮流选 | 负载均衡发任务 |
| **随机**（RANDOM） | 随机选一台 | 负载均衡 |
| **一致性 HASH**（CONSISTENT_HASH） | 按任务参数 hash 到固定机器 | 同一任务参数固定打到同一台（如按 userId 分片） |
| **最不经常使用**（LFU） | 选调用次数最少的机器 | 均衡 |
| **最近最久未使用**（LRU） | 选最久没被调用的机器 | 均衡 |
| **故障转移**（FAILOVER） | 先发一台，失败自动换下一台 | **高可用**：单机执行 + 失败自动切换 |
| **忙碌转移**（BUSYOVER） | 按顺序找空闲机器，第一个空闲的接收 | 高可用 |
| **分片广播**（SHARDING_BROADCAST） | **广播触发集群中所有机器各执行一次**，自动传递分片参数 | **大数据量并行处理**（本项目核心用法） |

### 5.1 分片广播原理（重点）

分片广播模式下，**一次调度会触发集群里每一台执行器都执行一次任务**，每台机器通过分片参数知道自己是谁：

- `XxlJobHelper.getShardIndex()`：当前实例的分片序号（从 0 开始）
- `XxlJobHelper.getShardTotal()`：总分片数（= 在线执行器数量）

各实例拿到不同的 `(shardIndex, shardTotal)`，**只处理自己分片对应的那部分数据**，互不冲突、并行执行。比如本项目按"分片序号"处理对应的分片表（[FollowingCountShardingXxlJob.java:48-56](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/job/FollowingCountShardingXxlJob.java#L48-L56)）：

```java
int shardIndex = XxlJobHelper.getShardIndex();  // 当前实例分片序号
int shardTotal = XxlJobHelper.getShardTotal();  // 总分片数
// 只处理"分片序号 = shardIndex"对应的那张分片表
String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);
```

**动态分片**（官方特性）：分片以执行器为维度，**执行器集群扩容/缩容后，下次调度自动重新分片**——新增机器即新增分片，任务处理能力随之提升。

> ⚠️ 注意：分片广播适合**"每台机器处理自己的数据分片"**的任务（如按分片查表）。若实例数 < 分片表数量，部分分片表没人处理（如 1 实例只有 shardIndex=0）；所以本项目对齐任务在查询前会**兜底建表**，且要求"部署实例数 ≥ 分片数"（见 [核心机制详解.md 第 2 节](file:///d:/java/xiaohashu/xiaohashu/docs/02-开发指南/核心机制详解.md)）。

---

## <a id="ch6"></a>六、高级配置：阻塞处理 / 超时 / 失败重试 / 告警

### 6.1 阻塞处理策略（任务来不及执行时怎么办）

| 策略 | 行为 |
|------|------|
| **单机串行**（SERIAL_EXECUTION，默认） | 上次没跑完，这次排队等，跑完再跑下一个 |
| **丢弃后续调度**（DISCARD_LATER） | 上次没跑完，这次**直接丢弃不执行** |
| **覆盖之前调度**（COVER_EARLY） | 上次没跑完，这次**取消上次的，直接跑新的** |

### 6.2 其他高级配置

| 配置 | 作用 |
|------|------|
| **任务超时时间** | 超过设定时间主动中断任务（避免任务卡死占用线程） |
| **失败重试次数** | 任务失败按预设次数自动重试（**分片任务支持分片粒度重试**） |
| **失败告警** | 默认邮件告警，预留扩展接口可扩展短信、钉钉等 |
| **子任务依赖** | 父任务成功执行后自动触发子任务（逗号分隔多个） |
| **任务参数** | 在线配置任务入参，`XxlJobHelper.getJobParam()` 读取 |

---

## <a id="ch7"></a>七、XxlJobHelper 常用方法

> `XxlJobHelper` 是 xxl-job-core 提供的**静态工具类**，任务代码里用它取上下文、写调度日志、主动设置结果。

| 方法 | 作用 |
|------|------|
| `XxlJobHelper.getJobParam()` | 获取任务配置的入参（String） |
| `XxlJobHelper.getShardIndex()` | 分片广播时**当前分片序号**（从 0 开始） |
| `XxlJobHelper.getShardTotal()` | 分片广播时**总分片数** |
| `XxlJobHelper.log(String, Object...)` | 写调度日志（`{}` 占位符），日志进 XXL-JOB 后台 |
| `XxlJobHelper.getJobId()` | 当前任务 ID |
| `XxlJobHelper.handleSuccess(String)` / `handleFail(String)` | 主动设置任务执行成功/失败 |

**本项目实际用到**：分片任务里 `getShardIndex()` / `getShardTotal()` 取分片参数、`log()` 写调度日志（如 [FollowingCountShardingXxlJob.java:51-56](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/job/FollowingCountShardingXxlJob.java#L51-L56)、[DeleteTableXxlJob.java:38-71](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/job/DeleteTableXxlJob.java#L38-L71)）。

---

## <a id="ch8"></a>八、本项目落地：data-align 的 9 个 Job

本项目在 `xiaohashu-data-align` 服务用 XXL-JOB 做"日增量计数对齐"，共 **9 个 Job**（Bean 模式）：

| Job（Handler 名） | 调度建议 | 作用 |
|------------------|---------|------|
| `CreateTableXxlJob`（`createTableJobHandler`） | 每日（建议零点前后） | 提前创建**明天**的 7 类 × 分片临时表（`CREATE TABLE IF NOT EXISTS`） |
| `FollowingCountShardingXxlJob`（`followingCountShardingJobHandler`） | 每日（处理昨天） | 分片广播：从 `t_following` 重算关注数 → 回写 `t_user_count` + Redis |
| `FansCountShardingXxlJob`（`fansCountShardingJobHandler`） | 每日（处理昨天） | 分片广播：从 `t_fans` 重算粉丝数 → 回写 + Redis |
| `NoteLikeCountShardingXxlJob`（`noteLikeCountShardingJobHandler`） | 每日（处理昨天） | 分片广播：从 `t_note_like`(status=1) 重算笔记点赞 → 回写 + Redis |
| `NoteCollectCountShardingXxlJob`（`noteCollectCountShardingJobHandler`） | 每日（处理昨天） | 分片广播：从 `t_note_collection` 重算笔记收藏 → 回写 + Redis |
| `UserLikeCountShardingXxlJob`（`userLikeCountShardingJobHandler`） | 每日（处理昨天） | 分片广播：`t_note_like join t_note` 重算用户获赞 → 回写 + Redis |
| `UserCollectCountShardingXxlJob`（`userCollectCountShardingJobHandler`） | 每日（处理昨天） | 分片广播：`t_note_collection join t_note` 重算用户获收藏 → 回写 + Redis |
| `NotePublishCountShardingXxlJob`（`notePublishCountShardingJobHandler`） | 每日（处理昨天） | 分片广播：从 `t_note`(status=1) 重算用户发布笔记数 → 回写 + Redis |
| `DeleteTableXxlJob`（`deleteTableJobHandler`） | 每日 | 删除**一个月前**的临时表（日期 × 分片循环 `DROP TABLE IF EXISTS`） |

**对齐任务统一流程**（以 [FollowingCountShardingXxlJob](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/job/FollowingCountShardingXxlJob.java#L47-L112) 为例）：

```
取分片参数 (shardIndex, shardTotal)
  → 处理"昨日日期"对应分片表（先兜底 CREATE TABLE IF NOT EXISTS，防建表任务漏跑）
  → 死循环批量查 1000 条发生变更的 userId / noteId
  → 从源表 count(*) 重算准确计数（t_following / t_note_like / ...）
  → 回写计数表 t_user_count / t_note_count
  → Redis Hash 存在则同步更新 count:user:{id} / count:note:{id}
  → 批量删除已处理记录（保证幂等，下次对齐不会重复处理）
  → 直到查空为止
```

> 为什么要"分片广播"：计数对齐要重算的数据量大（7 类计数 × 全量变更记录），单机跑太慢；分片广播让 **N 台执行器并行处理 N 个分片表**，处理能力随机器数线性扩展。完整业务链路见 [业务流程.md 第 19 节](file:///d:/java/xiaohashu/xiaohashu/docs/03-业务流程/业务流程.md#L533-L566)。

---

## <a id="ch9"></a>九、常见坑

### `@RefreshScope` 加在 Job 类上 → `job handler not found`

XXL-Job 的 `XxlJobSpringExecutor` 注册 handler 的流程是：`applicationContext.getBeansWithAnnotation(XxlJob.class)` 找 Bean → 反射 `bean.getClass().getDeclaredMethods()` 找方法上的 `@XxlJob` 注解。而 `@RefreshScope` 会把 Bean 替换成 **CGLIB 代理对象**，代理类方法**不携带 `@XxlJob` 注解** → handler 被静默跳过 → 调度时报 `job handler not found`。

**解决**：Job 类上**不能加 `@RefreshScope`**（只留 `@Component` + `@XxlJob`），需要动态刷新的配置（如 `table.shards`）放独立配置类（本项目放 [TableConfig.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/config/TableConfig.java)）注入使用。详见 [项目排错手册 #15](file:///d:/java/xiaohashu/xiaohashu/docs/04-问题排查与记录/项目排错手册.md#L381-L429)。

### 其他注意事项

1. **实例数 ≥ 分片数**：分片广播下实例少于分片表数量时，部分分片无人处理；
2. **执行器与调度中心 AccessToken 必须一致**，否则通讯失败；
3. **任务名（JobHandler）必须与代码里 `@XxlJob("xxx")` 完全一致**；
4. 生产环境调度中心建议**集群部署**（DB 锁保证不重复调度），并给调度数据库做好备份。

---

## <a id="ch10"></a>十、快速参考表

| 想做什么 | 怎么做 |
|---------|--------|
| 写一个定时任务 | 类加 `@Component`，方法加 `@XxlJob("handlerName")`，调度中心建任务选 Bean 模式 + 填 JobHandler |
| 集群部署只让一台执行 | 路由策略选"第一个/最后一个" |
| 单机执行 + 失败自动换机器 | 路由策略选"故障转移" |
| 大数据量并行处理 | 路由策略选"分片广播" + `getShardIndex/getShardTotal` |
| 取任务入参 | `XxlJobHelper.getJobParam()` |
| 写调度日志 | `XxlJobHelper.log("...{}", arg)` |
| 主动设失败 | `XxlJobHelper.handleFail("原因")` |
| 上次没跑完这次怎么办 | 阻塞处理策略：单机串行 / 丢弃后续 / 覆盖之前 |
| 任务卡死 | 设置任务超时时间，超时自动中断 |
| 失败要重试 | 设置失败重试次数（分片任务支持分片粒度重试） |
| 动态改代码不发布 | 用 GLUE(Java) 模式，在线编辑实时生效 |

---

## <a id="ch11"></a>参考

- XXL-JOB 官方中文文档：https://www.xuxueli.com/xxl-job/
- GitHub：https://github.com/xuxueli/xxl-job
- 本项目相关：项目结构梳理.md、[核心机制详解.md 第 2 节](file:///d:/java/xiaohashu/xiaohashu/docs/02-开发指南/核心机制详解.md)（分片存储/对齐原理）、[项目排错手册 #15](file:///d:/java/xiaohashu/xiaohashu/docs/04-问题排查与记录/项目排错手册.md#L381-L429)（@RefreshScope 坑）
