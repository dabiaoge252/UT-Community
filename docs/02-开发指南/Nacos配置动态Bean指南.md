# Nacos 配置中心动态刷新 Bean 指南（零基础版，结合本项目）

> 更新时间：2026-08-05
> 适用人群：**没学过 Nacos / 配置中心**的开发者。从"配置中心是干什么的"讲起，逐步深入到本项目代码（以 user-relation 令牌桶限流为例），所有概念配大白话类比 + 逐行注释示例。
> 适用版本：Spring Boot 3.0.2 + Spring Cloud Alibaba 2022.0.0.0

## 目录

1. [先看懂的：配置中心是干什么的](#1-先看懂的配置中心是干什么的)
2. [为什么配置要放 Nacos（放代码里有什么问题）](#2-为什么配置要放-nacos放代码里有什么问题)
3. [核心名词大白话（遇到不认识的先查这里）](#3-核心名词大白话遇到不认识的先查这里)
4. [先选方案：两种动态配置方式](#4-先选方案两种动态配置方式)
5. [前置条件（最容易错的一步）](#5-前置条件最容易错的一步)
6. [完整步骤：以 user-relation 令牌桶限流为例（6 步）](#6-完整步骤以-user-relation-令牌桶限流为例6-步)
7. [Data ID 加载顺序与配置优先级](#7-data-id-加载顺序与配置优先级)
8. [项目内其他 Nacos 动态 Bean 实例对照](#8-项目内其他-nacos-动态-bean-实例对照)
9. [刷新原理链路（一张图看懂）](#9-刷新原理链路一张图看懂)
10. [常见问题排查表](#10-常见问题排查表)

---

## 1. 先看懂的：配置中心是干什么的

**配置中心（Config Center）** 就是"**把配置文件从代码里抽出来，放到一台远程服务器上统一管理**"的地方。项目里那些经常要改的"参数"（比如限流阈值、存储类型、告警方式），以后都不写在本地代码里了，而是写在这台远程服务器上。

### 生活类比：餐厅的菜单板

- 以前的做法：菜单（配置）印在每一张桌子上（写死在代码里）。想改价格？得把每张桌子重新印刷一遍（改代码 + 重新发布 + 重启服务），又慢又容易漏。
- 现在用配置中心：菜单统一挂在大厅的电子屏上（Nacos 服务器）。改价格？服务员在控制台点一下，**所有桌子（所有服务）马上看到新价格**，不用重新印刷（不用重启）。

### 配置中心带来的三个好处

| 好处 | 大白话 | 本项目例子 |
|------|--------|-----------|
| **配置与代码分离** | 配置不再"藏"在代码里，运营/测试也能改 | 限流阈值、存储类型、告警方式都放 Nacos |
| **改配置不用重启** | 线上改个参数，点"发布"立刻生效，不用重新部署 | 改 `rate-limit` 后令牌桶速率秒级生效 |
| **多环境隔离** | 同一套代码，dev / test / prod 各用各的配置 | Nacos 里用 namespace（命名空间）区分 |

---

## 2. 为什么配置要放 Nacos（放代码里有什么问题）

### 2.1 如果把配置写死在代码里，会怎样？

**场景**：`user-relation` 服务里，关注/取关的 MQ 消费者每秒最多处理 5000 条消息（限流阈值）。

```yaml
# application.yml 里的本地配置（写死版本）
mq-consumer:
  follow-unfollow:
    rate-limit: 5000   # 每秒限流阈值
```

某天线上消息量暴增，你想把阈值从 5000 调到 10000：

- **写死版本**：改代码 → `mvn package` 打包 → 停服务 → 上传 → 重启 → 验证。最少 5~10 分钟，期间服务不可用，还有改错的风险。
- **Nacos 版本**：登录 Nacos 控制台 → 把 5000 改成 10000 → 点"发布"。**几秒内全部生效，服务一次都不用重启**。

### 2.2 生活类比：换插座 vs 换灯泡

- 配置写死在代码里 = **换灯泡**：得断电（停服务）、拆装（重新部署）、通电（重启）。
- 配置放 Nacos = **换一个可旋转的插座**：插头旋转一下角度（在控制台改个值），灯立刻变亮（配置立刻生效），全程不断电。

> 一句话总结：**动态配置（放 Nacos）的价值 = 改配置不用重启、不用重新发布**。这也是本文标题"动态刷新"的含义——配置一变，程序里的对象自动跟着变。

---

## 3. 核心名词大白话（遇到不认识的先查这里）

| 名词 | 大白话解释 | 生活类比 |
|------|-----------|---------|
| **Nacos** | 阿里巴巴开源的一站式服务发现 + 配置中心。既能管"服务之间怎么找到对方"（注册中心），又能管"配置放哪里"（配置中心） | 小区物业：既管每家每户的地址簿（注册中心），又管公告栏（配置中心） |
| **配置中心** | 把配置从代码里抽出来，放到远程服务器统一管理的地方 | 餐厅大厅的电子菜单屏 |
| **bootstrap.yml** | 应用**启动时最早加载**的配置文件。因为要"先连上 Nacos 才能拿到后面的配置"，所以这份文件必须最早被读到。它的职责就是：告诉程序"Nacos 在哪、我的配置叫什么名字" | 进小区前先看的"门卫指引牌"：告诉你怎么找到物业（Nacos） |
| **Data ID** | 每份配置在 Nacos 里的**唯一名字**（相当于文件名） | 公告栏上每张公告的标题 |
| **namespace（命名空间）** | 用来**隔离环境/项目**的抽屉，同一套 Nacos 可以分 dev、test、prod 各放一份配置 | 小区里不同栋楼的公告栏 |
| **Group（组）** | namespace 之下的再一级分组 | 同一栋楼里不同单元的公告栏 |
| **prefix（前缀）** | Data ID 的前半部分，本项目就是"应用名"（`${spring.application.name}`） | 公告标题的固定抬头 |
| **profile（环境）** | 开发环境标识，如 `dev`（本地开发）、`prod`（生产） | "测试版" / "正式版" |
| **@Value** | Spring 的注解，作用：**把配置文件里的某个值，填进下面这个字段里** | 填表时"把公告栏上的数字抄到你的表格里" |
| **Bean** | Spring 容器创建并管理的一个**对象**（俗称"豆子"）。你只管用，创建、销毁都交给 Spring | 工厂流水线上生产出来的标准零件 |
| **@Configuration** | 标注在类上，告诉 Spring："**这是一个配置类**，里面 `@Bean` 方法造出来的对象，都交给容器管理" | 给这个类贴一张"这是生产车间"的标签 |
| **@Bean** | 标注在方法上，告诉 Spring："**这个方法造出来的对象，放进容器**，别人需要时可以注入" | 车间里的一条生产线，出来的产品登记入库 |
| **@RefreshScope** | Spring Cloud 的注解，作用：**配置一变化，就把标注了这个注解的对象销毁掉、按新配置重新造一个** | "遥控器换电池"：不用换电视（不用重启应用），把电池（配置）一换，遥控器（Bean）立刻满血复活 |
| **@NacosValue** | Nacos 原生的取值注解，`autoRefreshed = true` 表示自动刷新字段值（方案 B 用） | 方案 B 的"遥控器"，只在特定依赖下能用 |
| **令牌桶（RateLimiter）** | Guava 提供的限流工具：每秒往桶里放 N 个令牌，拿不到令牌就等着。用来限制消费速度，防止打爆数据库 | 游乐园的旋转闸机，每秒最多放 N 个人进去 |
| **RefreshEvent** | Spring 发布的"配置刷新"事件，通知所有 `@RefreshScope` 的 Bean "该重新造了" | 广播里喊了一嗓子："各单位注意，公告栏更新了！" |
| **自动装配（AutoConfiguration）** | Spring Boot 的机制：引了依赖后，Spring 自动帮你把需要的 Bean 创建好，不用你手动 new | 买了个"智能插座"，插上电自动开始工作，不用自己接线 |

---

## 4. 先选方案：两种动态配置方式

"动态配置"有两条技术路线，先看对比表再选：

| 对比项 | **方案 A：Spring Cloud Alibaba**（推荐，项目主流） | **方案 B：Nacos 原生注解** |
|--------|-----------------------------------------------|--------------------------|
| 所需依赖 | `spring-cloud-starter-alibaba-nacos-config` | `nacos-config-spring-boot-starter` |
| 代码写法 | `@Configuration`/`@Component` + `@RefreshScope`，字段用 `@Value` | 字段直接 `@NacosValue(value = "${xxx}", autoRefreshed = true)` |
| 刷新原理 | Nacos 配置变更 → 发布 `RefreshEvent` → `RefreshScope` **销毁并重建 Bean** | Nacos 监听器直接**更新字段值**，Bean 不重建 |
| 项目现状 | auth / oss / user-relation 已使用（✅） | auth 的 [TestController](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/controller/TestController.java#L17) 曾用，但依赖被注释，**当前不生效** |

**结论**：本项目统一使用**方案 A**（`@RefreshScope`）。本文后续步骤均按方案 A 编写。

> 两种方案的本质区别，一句话：
> - 方案 A = **换遥控器**（把 Bean 销毁重建，整个对象都是新的）；
> - 方案 B = **换电池**（对象还是那个对象，只是里面的值被直接改掉）。
> 本项目推荐方案 A，因为有的对象（比如令牌桶）不支持"原地改速率"，必须整个重建，方案 A 正好合适。

---

## 5. 前置条件（最容易错的一步）

### 5.1 必须引入两个依赖

```xml
<!-- ① Nacos 配置中心（动态刷新必须，只引 nacos-discovery 不生效） -->
<!--    大白话：这个依赖是"连配置中心的钥匙"，没它程序根本不看 Nacos 的配置 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>

<!-- ② 启用 bootstrap.yml（Spring Boot 2.4+/Cloud 2020+ 默认关闭 bootstrap 上下文） -->
<!--    大白话：新版 Spring Boot 默认不读 bootstrap.yml，必须额外引入这个依赖才读。
             不引入的话，第 6 步写的 bootstrap.yml 会被无视，程序连不上 Nacos -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

> 版本由根 POM 的 `spring-cloud-alibaba-dependencies` / `spring-cloud-dependencies` 统一管理，无需写 version。
>
> ⚠️ **最容易犯的错**：只引了 `nacos-discovery`（注册中心，管"服务发现"）没引 `nacos-config`（配置中心，管"动态配置"）。两个依赖管的事完全不同，`@RefreshScope` 能不能刷新，关键看 **config** 依赖在不在。

### 5.2 先确认目标服务是否已具备 nacos-config 依赖

| 服务 | nacos-config 依赖 | `@RefreshScope` 是否可刷新 |
|------|:---:|:---:|
| auth | ✅ | ✅ |
| oss | ✅ | ✅ |
| **user-relation** | ✅ | ✅ |
| note | ❌（仅 discovery） | ❌ |
| kv | ❌（仅 discovery） | ❌ |
| user | ❌（仅 discovery） | ❌ |
| gateway | ❌（仅 discovery） | ❌ |
| distributed-id-generator | ❌（仅 discovery） | ❌ |

> 若目标服务只有 `nacos-discovery`，`@RefreshScope` 不会刷新（见常见问题排查表第 1 条）。
>
> 大白话总结：**表格里 ✅ 的服务，改配置才能"免重启生效"；❌ 的服务想用动态配置，得先按 5.1 补依赖**。

---

## 6. 完整步骤：以 user-relation 令牌桶限流为例（6 步）

> **目标**：把关注/取关 MQ 消费者的令牌桶速率 `mq-consumer.follow-unfollow.rate-limit` 放到 Nacos 管理，**修改配置后自动重建 `RateLimiter`**（Guava 令牌桶不支持原地改速率，正好用 Bean 重建实现）。
>
> 为什么要重建而不是改值：令牌桶像一个"沙漏"，每秒漏 N 粒沙是**出厂定死的**，想改速率只能换一个新沙漏（重建 Bean），不能把旧沙漏的孔改大。这正是 `@RefreshScope` 的用武之地。

### Step 1：引入依赖（按 5.1）

user-relation-biz 的 [pom.xml](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/pom.xml#L55-L62) 已有 `spring-cloud-starter-bootstrap` 与 `spring-cloud-starter-alibaba-nacos-config`，无需重复添加；**令牌桶还需 Guava**：

```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <!-- 版本号不用写：根 POM 已统一管理（33.0.0-jre） -->
</dependency>
```

> 大白话：Guava 是 Google 的开源工具包，`RateLimiter`（令牌桶）就住在它里面。要"造沙漏"，先得把"造沙漏的工厂"引进来。

### Step 2：配置 bootstrap.yml（连接 Nacos 配置中心）

`src/main/resources/config/bootstrap.yml`（本项目 user-relation 的真实配置，逐行注释）：

```yaml
spring:
  application:
    name: xiaohashu-user-relation # 应用名，也是 Data ID 前缀（给配置起名字用的）
  profiles:
    active: dev                   # 激活的环境：dev（本地开发）
  cloud:
    nacos:
      config:                     # ====== 配置中心相关配置（管"配置放哪、怎么连"）======
        server-addr: http://127.0.0.1:8848 # 配置中心地址（Nacos 服务器在哪）
        prefix: ${spring.application.name} # Data ID 前缀（默认即应用名，即 xiaohashu-user-relation）
        group: DEFAULT_GROUP               # 所属组（默认组）
        namespace: xiaohashu               # 命名空间（与控制台一致，隔离环境用）
        file-extension: yaml               # 配置文件格式（Data ID 后缀，如 .yaml）
        refresh-enabled: true              # 开启动态刷新（关键！false 的话改了配置不会自动生效）
      discovery:                  # ====== 注册中心相关配置（管"服务间互相发现"，与上面是两码事）======
        enabled: true             # 启用服务发现（把自己的地址注册到 Nacos，让别人能找到）
        group: DEFAULT_GROUP      # 所属组
        namespace: xiaohashu      # 命名空间
        server-addr: 127.0.0.1:8848 # 注册中心地址
```

> 注意：`config` 和 `discovery` 是两块独立配置；`refresh-enabled`、`prefix`、`file-extension` 属于 **config** 下，不要写进 discovery。
>
> 大白话：bootstrap.yml 是"门卫指引牌"，程序一启动最先读它，照着它找到 Nacos（8848 端口），再在 Nacos 里找到自己的配置。

### Step 3：Nacos 控制台创建配置

**做什么**：在 Nacos 网页上新建一份配置，把限流阈值放上去。
**为什么**：配置只有放在 Nacos 上，才能"改了不重启就生效"。

1. 打开 Nacos 控制台（默认 `http://localhost:8848/nacos`，账号密码默认 `nacos/nacos`）
2. **配置管理 → 配置列表**，切换到命名空间 `xiaohashu`
   - （为什么：namespace 必须和 bootstrap.yml 里的 `namespace: xiaohashu` 一致，否则找不到）
3. **Data ID** 规则：`${prefix}.${file-extension}` → 填 **`xiaohashu-user-relation.yaml`**
   - （为什么：prefix 是应用名、file-extension 是 yaml，拼起来正好是这个名，两边必须对得上）
4. Group 填 `DEFAULT_GROUP`，配置格式选 `YAML`
   - （为什么：Group 要和 bootstrap.yml 里的 `group: DEFAULT_GROUP` 一致）
5. 配置内容（覆盖或新增限流阈值）：

```yaml
mq-consumer:          # 一级 key（和本地 application.yml 里的结构保持一致）
  follow-unfollow:    # 二级 key
    rate-limit: 10000 # 每秒生成令牌数（这里改成 10000，会覆盖本地默认的 5000）
```

> 说明：本地 [application.yml](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/resources/config/application.yml#L10-L12) 中已有默认值 `rate-limit: 5000`；**Nacos 远程配置优先级更高，会覆盖本地值**（优先级规则详见第 7 节）。
>
> 大白话：本地写 5000 是"兜底默认值"，Nacos 上写 10000 是"线上正式值"。程序启动后两个都读到，但 Nacos 的说了算。

### Step 4：编写动态 Bean 配置类

**做什么**：写一个配置类，把令牌桶 `RateLimiter` 做成"动态 Bean"（配置一变就重建）。
**为什么**：令牌桶不能原地改速率，必须让 Spring 在配置变化时把旧桶扔掉、按新配置造新桶——这就是 `@RefreshScope` 干的事。

`FollowUnfollowMqConsumerRateLimitConfig`（[项目真实代码](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/config/FollowUnfollowMqConsumerRateLimitConfig.java#L15-L26)）：

```java
@Configuration     // 告诉 Spring：这是一个"配置类"，里面的 @Bean 方法造出的对象要交给容器管理
@RefreshScope      // 配置类标注：Nacos 配置一变，这个类里所有 Bean 都会被销毁重建
public class FollowUnfollowMqConsumerRateLimitConfig {

    // @Value：从配置里取值填进来（先读 Nacos 远程，读不到读本地 yml）
    @Value("${mq-consumer.follow-unfollow.rate-limit}") // 配置 key 要写全路径
    private double rateLimit;                           // 当前限流阈值（每秒几个令牌）

    @Bean              // 把下面方法造出来的 RateLimiter（令牌桶）放进 Spring 容器
    @RefreshScope      // Bean 方法也标注：重建时用最新 rateLimit 重新创建令牌桶
    public RateLimiter rateLimiter() {
        // 用最新读到的 rateLimit 造一个新令牌桶（每秒放 rateLimit 个令牌）
        return RateLimiter.create(rateLimit);
    }
}
```

> 大白话：这个类像一个"沙漏工厂"。配置变了，`rateLimit` 字段先被填上新值，然后整个工厂被 `@RefreshScope` 通知"重来一遍"，重新按新值造一个新的沙漏给消费者用。

### Step 5：消费方注入使用

**做什么**：在消费者里把令牌桶注入进来，消费消息前先"取令牌"。
**为什么**：取不到令牌就阻塞等待，消息再多也按每秒 rateLimit 条匀速处理，这就是削峰。

`FollowUnfollowConsumer`（[项目真实代码](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/consumer/FollowUnfollowConsumer.java#L50-L58)）：

```java
@Resource
private RateLimiter rateLimiter; // 直接注入令牌桶；刷新由 Spring 代理自动完成（我们不用管重建细节）

@Override
public void onMessage(Message message) {
    // 流量削峰：先取令牌，无令牌则阻塞等待，等下一秒放令牌再继续
    rateLimiter.acquire();
    ... // 后续落库等业务逻辑（关注表、粉丝表）
}
```

> 大白话：消费者每次处理消息前先过"闸机"（acquire）。闸机每秒只放 rateLimit 个人过，多出来的在门口排队，数据库就不会被瞬间打爆。

### Step 6：验证

**做什么**：启动服务，改配置，观察是否免重启生效。
**为什么**：验证整条链路（连上 Nacos → 拉到配置 → 刷新 Bean）是否真的通。

1. 启动 user-relation，日志出现 `nacos config dataId: xiaohashu-user-relation.yaml` 说明已拉取远程配置；
2. 调用关注接口，观察消费者消费正常；
3. 到 Nacos 控制台把 `rate-limit` 改为更大/更小值并**发布**（注意：改完必须点"发布"按钮，不然不生效）；
4. 观察日志出现 `Refreshing scope ...`（刷新生效），再次消费时 `RateLimiter` 已按新速率工作。

---

## 7. Data ID 加载顺序与配置优先级

### 7.1 Data ID 的加载顺序

**大白话**：Nacos 里可以建"同名不同环境"的多份配置，比如带 `-dev` 后缀的是开发版、不带后缀的是公共版。程序启动时会**优先加载带环境后缀的那份**。

Spring Cloud Alibaba 按以下顺序加载（**profile 变体优先级更高**）：

```text
${prefix}-${spring.profiles.active}.${file-extension}   # 高：xiaohashu-user-relation-dev.yaml
${prefix}.${file-extension}                             # 低：xiaohashu-user-relation.yaml
```

> 想按环境区分配置时，可分别建 `xiaohashu-user-relation.yaml`（公共）与 `xiaohashu-user-relation-dev.yaml`（dev 专属）。
>
> 生活类比：公告栏上贴了两张价格表，一张写"通用价格"、一张写"暑期特惠价"。顾客（程序）先看特惠价（带 -dev 的），没贴特惠价才看通用价（不带后缀的）。

### 7.2 配置优先级（本地 vs Nacos）

**大白话**：同一个配置项，在多处出现时，"越远程的越厉害"——Nacos 上的值会压过本地 yml 里的值。

```text
${prefix}-${profile}.yaml（Nacos，最高）
    ↓ 覆盖
${prefix}.yaml（Nacos）
    ↓ 覆盖
bootstrap.yml
    ↓ 覆盖
application.yml / application-{profile}.yml（本地，最低）
```

即：**Nacos 上的 `rate-limit` 会覆盖本地 application.yml 的值**，这是动态配置的预期行为。
（这也是为什么 Step 3 里 Nacos 写 10000，最终生效的是 10000，而不是本地 yml 的 5000。）

---

## 8. 项目内其他 Nacos 动态 Bean 实例对照

**大白话**：本项目一共有 4 处用到"配置动态刷新生效"，其中 3 处是方案 A（生效中），1 处是方案 B（当前不生效，仅作历史参考）。

| 服务 | 配置类 | 动态配置项 | 作用 |
|------|--------|-----------|------|
| user-relation | [FollowUnfollowMqConsumerRateLimitConfig](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/config/FollowUnfollowMqConsumerRateLimitConfig.java) | `mq-consumer.follow-unfollow.rate-limit` | 关注/取关消费者令牌桶速率 |
| auth | [AlarmConfig](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/alarm/AlarmConfig.java#L18-L32) | `alarm.type`（sms / mail） | 切换告警实现（SmsAlarmHelper / MailAlarmHelper） |
| oss | [FileStrategyFactory](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-oss/xiaohashu-oss-biz/src/main/java/com/quanxiaoha/xiaohashu/oss/biz/factory/FileStrategyFactory.java#L22-L35) | `storage.type`（minio / aliyun） | 切换文件上传策略 |
| auth（⚠️ 不生效） | [TestController](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/controller/TestController.java#L17) | `rate-limit.api.limit` | 用 `@NacosValue`，但依赖被注释，当前不刷新 |

三个生效实例的代码模式**完全一致**（都是"方案 A 四件套"）：

```java
@Configuration                 // 声明为配置类
@RefreshScope                  // 配置类级别：配置一变，内部 Bean 重建
public class XxxConfig {
    @Value("${prefix.key}") private String value;   // 或 double/int；@Value 从配置取值
    @Bean                        // 声明 Bean（造对象）
    @RefreshScope                // Bean 级别：重建时用最新配置重新造
    public Xxx bean() { return createBy(value); }   // 用最新配置重建 Bean
}
```

> 逐个看这 3 处的"动态"体现在哪：
> - **user-relation**：改 `rate-limit` → 令牌桶重建（新速率生效）；
> - **auth**：改 `alarm.type` → 决定返回 `SmsAlarmHelper` 还是 `MailAlarmHelper`（换告警渠道）；
> - **oss**：改 `storage.type` → 决定返回 `MinioFileStrategy` 还是 `AliyunOSSFileStrategy`（换存储服务商）。
>
> 共同点：**配置项一变，工厂方法被重新执行，返回一个按新配置造出来的新对象**。

---

## 9. 刷新原理链路（一张图看懂）

**大白话**：把整条链路想象成"公告栏更新 → 广播通知 → 各单位重新抄写 → 换上新图纸"。

```text
Nacos 控制台修改配置并发布                    （管理员改了公告栏）
        ↓
Nacos Config 客户端收到变更（refresh-enabled: true）   （门卫看到公告变了）
        ↓
发布 RefreshEvent（ConfigRefreshListener）            （广播："公告更新了！"）
        ↓
RefreshScope.refreshAll() 销毁 @RefreshScope 标记的 Bean   （各单位把旧图纸扔了）
        ↓
下次调用时重新实例化 Bean（重新解析 @Value / @ConfigurationProperties）  （按新公告重新抄图纸、按新图纸生产）
```

原理要点（大白话版）：

- `@RefreshScope` 的 Bean 实际是**代理对象**（一个"替身"），刷新时旧实例销毁、新实例按最新配置创建。业务代码里注入的始终是这个"替身"，所以**我们一行都不用改**，Spring 悄悄把幕后对象换了；
- **令牌桶刷新**：Guava `RateLimiter` 无法原地改速率，`@RefreshScope` 重建 Bean 正好解决——旧桶废弃，新桶按新速率生效（沙漏不能改孔，直接换新沙漏）；
- 注意：`RateLimiter` 是**单机内存**令牌桶，多实例部署时各实例独立限流，不共享。（大白话：每个服务实例各有一个自己的沙漏，各自限自己的速，不会互相借令牌。）

---

## 10. 常见问题排查

| 问题 | 原因 | 解决 |
|------|------|------|
| `@RefreshScope` 不生效 | 只有 `nacos-discovery`，没引 `nacos-config` | 补依赖，见 5.1 |
| 启动不加载远程配置 | bootstrap.yml 未生效 | 补 `spring-cloud-starter-bootstrap` 依赖 |
| 改了配置无刷新日志 | `refresh-enabled: false` 或 namespace/group/Data ID 不匹配 | 核对 Step 2/3 的 namespace `xiaohashu`、group `DEFAULT_GROUP`、Data ID `xiaohashu-user-relation.yaml` |
| 读到的还是旧值 | `target/classes` 是旧 yml；或远程配置未发布 | 重新 `mvn clean compile`；确认 Nacos 已点击"发布" |
| `@NacosValue` 不刷新 | 用的是方案 B，但 `nacos-config-spring-boot-starter` 被注释 | 统一改用方案 A（`@Value` + `@RefreshScope`） |
| 刷新后 Bean 报错 | 远程配置值写错（如 `alarm.type` 拼错） | 检查 Nacos 配置内容 |

> 排查口诀：**先看依赖（config 在不在）→ 再看 bootstrap（有没有生效）→ 再对三件套（namespace / group / Data ID）→ 最后看发布（点没点发布）**。

