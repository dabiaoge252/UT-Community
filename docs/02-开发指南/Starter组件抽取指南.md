# 将通用工具类抽取成 Starter 组件（零基础版，结合本项目）

> 更新时间：2026-08-05
> 适用人群：**没学过 Spring Boot Starter / 自动装配**的开发者。从"Starter 是什么"讲起，逐步深入到本项目 `xiaoha-framework` 基础设施层，所有概念配大白话类比 + 逐行注释示例。
> 适用范围：适用于 xiaohashu 多模块项目（`xiaoha-framework` 基础设施层），将通用功能（工具类、常量、切面、过滤器等）封装为独立的 `Spring Boot Starter`，供各业务模块"拿来即用"。

## 目录

1. [先看懂的：Spring Boot Starter 是什么](#1-先看懂的spring-boot-starter-是什么)
2. [为什么要抽 Starter（不抽会怎样）](#2-为什么要抽-starter不抽会怎样)
3. [核心名词大白话（遇到不认识的先查这里）](#3-核心名词大白话遇到不认识的先查这里)
4. [自动装配原理（一张图看懂）](#4-自动装配原理一张图看懂)
5. [项目里的 3 个 Starter 实例](#5-项目里的-3-个-starter-实例)
6. [完整步骤：从零抽取一个 Starter（8 步）](#6-完整步骤从零抽取一个-starter8-步)
7. [关键点回顾（项目实战踩坑总结）](#7-关键点回顾项目实战踩坑总结)

---

## 1. 先看懂的：Spring Boot Starter 是什么

**Spring Boot Starter（启动器）** 就是一个"**引入依赖即自动生效**"的组件包：你在 `pom.xml` 里加上一行依赖，Spring Boot 启动时**自动**把这个组件里预先写好的 Bean（对象）、切面、过滤器等全部创建好，**一行代码都不用写**。

### 生活类比：买家电"即插即用"

- 以前（不抽 Starter）：你买了一台冰箱，还得自己买电线、自己接电、自己装压缩机（每个服务都得重复写一遍通用代码）。
- 现在（Starter）：你买的是"**插上电就能用**"的智能家电——插头一插（引入依赖），电冰箱自动开始制冷（通用 Bean 自动创建），你只管用。

### 典型 Starter 长什么样

Spring 官方自己就有一堆 Starter，比如：

- `spring-boot-starter-web`：引入后 Tomcat 自动启动、`@RestController` 自动生效；
- `spring-boot-starter-mybatis`：引入后数据库连接池、Mapper 扫描自动配好。

本项目把"**自己写的通用能力**"也做成 Starter，放在 `xiaoha-framework` 里，谁需要谁引入。

---

## 2. 为什么要抽 Starter（不抽会怎样）

### 2.1 场景：接口日志功能（operationlog）

本项目每个业务服务（auth、note、user...）都要记录"接口调用日志"——谁在什么时候调了哪个接口。实现方式是一个**切面**（AOP）：接口一被调用，自动打印入参、出参、耗时。

- **不抽 Starter**：每个服务都复制一份切面代码。改一个日志格式，8 个服务各改一遍，还容易改漏、改出差异。这就是"复制粘贴式复用"。
- **抽成 Starter**：切面只写一份，放进 `xiaoha-spring-boot-starter-biz-operationlog`。任何服务引入依赖，切面自动生效。改格式只改一处，全部服务生效。

### 2.2 Starter 的三大价值

| 价值 | 大白话 | 本项目例子 |
|------|--------|-----------|
| **复用** | 通用能力写一次，N 个服务共享 | 3 个 Starter 被多个业务模块引入 |
| **统一** | 能力只有一份实现，行为永远一致 | 日志格式、JSON 时间格式全局统一 |
| **免配置** | 引入即用，使用者不用写任何装配代码 | 引入 jar 包，切面/过滤器自动注册 |

### 2.3 生活类比：小区快递驿站

每个业务服务就像小区里的每一栋楼。快递驿站（Starter）统一负责收发件（通用能力）：
- 不开驿站 = 每栋楼自己雇人收发件（每个服务自己复制一份代码），乱且重复；
- 开了驿站 = 一栋楼管好，全小区受益（一个 Starter，所有服务引入即用）。

---

## 3. 核心名词大白话（遇到不认识的先查这里）

| 名词 | 大白话解释 | 生活类比 |
|------|-----------|---------|
| **Starter（启动器）** | "引入依赖即自动生效"的组件包，里面打包好了自动配置类 + 依赖 + 代码 | 即插即用的智能家电 |
| **Maven 模块（module）** | 多模块项目里的一个子工程，有自己的 pom.xml，可以被单独打包成 jar | 大楼里的一户人家，独立门牌 |
| **聚合 POM（父工程）** | 只负责"点名"（声明有哪些子模块）和"统一版本"的 pom 文件，本身不写代码 | 小区的物业：只管登记哪几栋楼属于这个小区 |
| **jar 包** | Java 代码编译后的打包产物，别人引依赖拿到的就是它 | 打包好的"电器成品" |
| **全限定名（全类名）** | 类的完整名字：包名 + 类名（如 `com.quanxiaoha.framework.biz.operationlog.config.ApiOperationLogAutoConfiguration`），全项目唯一 | 身份证号：全国唯一，不能写错一个数字 |
| **自动配置类（AutoConfiguration）** | 标了 `@AutoConfiguration` 的类，Spring Boot 启动时**自动执行**里面的 `@Bean` 方法，把通用对象造好放进容器 | 家电出厂时预装好的"自动启机程序" |
| **@AutoConfiguration** | Spring Boot 2.7+ 推荐用的注解，声明"这是一个自动配置类" | 外包装上印的"自动启机"标识 |
| **@Bean** | 标注在方法上：这个方法造出来的对象交给 Spring 容器管理 | 生产线登记入库的产品 |
| **AutoConfiguration.imports 文件** | 放在 `META-INF/spring/` 下的清单文件，**一行写一个自动配置类的全限定名**，Spring 靠它找到自动配置类 | 家电的"出厂清单"：写了这台机器带哪些功能 |
| **全限定名（imports 里）** | 必须和类的真实包名**一字不差**，否则 Spring 找不到类 | 清单上写的型号和机器实际型号必须一致 |
| **dependencyManagement** | 统一声明"各依赖用什么版本"，子模块引用时不用写版本号 | 物业统一采购清单：型号都定好了，住户不用自己选 |
| **条件注解（@ConditionalOnXxx）** | 满足条件才让自动配置生效（比如"classpath 有某类才生效"） | 家里有冰箱插口才给冰箱通电 |
| **切面（Aspect）** | AOP 技术：在不改业务代码的前提下，给方法"套一层"统一逻辑（如打日志） | 给每户门口装一个监控摄像头 |
| **@ConfigurationProperties** | 把配置文件里一组 `xxx.yyy` 的值，批量绑定到对象属性上 | 把整张配置表抄到一张登记卡上 |

---

## 4. 自动装配原理（一张图看懂）

**大白话**：Spring Boot 启动时，会自动去每个 jar 包的 `META-INF/spring/` 目录里找那个 `AutoConfiguration.imports` 文件，读到几个全限定名，就去实例化几个自动配置类。

```text
业务服务启动（引了 starter 依赖）
        ↓
Spring Boot 扫每个依赖 jar 包里的清单文件
  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
        ↓
读到一行全限定名，例如：
  com.quanxiaoha.framework.biz.operationlog.config.ApiOperationLogAutoConfiguration
        ↓
自动创建这个自动配置类的对象（类上标了 @AutoConfiguration）
        ↓
执行类里的 @Bean 方法 → 把切面 / 过滤器 / 工具对象装进 Spring 容器
        ↓
业务代码里直接 @Resource 注入使用（或注解自动触发，如 @ApiOperationLog）
```

### 为什么需要 `imports` 文件？（关键）

Spring Boot **默认不知道**你的 jar 里有哪些自动配置类，全靠这份清单文件"指路"。所以：

1. **清单文件必须存在**，且名字必须是 `org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Spring Boot 约定好的固定文件名）；
2. **清单里必须写全限定名**，且和类实际包名**一字不差**（这就是本项目踩过的坑，详见第 7 节）；
3. 文件放在 `src/main/resources/META-INF/spring/` 下，打包后自动进 jar。

> 一句话：**@AutoConfiguration 是"机器的自动启机程序"，imports 文件是"出厂清单"**——清单丢了（文件缺失）或写错型号（全限定名对不上），机器（自动配置）就不会启动。

---

## 5. 项目里的 3 个 Starter 实例

**大白话**：本项目 `xiaoha-framework` 里一共有 3 个 Starter + 1 个通用模块，逐个看它们的目录和"清单内容"。

### 5.1 目录结构总览（项目真实布局）

```
xiaoha-framework/
├── xiaoha-common/                                # 通用模块（工具类、常量、枚举、异常）——不是 Starter，纯被依赖的"零件库"
├── xiaoha-spring-boot-starter-biz-operationlog/ # 接口日志组件（示例）
├── xiaoha-spring-boot-starter-biz-context/      # 上下文组件（示例）
└── xiaoha-spring-boot-starter-jackson/          # Jackson 序列化组件（示例）
```

### 5.2 三个 Starter 各自装了什么

| Starter 模块 | 包名（全限定名前缀） | 自动配置类（imports 清单内容） | 提供的通用能力 |
|--------------|--------------------|------------------------------|---------------|
| `xiaoha-spring-boot-starter-biz-operationlog` | `com.quanxiaoha.framework.biz.operationlog` | `com.quanxiaoha.framework.biz.operationlog.config.ApiOperationLogAutoConfiguration` | 接口操作日志切面（`@ApiOperationLog` 注解 + `ApiOperationLogAspect` 切面） |
| `xiaoha-spring-boot-starter-biz-context` | `com.quanxiaoha.framework.biz.context` | `com.quanxiaoha.framework.biz.context.config.ContextAutoConfiguration`、`...config.FeignContextAutoConfiguration`（一行一个，共 2 行） | 用户上下文传递：`LoginUserContextHolder`（存当前登录用户）、`HeaderUserId2ContextFilter`（过滤器）、`FeignRequestInterceptor`（Feign 调用时透传用户 ID） |
| `xiaoha-spring-boot-starter-jackson` | `com.quanxiaoha.framework.jackson` | `com.quanxiaoha.framework.jackson.config.JacksonAutoConfiguration` | 统一 JSON 序列化：`ObjectMapper`（LocalDateTime 等时间格式统一 `yyyy-MM-dd HH:mm:ss`、时区 Asia/Shanghai、忽略未知字段） |

### 5.3 三个 imports 清单文件的真实内容

**① `xiaoha-spring-boot-starter-biz-operationlog`** 的 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```text
# 清单文件：一行一个自动配置类的全限定名（注意：格式是纯文本，无任何符号包裹）
com.quanxiaoha.framework.biz.operationlog.config.ApiOperationLogAutoConfiguration
```

**② `xiaoha-spring-boot-starter-biz-context`** 的同一路径清单文件：

```text
# 一个模块可以有多个自动配置类，分两行写
com.quanxiaoha.framework.biz.context.config.ContextAutoConfiguration
com.quanxiaoha.framework.biz.context.config.FeignContextAutoConfiguration
```

**③ `xiaoha-spring-boot-starter-jackson`** 的同一路径清单文件：

```text
com.quanxiaoha.framework.jackson.config.JacksonAutoConfiguration
```

### 5.4 看一眼真实的自动配置类（jackson 为例）

`JacksonAutoConfiguration`（[项目真实代码](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-jackson/src/main/java/com/quanxiaoha/framework/jackson/config/JacksonAutoConfiguration.java)）：

```java
@AutoConfiguration   // 声明：这是一个自动配置类，Spring Boot 启动时自动执行
public class JacksonAutoConfiguration {

    @Bean                                            // 把下面方法造出的 ObjectMapper 放进容器
    public ObjectMapper objectMapper() {
        // 造一个 JSON 转换工具对象（序列化/反序列化的核心）
        ObjectMapper objectMapper = new ObjectMapper();

        // 遇到未知字段不要报错（反序列化时，JSON 里多了字段直接忽略）
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 空对象序列化时不要报错
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // 设置时区为上海（保证时间转换不带 UTC 偏移）
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // JavaTimeModule：注册 java.time（LocalDateTime/LocalDate...）的序列化规则
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // 例：LocalDateTime 统一序列化成 "yyyy-MM-dd HH:mm:ss" 格式
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateConstants.DATE_FORMAT_Y_M_D_H_M_S));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateConstants.DATE_FORMAT_Y_M_D_H_M_S));
        // ...（LocalDate / LocalTime / YearMonth 同理，略）

        objectMapper.registerModule(javaTimeModule); // 把规则注册进 ObjectMapper

        JsonUtils.init(objectMapper);                // 同步初始化本项目 JsonUtils 工具类里的 ObjectMapper

        return objectMapper;                         // 交回容器，全局注入都用它
    }
}
```

> 大白话：**任何服务引入这个 jar，自动获得一套"时间格式统一、时区正确、能容忍未知字段"的 JSON 工具**，不用每个服务自己配一遍。
>
> 注意：本项目 `biz-operationlog`、`biz-context` 两个模块的源码在部分分支里只有 `resources`（`imports` 清单文件在 `src/main/resources/META-INF/spring/` 下），编译产物里的自动配置类为 `...config.ApiOperationLogAutoConfiguration` / `...config.ContextAutoConfiguration` / `...config.FeignContextAutoConfiguration`，结构同上。

---

## 6. 完整步骤：从零抽取一个 Starter（8 步）

> 以下 8 步是**抽取一个全新 Starter 的完整流程**，每一步先讲"做什么"，再讲"为什么"。以要新建 `xiaoha-spring-boot-starter-xxx` 为例（xxx 替换为你的功能名）。

### 步骤一：创建独立 Maven 模块

**做什么**：在 `xiaoha-framework` 下新建模块目录，创建 `pom.xml`。
**为什么**：Starter 必须是一个独立的 Maven 模块，才能被单独打包成 jar、被其他服务引用。

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承父工程，统一版本号 ${revision}（不写死版本，跟随父工程走） -->
    <parent>
        <groupId>com.quanxiaoha</groupId>
        <artifactId>xiaoha-framework</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>xiaoha-spring-boot-starter-xxx</artifactId> <!-- 本模块的名字，被别人引入时靠它 -->
    <packaging>jar</packaging>                              <!-- 打包成 jar（供依赖） -->

    <dependencies>
        <!-- 依赖通用模块（工具类、常量等，避免重复造轮子） -->
        <dependency>
            <groupId>com.quanxiaoha</groupId>
            <artifactId>xiaoha-common</artifactId>
        </dependency>
        <!-- 按需引入 Spring Boot / 其他依赖（如切面需要 aop） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 步骤二：注册模块到聚合 POM

> ⚠️ **容易遗漏**：新模块必须在聚合 POM 中注册，否则不会被 Maven/IDEA 纳入构建，jar 永远生成不出来。
> 大白话：新住户搬进来，必须先在物业（聚合 POM）登记门牌号，不然物业永远不知道这户人家存在，更不会去"收水电费"（构建它）。

#### 1. 根聚合 POM（`xiaohashu/pom.xml`）

```xml
<modules>
    <module>xiaohashu-auth</module>
    <module>xiaoha-framework</module>
    <module>xiaoha-framework/xiaoha-spring-boot-starter-jackson</module>
    <module>xiaohashu-gateway</module>
    <module>xiaoha-framework/xiaoha-spring-boot-starter-biz-context</module>
</modules>
```

> 说明：这是根 POM 的 `<modules>` 片段（**示例**）。注意本项目里 `jackson` 和 `biz-context` 两个 Starter 是**直接挂在根 POM** 下的，而 `operationlog` 挂在 `xiaoha-framework` 下——两种挂法都行，只要确保模块被某个聚合 POM 点名即可。

#### 2. 父聚合 POM（`xiaoha-framework/pom.xml`）

```xml
<modules>
    <module>xiaoha-common</module>
    <module>xiaoha-spring-boot-starter-biz-operationlog</module>
    <module>xiaoha-spring-boot-starter-biz-context</module>
</modules>
```

> 大白话：模块可以挂"根 POM"也可以挂"父 POM"，但**必须二选一被登记**。本项目三个 Starter 的登记情况：
> - `jackson`、`biz-context` → 根 POM `xiaohashu/pom.xml`
> - `operationlog` → 父 POM `xiaoha-framework/pom.xml`

### 步骤三：按职责分包

**做什么**：按功能把类分到不同的包（目录）下。
**为什么**：结构清晰、引用明确，也方便别人快速定位。

```
src/main/java/com/quanxiaoha/framework/xxx/
├── config/     # 自动配置类（XxxAutoConfiguration）
├── constant/   # 常量
├── util/       # 工具类
├── enums/      # 枚举
├── aspect/     # 切面（如操作日志切面）
├── filter/     # 过滤器
└── holder/     # 上下文持有者（如 LoginUserContextHolder）
```

> ⚠️ **包名规范**：建议统一为 `com.quanxiaoha.framework.biz.xxx` 风格
> （例如 operationlog 模块为 `com.quanxiaoha.framework.biz.operationlog`），
> 保证各模块风格一致、引用清晰。
> 大白话：包名 = 类的"身份证地址"。本项目 3 个 Starter 的地址前缀分别是 `framework.biz.operationlog`、`framework.biz.context`、`framework.jackson`。

### 步骤四：编写自动配置类

**做什么**：写一个标了 `@AutoConfiguration` 的类，在里面用 `@Bean` 把通用对象造出来。
**为什么**：Spring Boot 启动时会自动执行这个类，把对象装进容器——这就是"引入即生效"的来源。

```java
package com.quanxiaoha.framework.xxx.config;   // 包名：注意要和步骤五的清单文件一字不差

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration          // 声明为 Spring Boot 自动配置类（启动时自动执行）
public class XxxAutoConfiguration {

    @Bean              // 把过滤器注册进容器（FilterRegistrationBean 是 Spring 的"过滤器登记表"）
    public FilterRegistrationBean<XxxFilter> xxxFilter() {
        // 把过滤器对象登记进表里，Spring 启动时会自动挂到请求链上
        FilterRegistrationBean<XxxFilter> bean = new FilterRegistrationBean<>(new XxxFilter());
        return bean;
    }

    // 其他 Bean 定义...
}
```

常用条件注解（按需使用，**大白话**：满足条件才自动生效）：

| 注解 | 作用（大白话） |
|------|------|
| `@ConditionalOnClass` | classpath 存在某类时才生效（比如引了某个依赖才启用） |
| `@ConditionalOnBean` / `@ConditionalOnMissingBean` | 容器存在/不存在某 Bean 时生效（避免重复创建） |
| `@ConditionalOnProperty` | 配置项满足条件时生效（比如 `xxx.enabled=true` 才启用） |

### 步骤五：注册自动配置类

**做什么**：在 `src/main/resources/META-INF/spring/` 下创建清单文件，写自动配置类的全限定名。
**为什么**：Spring Boot 靠这个文件"按图索骥"找到你的自动配置类（原理见第 4 节）。

```
org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

内容为该自动配置类的**全限定名**：

```
com.quanxiaoha.framework.xxx.config.XxxAutoConfiguration
```

> ⚠️ **容易踩坑**：全限定名必须与实际类包名**一字不差**。
> 若 `imports` 中的类名与类实际包名不一致，Spring Boot 启动时会抛
> `ClassNotFoundException` / 自动配置不生效（本项目 biz-context 曾踩过此坑）。
> 大白话：清单上写的"型号"和机器实际型号对不上，机器（自动配置）自然启动不了。

### 步骤六：在根 POM 的 dependencyManagement 中声明版本

**做什么**：在根 POM 里声明这个 Starter 的版本。
**为什么**：统一版本管理后，各业务模块引入时**无需写版本号**（版本号由根 POM 一处管）。

`xiaohashu/pom.xml`：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.quanxiaoha</groupId>
            <artifactId>xiaoha-spring-boot-starter-xxx</artifactId>
            <version>${revision}</version>   <!-- 版本统一跟随 ${revision}（本项目为 0.0.1-SNAPSHOT） -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

> 本项目现状：根 POM 的 `dependencyManagement` 里已登记 `xiaoha-common`、`xiaoha-spring-boot-starter-biz-operationlog`、`xiaoha-spring-boot-starter-biz-context`、`xiaoha-spring-boot-starter-jackson` 四个坐标。

### 步骤七：业务模块引入依赖

**做什么**：在需要用的业务服务（如 auth）的 pom 里引入。
**为什么**：引入即生效——服务启动时自动执行自动配置类。

`xiaohashu-auth/pom.xml`：

```xml
<dependency>
    <groupId>com.quanxiaoha</groupId>
    <artifactId>xiaoha-spring-boot-starter-xxx</artifactId>
    <!-- 不用写 version：步骤六已在根 POM 统一声明 -->
</dependency>
```

### 步骤八：构建并验证

**做什么**：执行 Maven 构建，确认 jar 生成、启动后配置生效。
**为什么**：改了代码不重新 `mvn install`，本地仓库还是旧 jar，"改了等于没改"（坑点 4）。

```bash
# 构建指定模块及其依赖模块（-pl 指定模块路径；-am = also make，连同它依赖的模块一起构建）
mvn install -pl xiaoha-framework/xiaoha-spring-boot-starter-xxx -am

# 或全量构建（所有模块一起）
mvn clean install
```

验证：

1. 本地仓库 `~/.m2/repository/com/quanxiaoha/` 下出现对应 jar；
2. 启动应用，日志中出现自动配置生效的输出 / Bean 注入成功；
3. 无 `NoClassDefFoundError`、`FileNotFoundException`、`ClassNotFoundException`。

---

## 7. 关键点回顾（项目实战踩坑总结）

> 大白话：以下 4 个坑，是项目里真实踩过的，**每个坑的共性都是"改了某处，忘了同步另一处"**。

| # | 坑点 | 后果 | 对策 |
|---|------|------|------|
| 1 | 模块未注册进聚合 POM `<modules>` | 模块从不被构建，jar 不存在，依赖无法解析 | 步骤二必须执行 |
| 2 | `AutoConfiguration.imports` 类名与包名不一致 | 启动报 `ClassNotFoundException`，自动配置不生效 | 步骤五核对全限定名 |
| 3 | 修改包名时未同步引用方 | 业务代码 `找不到符号` | 同步更新 imports 文件 + 所有业务 import |
| 4 | 修改后未重新 `mvn install` | 本地仓库仍是旧 jar，改了等于没改 | 步骤八重新构建 |

> 排查口诀：**先看登记（modules 有没有我）→ 再看清单（imports 名字对不对）→ 再看同步（改包名改全了没）→ 最后重新 install（本地仓库新不新）**。
