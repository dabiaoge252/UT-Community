# xiaoha-framework 平台基础设施层：模块与文件详解

> 更新时间：2026-08-21
> 说明：本文基于 `xiaoha-framework` 当前源码逐文件整理，说明每个模块、每个文件的职责与核心业务流，帮助快速定位"这段代码是谁、干什么、怎么流转的"。
> 源码根目录：`d:\java\xiaohashu\xiaohashu\xiaoha-framework`

---

## 目录

1. [整体定位与模块依赖关系](#一整体定位与模块依赖关系)
2. [xiaoha-common 平台通用模块](#二xiaoha-common-平台通用模块)
3. [xiaoha-spring-boot-starter-biz-operationlog 接口日志组件](#三xiaoha-spring-boot-starter-biz-operationlog-接口日志组件)
4. [xiaoha-spring-boot-starter-biz-context 登录上下文组件](#四xiaoha-spring-boot-starter-biz-context-登录上下文组件)
5. [xiaoha-spring-boot-starter-jackson Jackson 序列化组件](#五xiaoha-spring-boot-starter-jackson-jackson-序列化组件)
6. [核心业务流程串讲](#六核心业务流程串讲)
7. [业务模块如何引入使用](#七业务模块如何引入使用)

---

## 一、整体定位与模块依赖关系

`xiaoha-framework` 是整个项目（小哈书）的**平台基础设施层**，职责是"封装常用功能，供各个业务线拿来即用"。它是一个 `packaging=pom` 的聚合工程，本身不写业务代码，只聚合 4 个子模块：

```
xiaoha-framework/  (聚合工程, packaging=pom)
├── xiaoha-common/                                    # 平台通用模块（基础底座）
├── xiaoha-spring-boot-starter-biz-operationlog/      # 接口操作日志组件（AOP 切面）
├── xiaoha-spring-boot-starter-biz-context/           # 登录用户上下文组件（ThreadLocal + Feign 透传）
└── xiaoha-spring-boot-starter-jackson/               # Jackson 全局序列化组件（时间格式化）
```

### 模块依赖关系图

```
                    xiaoha-common（被所有模块依赖）
                     ▲      ▲      ▲
                     │      │      │
   operationlog ─────┘      │      │
   biz-context ─────────────┘      │
   jackson ────────────────────────┘
```

三个 Starter 都依赖 `xiaoha-common`（因为要复用其中的 `Response`、`JsonUtils`、`DateConstants`、`GlobalConstants` 等），三者之间**互不依赖**。

### 在根工程 pom 中的注册方式

根工程 `xiaohashu/pom.xml` 通过 `<modules>` 显式注册了 4 个框架模块，并在 `<dependencyManagement>` 中统一管理版本（`${revision}`），业务模块引入时无需写版本号：

| 依赖坐标（groupId 均为 com.quanxiaoha） | 用途 |
|---|---|
| xiaoha-common | 通用模块，全局可引 |
| xiaoha-spring-boot-starter-biz-operationlog | 接口日志（AOP） |
| xiaoha-spring-boot-starter-biz-context | 登录上下文 |
| xiaoha-spring-boot-starter-jackson | Jackson 序列化 |

> 需要在 Spring 启动时自动创建的 Bean——有就是 Starter。
>
> 每个 Starter 都通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `@AutoConfiguration` 自动配置类，业务模块只需引入依赖即可自动生效（即插即用）。

---

## 二、xiaoha-common 平台通用模块

**定位**：平台的基础底座，提供常量、枚举、统一响应、异常、工具类、参数校验器，供框架内其他模块与所有业务模块共享。不含任何 Spring 装配逻辑，是纯 Java 工具集。

```
xiaoha-common/src/main/java/com/quanxiaoha/framework/common/
├── constant/        # 常量
│   ├── DateConstants.java
│   └── GlobalConstants.java
├── enums/           # 通用枚举
│   ├── DeletedEnum.java
│   └── StatusEnum.java
├── exception/       # 异常体系
│   ├── BaseExceptionInterface.java
│   └── BizException.java
├── response/        # 统一响应体
│   ├── Response.java
│   └── PageResponse.java
├── util/            # 工具类
│   ├── DateUtils.java
│   ├── JsonUtils.java
│   └── ParamUtils.java
└── validator/       # 参数校验
    ├── PhoneNumber.java
    └── PhoneNumberValidator.java
```

### 2.1 constant 常量包

| 文件 | 功能 | 关键内容 |
|------|------|----------|
| [GlobalConstants.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/constant/GlobalConstants.java) | 全局常量接口 | 定义 `USER_ID = "userId"`。它是**用户 ID 在请求头/ThreadLocal 中统一的键名**，网关写入 header、过滤器解析 header、Feign 透传全部用它，保证全局键名唯一一致。 |
| [DateConstants.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/constant/DateConstants.java) | 日期格式常量 | 4 个 `DateTimeFormatter`：`yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd`、`HH:mm:ss`、`yyyy-MM`，供 Jackson 组件与业务日期格式化统一使用。 |

### 2.2 enums 通用枚举包

| 文件 | 功能 |
|------|------|
| [DeletedEnum.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/enums/DeletedEnum.java) | 逻辑删除枚举：`YES(true)` / `NO(false)`，标识数据是否被逻辑删除。 |
| [StatusEnum.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/enums/StatusEnum.java) | 状态枚举：`ENABLE(0)` 启用 / `DISABLED(1)` 禁用。 |

### 2.3 exception 异常体系包

| 文件 | 功能 |
|------|------|
| [BaseExceptionInterface.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/exception/BaseExceptionInterface.java) | 异常定义接口，定义 `getErrorCode()`（异常码）与 `getErrorMessage()`（异常信息）。各业务服务的"错误码枚举"实现它即可统一异常出口。 |
| [BizException.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/exception/BizException.java) | 自定义业务异常（继承 RuntimeException），构造时传入 `BaseExceptionInterface` 提取错误码与信息。业务代码 `throw new BizException(...)` 抛出后，由全局异常处理器捕获并转成统一失败响应。 |

### 2.4 response 统一响应体包

| 文件 | 功能 |
|------|------|
| [Response.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/response/Response.java) | **所有接口的统一返回体**。字段：`success`（默认 true）、`message`、`errorCode`、`data`。提供静态工厂：`success()`、`success(data)`、`fail()`、`fail(msg)`、`fail(code, msg)`、`fail(BizException)`、`fail(BaseExceptionInterface)`。 |
| [PageResponse.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/response/PageResponse.java) | 分页响应体，继承 `Response<List<T>>`，额外带 `pageNo`、`totalCount`、`pageSize`、`totalPage`。静态方法：`success(list, pageNo, totalCount)`（默认每页 10 条）、`success(list, pageNo, totalCount, pageSize)`；工具方法 `getTotalPage(totalCount, pageSize)` 算总页数、`getOffset(pageNo, pageSize)` 算 SQL 分页 offset（页码 < 1 时按第 1 页处理）。 |

### 2.5 util 工具类包

| 文件 | 功能 | 关键方法 |
|------|------|----------|
| [JsonUtils.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/util/JsonUtils.java) | 静态 JSON 工具类（基于 Jackson）。默认忽略未知属性、忽略空 Bean、注册 `JavaTimeModule`。**关键设计：`init(ObjectMapper)` 允许被 Jackson Starter 注入统一配置的 ObjectMapper 覆盖默认实例**，保证全局序列化行为一致。 | `toJsonString(obj)` 对象转 JSON；`parseObject(json, clazz)` JSON 转对象；`parseMap(...)` 转 Map；`parseList(...)` 转 List。 |
| [DateUtils.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/util/DateUtils.java) | 日期工具类 | `localDateTime2Timestamp(LocalDateTime)` 按 UTC 转毫秒时间戳。 |
| [ParamUtils.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/util/ParamUtils.java) | 参数校验工具类 | `checkNickname(nickname)` 昵称 2~24 位且不含特殊字符；`checkXiaohashuId(id)` 小哈书号 6~15 位且仅含字母数字下划线；`checkLength(str, length)` 通用长度校验。 |

### 2.6 validator 参数校验包（JSR-303 自定义校验器）

| 文件 | 功能 |
|------|------|
| [PhoneNumber.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/validator/PhoneNumber.java) | 自定义注解 `@PhoneNumber`，标注在字段/参数上触发手机号校验。默认错误消息"手机号格式不正确, 需为 11 位数字"。通过 `@Constraint(validatedBy = PhoneNumberValidator.class)` 绑定校验器。 |
| [PhoneNumberValidator.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-common/src/main/java/com/quanxiaoha/framework/common/validator/PhoneNumberValidator.java) | 校验器实现 `ConstraintValidator<PhoneNumber, String>`，`isValid` 用正则 `\d{11}` 判断是否为 11 位纯数字。业务 DTO 上写 `@PhoneNumber` 即可自动校验手机号。 |

---

## 三、xiaoha-spring-boot-starter-biz-operationlog 接口日志组件

**定位**：接口操作日志组件。业务 Controller 的方法上打 `@ApiOperationLog(description = "...")` 注解，AOP 切面自动打印"请求开始 / 请求结束 + 入参、出参、耗时"，无需在业务代码里写日志。

```
xiaoha-spring-boot-starter-biz-operationlog/src/main/
├── java/com/quanxiaoha/framework/biz/operationlog/
│   ├── aspect/
│   │   ├── ApiOperationLog.java        # 自定义注解
│   │   └── ApiOperationLogAspect.java  # 环绕切面
│   └── config/
│       └── ApiOperationLogAutoConfiguration.java  # 自动配置类
└── resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

| 文件 | 功能 | 核心逻辑 |
|------|------|----------|
| [ApiOperationLog.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-operationlog/src/main/java/com/quanxiaoha/framework/biz/operationlog/aspect/ApiOperationLog.java) | 自定义方法级注解，属性 `description()` 描述接口功能（如"用户注册"）。`@Retention(RUNTIME)` + `@Target(METHOD)`。 | 仅作为切点标记，不含逻辑。 |
| [ApiOperationLogAspect.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-operationlog/src/main/java/com/quanxiaoha/framework/biz/operationlog/aspect/ApiOperationLogAspect.java) | 环绕切面，`@Pointcut("@annotation(ApiOperationLog)")` 把所有带注解的方法设为切点。 | `doAround`：① 记录开始时间；② 取类名、方法名、入参并转 JSON；③ 从方法注解取 description；④ 打印"请求开始"日志；⑤ `joinPoint.proceed()` 执行业务方法；⑥ 计算耗时，用 `JsonUtils.toJsonString(result)` 打印出参，打印"请求结束"日志。 |
| [ApiOperationLogAutoConfiguration.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-operationlog/src/main/java/com/quanxiaoha/framework/biz/operationlog/config/ApiOperationLogAutoConfiguration.java) | `@AutoConfiguration` 自动配置类，注册 `ApiOperationLogAspect` Bean。 | 引入依赖即自动装配，无需手动注册。 |
| [AutoConfiguration.imports](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-operationlog/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | Spring Boot 自动配置注册文件，内容为 `ApiOperationLogAutoConfiguration` 全限定名。 | 是 Starter 能被自动扫描装配的关键。 |

**依赖**：`xiaoha-common`（用 JsonUtils）+ `spring-boot-starter-aop`。

---

## 四、xiaoha-spring-boot-starter-biz-context 登录上下文组件

**定位**：登录用户上下文组件。解决两个问题：

1. **同服务内**：从请求头 `userId` 取出当前登录用户，存入 `ThreadLocal`（实际用阿里 TTL，支持线程池透传），业务代码随时 `LoginUserContextHolder.getUserId()` 取用；
2. **跨服务（Feign）**：调用下游 Feign 接口时自动把当前 `userId` 写进请求头透传，保证下游也能拿到登录用户。

```
xiaoha-spring-boot-starter-biz-context/src/main/
├── java/com/quanxiaoha/framework/biz/context/
│   ├── config/
│   │   ├── ContextAutoConfiguration.java        # 注册过滤器
│   │   └── FeignContextAutoConfiguration.java   # 注册 Feign 拦截器
│   ├── filter/
│   │   └── HeaderUserId2ContextFilter.java      # 请求头 → ThreadLocal
│   ├── holder/
│   │   └── LoginUserContextHolder.java          # ThreadLocal 存取器
│   └── interceptor/
│       └── FeignRequestInterceptor.java         # ThreadLocal → 请求头
└── resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

| 文件 | 功能 | 核心逻辑 |
|------|------|----------|
| [LoginUserContextHolder.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-context/src/main/java/com/quanxiaoha/framework/biz/context/holder/LoginUserContextHolder.java) | 登录用户上下文存取器。底层是 `TransmittableThreadLocal<Map<String, Object>>`（阿⾥ TTL），用 Map 是为了后续可扩展存储更多字段。 | `setUserId(value)` 写入 `userId` 键；`getUserId()` 取出并转 `Long`（空则返回 null）；`remove()` 清空 ThreadLocal 防止内存泄漏。TTL 保证线程池中线程也能传递用户信息。 |
| [HeaderUserId2ContextFilter.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-context/src/main/java/com/quanxiaoha/framework/biz/context/filter/HeaderUserId2ContextFilter.java) | 请求过滤器（继承 `OncePerRequestFilter`，每个请求只过滤一次）。 | 从请求头读 `userId`：为空直接放行；非空则 `LoginUserContextHolder.setUserId(userId)` 写入 ThreadLocal，`finally` 中务必 `remove()` 清理。 |
| [FeignRequestInterceptor.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-context/src/main/java/com/quanxiaoha/framework/biz/context/interceptor/FeignRequestInterceptor.java) | Feign 请求拦截器，实现 `feign.RequestInterceptor`。 | 发起 Feign 调用前，从 `LoginUserContextHolder.getUserId()` 取当前用户，非空则写入请求头 `userId`，实现用户身份跨服务透传。 |
| [ContextAutoConfiguration.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-context/src/main/java/com/quanxiaoha/framework/biz/context/config/ContextAutoConfiguration.java) | 自动配置类，用 `FilterRegistrationBean` 注册 `HeaderUserId2ContextFilter` 过滤器。 | 引入依赖即自动注册过滤器。 |
| [FeignContextAutoConfiguration.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-context/src/main/java/com/quanxiaoha/framework/biz/context/config/FeignContextAutoConfiguration.java) | 自动配置类，注册 `FeignRequestInterceptor` Bean。 | 引入依赖即自动注册 Feign 拦截器。 |
| [AutoConfiguration.imports](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-context/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | 注册两个自动配置类（Context、FeignContext）。 | — |

**依赖**：`xiaoha-common`、`transmittable-thread-local`、`spring-boot-starter`、`spring-web`、`jakarta.servlet-api`、`spring-cloud-alibaba-commons`、`feign-core`。

---

## 五、xiaoha-spring-boot-starter-jackson Jackson 序列化组件

**定位**：全局统一 Jackson 序列化配置，重点解决 **Java 8 时间类型（LocalDateTime/LocalDate/LocalTime/YearMonth）的 JSON 序列化格式**问题，并让 `JsonUtils` 复用同一份 ObjectMapper。

```
xiaoha-spring-boot-starter-jackson/src/main/
├── java/com/quanxiaoha/framework/jackson/config/
│   └── JacksonAutoConfiguration.java
└── resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

| 文件 | 功能 | 核心逻辑 |
|------|------|----------|
| [JacksonAutoConfiguration.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-jackson/src/main/java/com/quanxiaoha/framework/jackson/config/JacksonAutoConfiguration.java) | `@AutoConfiguration`，定义 `ObjectMapper` Bean。 | ① 忽略未知属性、忽略空 Bean；② 时区设为 `Asia/Shanghai`；③ 注册 `JavaTimeModule`，为 `LocalDateTime`（yyyy-MM-dd HH:mm:ss）、`LocalDate`（yyyy-MM-dd）、`LocalTime`（HH:mm:ss）、`YearMonth`（yyyy-MM）分别注册序列化器与反序列化器；④ 调用 `JsonUtils.init(objectMapper)` 让工具类与 Spring 共用同一个 Mapper。 |
| [AutoConfiguration.imports](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-jackson/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | 注册 JacksonAutoConfiguration。 | — |

**依赖**：`xiaoha-common`（用 DateConstants、JsonUtils）+ `spring-boot-autoconfigure`。

---

## 六、核心业务流程串讲

### 流程一：登录用户身份全链路透传（网关 → 业务服务 → Feign 下游）

这是本项目最核心的链路，`biz-context` + `xiaoha-common.GlobalConstants` + 网关过滤器共同完成：

```
用户请求
   │
   ▼
① xiaohashu-gateway / AddUserId2HeaderFilter（GlobalFilter，@Order(-90)）
   │  Sa-Token 校验登录，StpUtil.getLoginIdAsLong() 拿到 userId
   │  exchange.mutate() 把 userId 写入请求头 header("userId", ...)
   ▼
② 下游业务服务（如 user/note 服务）
   │  HeaderUserId2ContextFilter（OncePerRequestFilter，自动装配注册）
   │    读取请求头 userId → 非空则 LoginUserContextHolder.setUserId(userId)
   │    finally 中 remove() 清理，防止 ThreadLocal 内存泄漏
   ▼
③ 业务 ServiceImpl 中
   │  Long userId = LoginUserContextHolder.getUserId()   // 直接取当前登录用户
   │  注：底层是 TransmittableThreadLocal，异步线程池中也能取到
   ▼
④ 需要调用其他服务（如 user 服务调 note 服务的 Feign）
   │  FeignRequestInterceptor（自动装配注册）
   │    发起 Feign 请求前从 LoginUserContextHolder 取 userId
   │    非空则写入本次请求头 userId，透传给下游
   ▼
⑤ 下游服务再次走②③，链路闭环
```

**要点**：所有环节都用 `GlobalConstants.USER_ID`（即字符串 `"userId"`）作为键，保证透传键名一致。

### 流程二：接口操作日志（@ApiOperationLog + AOP）

```
业务 Controller 方法上加 @ApiOperationLog(description = "用户注册")
   │
   ▼
ApiOperationLogAspect.doAround()（环绕通知）
   │  ① 记录 startTime
   │  ② 取类名/方法名，入参数组转 JSON 字符串
   │  ③ 反射读取注解 description
   │  ④ log.info 打印 "请求开始: [描述], 入参: xxx"
   ▼
joinPoint.proceed()   ← 执行业务方法（正常返回或抛异常）
   │
   ▼
⑤ 计算耗时，JsonUtils.toJsonString(result) 打印出参
   │  log.info 打印 "请求结束: [描述], 耗时: xxms, 出参: xxx"
   ▼
返回业务方法结果（不影响正常逻辑）
```

### 流程三：统一响应 + 业务异常

```
业务逻辑
   ├─ 成功 → return Response.success(data) 或 PageResponse.success(list, pageNo, total)
   └─ 失败 → throw new BizException(某业务ErrorCode枚举实现 BaseExceptionInterface)
                │
                ▼
        全局异常处理器捕获 BizException
                │
                ▼
        Response.fail(errorCode, errorMessage) 返回前端
```

### 流程四：时间类型 JSON 序列化

```
业务返回对象含 LocalDateTime 等字段
   │
   ▼
Spring MVC 使用 JacksonAutoConfiguration 提供的 ObjectMapper
   │  JavaTimeModule 按 DateConstants 中定义的格式序列化
   │  LocalDateTime → "yyyy-MM-dd HH:mm:ss"；LocalDate → "yyyy-MM-dd" 等
   │  时区 Asia/Shanghai；null 字段是否返回可配置（默认返回）
   ▼
JsonUtils.init(objectMapper) 同步给 JsonUtils，代码内 toJsonString 与接口出参格式完全一致
```

---

## 七、业务模块如何引入使用

以业务模块（如 `xiaohashu-user-biz`）为例，只需在 pom.xml 引入所需 Starter：

```xml
<!-- 接口操作日志 -->
<dependency>
    <groupId>com.quanxiaoha</groupId>
    <artifactId>xiaoha-spring-boot-starter-biz-operationlog</artifactId>
</dependency>

<!-- 登录用户上下文 -->
<dependency>
    <groupId>com.quanxiaoha</groupId>
    <artifactId>xiaoha-spring-boot-starter-biz-context</artifactId>
</dependency>

<!-- Jackson 统一序列化 -->
<dependency>
    <groupId>com.quanxiaoha</groupId>
    <artifactId>xiaoha-spring-boot-starter-jackson</artifactId>
</dependency>

<!-- 通用模块（常量/枚举/响应体/工具类，基本都会用到） -->
<dependency>
    <groupId>com.quanxiaoha</groupId>
    <artifactId>xiaoha-common</artifactId>
</dependency>
```

版本号无需写，由根工程 `<dependencyManagement>` 统一管理（`${revision}`）。

**使用姿势小结**：

| 场景 | 怎么做 |
|------|--------|
| 接口返回统一格式 | Controller 返回 `Response<T>` / `PageResponse<T>` |
| 接口打日志 | 方法上加 `@ApiOperationLog(description = "...")` |
| 获取当前登录用户 | `LoginUserContextHolder.getUserId()` |
| 对象/JSON 互转 | `JsonUtils.toJsonString / parseObject / parseList` |
| 手机号校验 | DTO 字段加 `@PhoneNumber` |
| 时间格式化 | 引入 jackson Starter 后自动生效 |
