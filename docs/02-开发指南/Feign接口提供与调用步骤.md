# Feign 接口提供与调用（零基础版，结合本项目）

> 更新时间：2026-08-10
> 适用人群：**没学过微服务、没接触过 Feign 远程调用**的开发者。
> 以 **auth（认证服务）调用 user（用户服务）** 的用户登录/注册为例，从"为什么要远程调用"讲起，逐步讲清"一个模块的功能如何对外提供接口、其他服务如何调用"的完整套路。所有技术名词配大白话解释，所有代码逐行中文注释。
> 本项目已按此模式完成：`auth → user`（用户登录、密码更新）、`user → oss`（文件上传）。

## 目录

1. [先看懂的：服务之间为什么要"远程调用"？](#1-先看懂的-服务之间为什么要远程调用)
2. [认识主角：Feign 是什么？为什么用它？](#2-认识主角-feign-是什么为什么用它)
3. [整体思路：接口怎么提供、接口怎么调用](#3-整体思路-接口怎么提供接口怎么调用)
4. [完整调用链时序图：auth 调 user 的用户登录](#4-完整调用链时序图auth-调-user-的用户登录)
5. [服务提供方（user 服务）8 个步骤](#5-服务提供方user-服务8-个步骤)
6. [服务调用方（auth 服务）5 个步骤](#6-服务调用方auth-服务5-个步骤)
7. [关键注意点：9 条避坑清单](#7-关键注意点9-条避坑清单)
8. [项目内已有实践对照表](#8-项目内已有实践对照表)

---

## 1. 先看懂的：服务之间为什么要"远程调用"？

### 1.1 生活类比：打电话给别的部门

想象一家大公司（**微服务架构**：一个大系统被拆成很多个独立的小系统/小服务，各管一摊，比如"用户服务"只管用户、"认证服务"只管登录）。

- 用户在认证服务（auth）里登录，认证服务需要知道"这个手机号有没有注册过、密码是多少"——但**这些数据存在用户服务（user）的数据库里**；
- 于是认证服务只能**打电话给用户服务**："喂，帮我查一下这个手机号对应的用户信息。"（这通"电话"就是**服务间远程调用**）
- 打完电话拿到结果，认证服务继续自己的登录逻辑。

打电话（远程调用）相比"把所有东西都搬到一个楼里"（把所有业务塞进一个服务）的好处：各服务**职责清晰、独立开发部署**，需要谁的数据就打电话问谁。

### 1.2 RPC 是什么？

**RPC（Remote Procedure Call，远程过程调用）** 正式一点说，是"一台机器上的程序，像调用本地方法一样去调用另一台机器上的程序"的技术。

- 大白话：**隔着网络的"函数调用"**——你在代码里写 `userRpcService.findUserByPhone(phone)`，感觉就像调自己项目里的一个方法，实际上这行代码背后是在**通过网络请求另一台机器上的服务**。
- "过程/方法"指的就是代码里的函数；"远程"就是这个函数不在自己这台机器上。

### 1.3 为什么需要 RPC？三个理由

| 理由 | 大白话 |
|------|--------|
| 数据不在自己手里 | 登录要查用户信息，但用户表在 user 服务的数据库里，必须找 user 服务要 |
| 避免重复造轮子 | 多个服务都要"按手机号查用户"，与其各自连一遍数据库，不如由 user 服务统一提供这个能力 |
| 微服务架构的必然结果 | 服务拆开之后，服务之间交流只能靠网络，RPC 就是给"服务间打电话"封装一层好用的工具 |

---

## 2. 认识主角：Feign 是什么？为什么用它？

### 2.1 Feign 是什么？

**Feign（OpenFeign）** 是一个 **"声明式 HTTP 客户端"** 框架。翻译成人话：

- 它是 Spring Cloud 全家桶里的一个工具，专门用来**发起 HTTP 请求**；
- "**声明式**"意思是：你不用写"怎么发请求"的一堆代码（拼 URL、拼请求头、解析响应……），**只需要声明"我要调哪个接口、传什么参数、返回什么"，剩下的框架全帮你干**；
- 最终效果：**像调本地方法一样调远程接口**——`userFeignApi.findByPhone(dto)` 这一行，底层自动帮你发了一个 HTTP POST 请求给 user 服务。

### 2.2 Feign vs 手写 HTTP（RestTemplate / HttpClient）

| 对比项 | 手写 HTTP 请求 | 用 Feign |
|--------|---------------|----------|
| 代码量 | 要写：拼 URL、设请求头、把对象转 JSON、解析返回的 JSON 再转回对象 | 只写一个接口方法声明，全自动 |
| 出错率 | 容易拼错 URL、字段对不上、类型写错 | 接口路径、参数类型编译期就能检查 |
| 服务发现 | 要自己记住目标服务的 IP 端口 | 按服务名找（配合 Nacos），服务换 IP 也不影响 |
| 负载均衡 | 要自己实现轮询 | 配合 LoadBalancer 自动挑选一个实例 |
| 可读性 | 满屏模板代码，看不出调的是什么 | 接口名 = 业务名，一眼看懂 |

> 一句话总结：**手写 HTTP 像自己打车还要记路线；Feign 像用打车软件，说个目的地就完事**。

### 2.3 名词小词典（第一次见面先混个脸熟）

| 名词 | 大白话一句话解释 |
|------|------------------|
| 注解（Annotation） | 写在代码上的"贴标签"，框架看到标签就知道要干什么（`@FeignClient` 就是"这是个远程接口"的标签） |
| 接口（interface） | Java 里只写"有哪些方法、参数、返回值"、不写具体实现的"合同"，别人照着合同实现 |
| Controller（控制层） | 接收 HTTP 请求的"前台接待"，把请求转给业务代码，再把结果返回给调用方 |
| Service（业务层） | 真正干活的地方，写"登录怎么验、注册怎么建"等业务规则 |
| Mapper（数据访问层） | 和数据库打交道的"搬运工"，负责执行 SQL 存取数据（本项目用 MyBatis） |
| DTO | 只用来"装数据、传数据"的盒子，比如把手机号从 auth 服务传到 user 服务 |
| 序列化 / Jackson | 把 Java 对象转成 JSON 字符串（或反过来）。HTTP 上只能传字符串，所以对象要先"打包"成 JSON 再传 |
| Nacos（注册中心） | 服务们的"通讯录"：每个服务启动后把"我叫什么、在哪个 IP 端口"登记上去，别的服务按名字就能查到 |
| 负载均衡（LoadBalancer） | 一个服务开了多个实例时，自动轮流把请求分给不同实例，避免某个实例累死 |
| 依赖（Maven 依赖） | 通过 pom.xml 声明"我要用哪个代码包"，Maven 自动下载并放进项目 |

---

## 3. 整体思路：接口怎么提供、接口怎么调用

本项目每个业务服务都拆成两个 Maven 模块（Maven 模块 = 同一个大项目里的一个独立子工程，有自己的代码和 pom.xml）：

| 模块 | 名字规律 | 放什么 | 例子 |
|------|---------|--------|------|
| **接口契约层** | `*-api` | **Feign 接口 + DTO**（只写"接口长什么样"，不写实现），供其他服务依赖 | `xiaohashu-user-api` |
| **业务实现层** | `*-biz` | **Controller / Service / Mapper 等实现**（真正干活的地方） | `xiaohashu-user-biz` |

- 服务之间通过 **Feign 声明式远程调用**：调用方引入对方的 `*-api` 模块后，**像调用本地方法一样调用远程接口**。
- 前提：两个服务都要注册到 **Nacos**（依赖 `spring-cloud-starter-alibaba-nacos-discovery`，即"注册发现"依赖）。Feign 通过**服务名**发现目标实例，并配合 LoadBalancer **负载均衡**（自动挑一个实例来调）。

> 为什么要拆成 `api` 和 `biz` 两个模块？——把"接口合同"和"内部实现"分开：调用方只需要依赖体积小、内容纯的 `api` 模块（合同），不需要把整个服务的实现代码都引进来，也防止实现层代码被外部乱依赖。

---

## 4. 完整调用链时序图：auth 调 user 的用户登录

以下是一次"用户登录（密码方式）"的完整调用链：从浏览器请求 auth 服务开始，一路"接力"到 user 服务的数据库：

```
AuthController (/auth/login)                        ← ① auth 服务的 HTTP 接口，接收登录请求
   └─▶ AuthServiceImpl.loginAndRegister             ← ② auth 的业务层，处理登录逻辑
          └─▶ UserRpcService.findUserByPhone(phone)          [auth 模块, 封装 Feign]
                 └─▶ UserFeignApi.findByPhone(FindUserByPhoneReqDTO)  [Feign, 走 HTTP]
                        └─▶ UserController (/user/findByPhone) [user-biz 实现]
                               └─▶ UserServiceImpl.findByPhone   ← ③ user 业务层，查用户
                                      └─▶ UserDOMapper.selectByPhone(phone)  [MySQL]
```

解读：箭头一层层往下，**每一层只认识自己下面那一层**（auth 的 Controller 只调 Service，Service 只调 RPC 封装层……），这就是"分层 + 封装"的好处：每一层职责单一、好维护。

登录场景下三种操作分别调用哪个接口：

- **密码登录**：调 `UserFeignApi.findByPhone`（按手机号查出用户，拿到加密密码后比对各）——即上图链路；
- **验证码登录**：调 `UserFeignApi.registerUser`（**注册/查重**：没注册过的手机号直接注册，注册过的返回老用户 ID）；
- **修改密码**：调 `UserFeignApi.updatePassword`（把新密码的密文传过去更新）。

---

## 5. 服务提供方（user 服务）8 个步骤

**这一节的主角是 user 服务**：它要把"注册、查用户、改密码"这些能力做成接口，供别的服务（auth）调用。共 8 步。

### 步骤 1：在 api 模块定义 Feign 接口（声明 HTTP 契约）

**做什么**：在 `xiaohashu-user-api` 里写一个 Feign 接口，声明"我对外提供哪些 HTTP 接口、每个接口的路径和参数"。
**为什么**：这个接口就是"合同"，调用方依赖的就是它；Spring 会根据合同自动生成"能发 HTTP 请求"的实现。

文件：[UserFeignApi.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-api/src/main/java/com/quanxiaoha/xiaohashu/user/api/UserFeignApi.java)

```java
@FeignClient(name = ApiConstants.SERVICE_NAME)   // 贴"Feign 标签"：告诉 Spring 这是个远程接口，目标服务名叫 ApiConstants.SERVICE_NAME
public interface UserFeignApi {                 // Java 接口：只声明"调什么"，不写"怎么调"

    String PREFIX = "/user";                    // 路径前缀常量：下面所有接口的路径都从 /user 开头

    @PostMapping(value = PREFIX + "/register")                     // 声明这是一个 POST 请求，路径 = /user/register
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO registerUserReqDTO);  // 方法签名即契约：传入注册 DTO，返回统一结果，数据是用户 ID

    @PostMapping(value = PREFIX + "/findByPhone")                  // POST 请求，路径 = /user/findByPhone
    Response<FindUserByPhoneRspDTO> findByPhone(@RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO);  // 传入"手机号 DTO"，返回"用户信息 DTO"

    @PostMapping(value = PREFIX + "/password/update")              // POST 请求，路径 = /user/password/update
    Response<?> updatePassword(@RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO);  // 传入"新密码 DTO"，返回通用结果
}
```

> `@PostMapping` 声明了 HTTP 方法（POST）和路径；`@RequestBody` 表示参数放在请求体里、以 JSON 传输（后面步骤 4 的 Controller 也要用同样的注解接收）。`Response<T>` 是项目统一的返回包装类（含成功/失败标志 + 数据）。

### 步骤 2：定义入参/出参 DTO（放 api 模块，双方共享）

**做什么**：为每个接口定义入参（req）和出参（resp）的 DTO 类，放在 api 模块里。
**为什么**：这些 DTO 是"信封"——调用方装好数据传过来，提供方拆开信封取数据，**两边共用同一份类**，字段才不会对不上。

- `dto.req`（入参）：`RegisterUserReqDTO`（字段：phone 手机号）、`FindUserByPhoneReqDTO`（字段：phone）、`UpdateUserPasswordReqDTO`（字段：encodePassword 加密后的密码）
- `dto.resp`（出参）：`FindUserByPhoneRspDTO`（字段：id 用户 ID、password 加密密码）
- 另：api 模块里还有按用户 ID 查询的 `FindUserByIdReqDTO` / `FindUserByIdRspDTO`（供 `UserFeignApi.findById` 使用）。

> **避坑提示**：DTO 需可被 Jackson 序列化（加 `@Data` 等 Lombok 注解自动生成 getter/setter），并支持参数校验注解（如 `@PhoneNumber` 校验手机号格式）。因为 DTO 要在 HTTP 上以 JSON 传输，不能序列化的对象是传不过去的。

### 步骤 3：定义服务名常量

**做什么**：定义一个常量类，写死服务名 `xiaohashu-user`。
**为什么**：`@FeignClient(name = ...)` 要填服务名，且**必须与 Nacos 上注册的服务名一致**。用常量集中管理，避免到处手写字符串、写错一个就找不到服务。

文件：[ApiConstants.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-api/src/main/java/com/quanxiaoha/xiaohashu/user/constant/ApiConstants.java)

```java
public interface ApiConstants {          // 常量接口：里面的值都是固定不变的"配置"
    String SERVICE_NAME = "xiaohashu-user";   // 服务名：与 Nacos 上注册的服务名一致
}
```

### 步骤 4：api 模块 pom 引入 Feign 相关依赖

**做什么**：在 `xiaohashu-user-api/pom.xml` 里引入 OpenFeign 和负载均衡的依赖。
**为什么**：api 模块是"合同 + 发请求工具"的载体，Feign 注解（`@FeignClient`）和自动生成请求实现的机制都在这些依赖里。

文件：[xiaohashu-user-api/pom.xml](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-api/pom.xml)

```xml
<dependency>                                    <!-- 引入自研基础模块 xiaoha-common：提供 Response 等通用类 -->
    <groupId>com.quanxiaoha</groupId>
    <artifactId>xiaoha-common</artifactId>
</dependency>
<!-- OpenFeign -->
<dependency>                                    <!-- 引入 OpenFeign：有了它才能用 @FeignClient 注解、自动生成远程调用实现 -->
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<!-- 负载均衡 -->
<dependency>                                    <!-- 引入负载均衡：目标服务有多个实例时，自动挑一个来调，避免单点压力 -->
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

> 依赖无需写版本号：父 pom 的 `dependencyManagement` 已统一管理版本（详见第 6 节步骤 1 的说明）。

### 步骤 5：biz 模块的 Controller 实现同名接口

**做什么**：在 `xiaohashu-user-biz` 里写 `UserController`，用与 Feign 接口**一模一样**的路径和参数注解，接收 HTTP 请求。
**为什么**：Feign 接口只是"合同"，真正"接电话"的是 Controller——请求发到 `/user/findByPhone`，由这里接收并转给业务层。

文件：[UserController.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/java/com/quanxiaoha/xiaohashu/user/biz/controller/UserController.java)

```java
@RestController                             // 贴"接口类"标签：里面的方法可以被外界通过 HTTP 调用
@RequestMapping("/user")                    // 类级路径前缀：这个类里所有接口路径都从 /user 开头
public class UserController {               // 控制层：负责"接收请求 → 转给 Service → 返回结果"

    @PostMapping("/register")                        // POST 请求，路径 /user/register —— 必须与 UserFeignApi 声明的一致
    @ApiOperationLog(description = "用户注册")        // 项目自定义注解：自动记录该接口的操作日志
    public Response<Long> register(@Validated @RequestBody RegisterUserReqDTO registerUserReqDTO) {  // @Validated 自动校验入参；@RequestBody 把 JSON 请求体转成 DTO
        return userService.register(registerUserReqDTO);   // 转给 Service 层真正干活，结果原样返回
    }

    @PostMapping("/findByPhone")                     // POST 请求，路径 /user/findByPhone
    @ApiOperationLog(description = "手机号查询用户信息")
    public Response<FindUserByPhoneRspDTO> findByPhone(@Validated @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO) {  // 接收查询 DTO
        return userService.findByPhone(findUserByPhoneReqDTO);   // 转给 Service 层查询
    }

    @PostMapping("/password/update")                 // POST 请求，路径 /user/password/update
    public Response<?> updatePassword(@Validated @RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {  // 接收新密码 DTO
        return userService.updatePassword(updateUserPasswordReqDTO);  // 转给 Service 层更新密码
    }
}
```

> **避坑提示**：Feign 接口里 `@PostMapping` 的路径、`@RequestBody` 的位置，**必须和这里 Controller 一一对应**（详见第 7 节避坑 1、2）。

### 步骤 6：Service 层实现业务逻辑

**做什么**：`UserService` / `UserServiceImpl` 完成真正的业务逻辑。
**为什么**：Controller 只负责"接电话"，业务规则（怎么注册、怎么查、怎么改密码）放 Service 层，职责清晰。

`UserServiceImpl` 完成的实际业务包括：注册时生成工大社区号/默认昵称、分配默认角色、按手机号查用户、更新密码，最终**落库到 MySQL**（通过 MyBatis 的 Mapper 执行 SQL）。

### 步骤 7：biz 模块 pom 依赖 api 模块

**做什么**：在 `xiaohashu-user-biz/pom.xml` 里引入 `xiaohashu-user-api`。
**为什么**：biz 要实现 Feign 接口声明的那些 DTO 和接口，必须依赖 api 模块才能引用到这些类。

文件：[xiaohashu-user-biz/pom.xml](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/pom.xml)

```xml
<dependency>                        <!-- 引入自家 api 模块：拿到 Feign 接口和 DTO 类 -->
    <groupId>com.quanxiaoha</groupId>
    <artifactId>xiaohashu-user-api</artifactId>
</dependency>
```

### 步骤 8：biz 启动类开启 Feign（若它也调用其他服务）

**做什么**：在 `XiaohashuUserBizApplication` 启动类上加 `@EnableFeignClients`，开启 Feign 扫描。
**为什么**：`@EnableFeignClients` 是"总开关"——它扫描指定包下所有 `@FeignClient` 接口，并自动生成它们的实现对象。不开启，Feign 接口就无法注入使用。

文件：[XiaohashuUserBizApplication.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/java/com/quanxiaoha/xiaohashu/user/biz/XiaohashuUserBizApplication.java)

```java
@SpringBootApplication                              // Spring Boot 启动类标志
@MapperScan("com.quanxiaoha.xiaohashu.user.biz.domain.mapper")  // 扫描 MyBatis 的 Mapper 接口，自动注册
@EnableFeignClients(basePackages = "com.quanxiaoha.xiaohashu")  // 开启 Feign：扫描整个 com.quanxiaoha.xiaohashu 包下的 @FeignClient 接口
public class XiaohashuUserBizApplication {          // 启动类（程序入口）
    // main 方法里调用 SpringApplication.run(...) 启动应用
}
```

> 说明：user-biz 本身就依赖 `xiaohashu-oss-api`（要调 OSS 服务上传头像），所以这里同样需要开启 Feign——**只要这个服务会调用别的服务，就得开这个总开关**。

---

## 6. 服务调用方（auth 服务）5 个步骤

**这一节的主角是 auth 服务**：它要"打电话"给 user 服务拿数据。共 5 步，比提供方简单——因为提供方 8 步做完后，调用方拿来即用。

### 步骤 1：pom 引入目标服务的 api 模块

**做什么**：在 `xiaohashu-auth/pom.xml` 里引入 `xiaohashu-user-api`。
**为什么**：引入后就能直接使用 `UserFeignApi` 接口和那些 DTO 类——这就是 api 模块"共享合同"的价值。

文件：[xiaohashu-auth/pom.xml](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-auth/pom.xml)

```xml
<dependency>                        <!-- 引入 user 服务的 api 模块：拿到 Feign 接口 + DTO -->
    <groupId>com.quanxiaoha</groupId>
    <artifactId>xiaohashu-user-api</artifactId>
</dependency>
```

> 无需写版本号：父 pom 的 `dependencyManagement` 已用 `${revision}` 统一管理所有自研模块的版本，改版本只改一处。

### 步骤 2：启动类开启 Feign 扫描

**做什么**：在 `XiaohashuAuthApplication` 启动类上加 `@EnableFeignClients`。
**为什么**：同提供方步骤 8——只有开启扫描，`UserFeignApi` 才能被注入使用。

文件：[XiaohashuAuthApplication.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/XiaohashuAuthApplication.java)

```java
@SpringBootApplication                              // Spring Boot 启动类标志
@EnableFeignClients(basePackages = "com.quanxiaoha.xiaohashu")  // 开启 Feign：扫描整个 com.quanxiaoha.xiaohashu 包下的 Feign 接口
public class XiaohashuAuthApplication {             // auth 服务的启动类（程序入口）
    // main 方法里调用 SpringApplication.run(...) 启动应用
}
```

### 步骤 3：封装 RPC 层，隔离 Feign（推荐做法）

**做什么**：新建一个 `UserRpcService` 类，把"调用 Feign、判断成功与否、取数据"这些细节都收进去，业务层只调用它。
**为什么**：好处是**隔离**——业务层（`AuthServiceImpl`）只依赖 `UserRpcService`，不直接接触 Feign；后续接口契约变化，只需改这一处封装，其他代码不用动。

文件：[UserRpcService.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/rpc/UserRpcService.java)

```java
@Component                                    // 交给 Spring 管理（自动创建该类的对象，供别人注入）
public class UserRpcService {                 // RPC 封装类：把"怎么调 Feign"统一收在一个类里

    @Resource                                 // 自动注入 Feign 接口（Spring 已自动生成它的实现对象）
    private UserFeignApi userFeignApi;

    /** 用户注册 */
    public Long registerUser(String phone) {          // 入参：手机号；返回：用户 ID
        RegisterUserReqDTO registerUserReqDTO = new RegisterUserReqDTO();  // 造一个入参 DTO 信封
        registerUserReqDTO.setPhone(phone);           // 把手机号装进信封
        Response<Long> response = userFeignApi.registerUser(registerUserReqDTO);  // 调 Feign（底层发 HTTP 请求给 user 服务的 /user/register）
        if (!response.isSuccess()) {                  // 判断统一返回结果是否成功
            return null;                              // 不成功就返回 null，由上层决定怎么处理
        }
        return response.getData();                    // 成功则取出数据（用户 ID）
    }

    /** 根据手机号查询用户信息 */
    public FindUserByPhoneRspDTO findUserByPhone(String phone) {  // 入参：手机号；返回：用户信息 DTO
        FindUserByPhoneReqDTO findUserByPhoneReqDTO = new FindUserByPhoneReqDTO();  // 造入参 DTO
        findUserByPhoneReqDTO.setPhone(phone);        // 装手机号
        Response<FindUserByPhoneRspDTO> response = userFeignApi.findByPhone(findUserByPhoneReqDTO);  // 调 Feign：请求 user 服务的 /user/findByPhone
        if (!response.isSuccess()) {                  // 结果不成功
            return null;                              // 返回 null（调用方据此判断"该手机号未注册"）
        }
        return response.getData();                    // 成功则返回用户信息
    }

    /** 密码更新 */
    public void updatePassword(String encodePassword) {           // 入参：加密后的新密码
        UpdateUserPasswordReqDTO updateUserPasswordReqDTO = new UpdateUserPasswordReqDTO();  // 造入参 DTO
        updateUserPasswordReqDTO.setEncodePassword(encodePassword);  // 把密文密码装进信封
        userFeignApi.updatePassword(updateUserPasswordReqDTO);   // 调 Feign：请求 user 服务的 /user/password/update
    }
}
```

### 步骤 4：业务层调用（以登录为例）

**做什么**：在 `AuthServiceImpl.loginAndRegister` 里通过 `UserRpcService` 调用远程接口，实现登录逻辑。
**为什么**：业务层只需"要什么数据"（查用户），不关心数据从哪来、怎么传——这些都藏在 RPC 封装层里。

文件：[AuthServiceImpl.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/service/impl/AuthServiceImpl.java)

```java
case PASSWORD:                                          // 登录类型 = 密码登录
    String password = userLoginReqVO.getPassword();     // 从请求里取出用户输入的明文密码
    // RPC：调用用户服务，通过手机号查询用户（返回用户信息，含加密后的密码）
    FindUserByPhoneRspDTO findUserByPhoneRspDTO = userRpcService.findUserByPhone(phone);
    // 如果返回 null，说明该手机号没有注册过
    if (Objects.isNull(findUserByPhoneRspDTO)) {
        throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);  // 抛业务异常：用户不存在
    }
    String encodePassword = findUserByPhoneRspDTO.getPassword();  // 拿到数据库里存的"加密密码"
    boolean isPasswordCorrect = passwordEncoder.matches(password, encodePassword);  // 用加密器比对明文密码与密文
    if (!isPasswordCorrect) {                              // 比对失败 = 密码错误
        throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);  // 抛业务异常：手机号或密码错误
    }
    userId = findUserByPhoneRspDTO.getId();               // 登录成功，拿到用户 ID
    break;

// 验证码登录时（另一个 case 分支）：
// Long userId = userRpcService.registerUser(phone);   // 未注册则注册，已注册返回旧 ID
```

### 步骤 5：返回值统一使用 `Response<T>`，调用方校验结果

**做什么**：服务方统一返回 `Response.success(data)` / `Response.fail(...)`；调用方用 `response.isSuccess()` 判断成功与否，再取 `getData()`。
**为什么**：统一包装的好处——**错误处理和业务数据解耦**：调用方先看"成功还是失败"，再看"数据是什么"，不用靠猜状态码。

```java
Response<FindUserByPhoneRspDTO> response = userFeignApi.findByPhone(reqDTO);  // 调 Feign 拿到的统一结果
if (!response.isSuccess()) {   // 先判断调用是否成功（isSuccess() 返回 true/false）
    return null;               // 失败：返回 null，由上层业务处理
}
return response.getData();     // 成功：getData() 取出真正要的数据
```

---

## 7. 关键注意点：9 条避坑清单

| # | 注意点 | 说明 |
|---|--------|------|
| 1 | **接口路径必须一致** | Feign 接口里 `@PostMapping` 的路径必须与 biz 端 Controller 的路径、方法一一对应（少个斜杠、多个层级都会 404） |
| 2 | **必须加 `@RequestBody` / `@RequestPart`** | Feign 声明与 Controller 接收的参数注解要保持一致：JSON 传参用 `@RequestBody`，文件上传用 `@RequestPart` |
| 3 | **服务名一致** | `@FeignClient(name=...)` 要与 Nacos 注册的服务名一致（本项目统一用 `ApiConstants.SERVICE_NAME`），名字对不上就找不到服务 |
| 4 | **DTO 序列化** | 入参/出参对象需可被 Jackson 序列化，统一引入 `xiaoha-spring-boot-starter-jackson` 保证时间格式等全局规则一致 |
| 5 | **api 模块需带 Feign 依赖** | api 模块 pom 引入 `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer`（本项目 oss-api、user-api 均如此） |
| 6 | **版本统一管理** | 自研 api 模块登记进父 pom `dependencyManagement`（`${revision}`），调用方无需写版本号 |
| 7 | **启动类开启 Feign** | 调用方 `@EnableFeignClients(basePackages = "com.quanxiaoha.xiaohashu")` 扫描 Feign 接口，漏了会注入失败 |
| 8 | **userId 透传** | 框架 `FeignRequestInterceptor` 已自动把当前登录用户 ID 写入 Feign 请求头，下游通过上下文 Starter 获取，业务代码无需关心 |
| 9 | **构建顺序** | 修改 api 模块后需先 `mvn install` 该模块（`mvn install -pl xiaohashu-user/xiaohashu-user-api -am`），本地仓库更新后调用方才生效 |

> 避坑 8 补充：这个"透传"的实现是框架里的 `FeignRequestInterceptor`（实现了 Feign 的 `RequestInterceptor` 接口），它在每个 Feign 请求发出前，把 `LoginUserContextHolder` 里的用户 ID 塞进请求头；下游服务再通过上下文 Starter 取出来用。对业务代码完全透明。

---

## 8. 项目内已有实践对照表

| 调用方 | 被调方 | Feign 接口 | 封装 RPC 层 | 场景 |
|--------|--------|-----------|------------|------|
| auth | user | `UserFeignApi` | `UserRpcService` | 登录注册、按手机号查用户、更新密码 |
| user-biz | oss | `FileFeignApi` | `OssRpcService` | 上传头像、背景图 |

> **进阶场景**：`user-biz` 调用 `oss` 时，因为涉及文件上传（`multipart/form-data` 格式，不是普通 JSON），oss-api 额外提供了 `FeignFormConfig`（内部返回一个 `SpringFormEncoder` 编码器），并在 `@FeignClient` 中指定 `configuration = FeignFormConfig.class`。这是文件上传类远程调用的特殊处理方式，普通 JSON 接口不需要。

---

### 附：本项目相关文件路径速查

| 文件 | 作用 |
|------|------|
| `xiaohashu-user-api/.../api/UserFeignApi.java` | user 服务对外提供的 Feign 接口（合同） |
| `xiaohashu-user-api/.../constant/ApiConstants.java` | 服务名常量 |
| `xiaohashu-user-biz/.../controller/UserController.java` | user 服务接收请求的 Controller（实现） |
| `xiaohashu-auth/.../rpc/UserRpcService.java` | auth 服务封装 Feign 的 RPC 层 |
| `xiaohashu-auth/.../service/impl/AuthServiceImpl.java` | auth 业务层（登录逻辑，实际调用处） |
| `xiaoha-framework/.../interceptor/FeignRequestInterceptor.java` | 框架里的 Feign 请求拦截器（userId 透传） |
