# 整合 Druid 与 MyBatis 步骤（零基础版）

> 适用人群：**没学过数据库连接池、也没用过 MyBatis**的开发者。从"连接池是什么、MyBatis 是什么"讲起，再完整走一遍本项目整合步骤，所有概念配大白话类比 + 逐行中文注释。
> 本项目以 auth 模块为例，完整介绍在 Spring Boot 3.x 中整合 Druid 连接池。
> 版本：Spring Boot 3.0.2 + `druid-spring-boot-3-starter` 1.2.23

## 目录

1. [先看懂的：数据库连接池是什么、为什么需要它](#1-先看懂的数据库连接池是什么为什么需要它)
2. [Druid 是什么、为什么选它](#2-druid-是什么为什么选它)
3. [MyBatis 是什么（ORM / Mapper 接口 / XML 的关系）](#3-mybatis-是什么orm--mapper-接口--xml-的关系)
4. [步骤一：根 POM 统一管理版本](#4-步骤一根-pom-统一管理版本)
5. [步骤二：业务模块引入依赖](#5-步骤二业务模块引入依赖)
6. [步骤三：yml 配置数据源与连接池](#6-步骤三yml-配置数据源与连接池)
7. [步骤四：整合 MyBatis Generator 代码生成器](#7-步骤四整合-mybatis-generator-代码生成器)
8. [步骤五：启动类添加 @MapperScan 扫描 Mapper 接口](#8-步骤五启动类添加-mapperscan-扫描-mapper-接口)
9. [步骤六：验证](#9-步骤六验证)
10. [常见问题](#10-常见问题)

---

## 1. 先看懂的：数据库连接池是什么、为什么需要它

### 1.1 什么是"数据库连接"？（先弄清最小概念）

程序要读写数据库，不能直接"隔空操作"，必须先和数据库建立一条**连接**（connection，可以理解为一条"专用的网络通道"）。建立一条连接要做网络握手、身份认证等一堆事情，**很耗时**。

- **生活类比**：连接 = 你从候车大厅走到售票窗口前的那段路。每办一次业务都要走一遍，来来回回很费时间。

### 1.2 什么是"连接池"？（核心概念）

**连接池（Connection Pool）** = 提前建好一批连接放着，谁要用就领一条，用完**还回去**，下一个人继续用。它像一个**"共享的数据库连接柜台"**：柜台里始终趴着 N 个准备好的连接，业务方随到随用，用完归还，不用每次重新"开窗"。

- **生活类比**：图书馆的自习室。如果你每次学习都临时搬一套桌椅（每次新建连接），又慢又累；图书馆直接放好 100 张桌椅（连接池），你来就用、走就走，桌椅留给下一个人（连接复用）。
- **没有连接池的坏处**：每次操作数据库都要新建连接（耗时、耗资源），高并发下会把数据库"打爆"（连接数爆满）。
- **有连接池的好处**：连接反复复用，性能大幅提升；还能控制最大连接数（比如 20 条），防止数据库被压垮。

### 1.3 为什么本项目需要连接池？

本项目是微服务架构，一个服务（比如 user 服务、auth 服务）随时可能被大量请求调用，每个请求都可能查数据库。如果没有连接池，等于每个请求都"重新开窗"，数据库根本扛不住。连接池让所有请求共享那批连接，又快又稳。

---

## 2. Druid 是什么、为什么选它

**Druid（读音"德鲁伊"）** 是阿里巴巴开源的一个**数据库连接池**实现（同时也是一个监控平台）。市面上默认的连接池是 HikariCP（Spring Boot 自带的），本项目特意换成 Druid，因为它除了"复用连接"这个基本功，还自带三大额外能力：

| 能力 | 大白话 | 对应组件 |
|------|--------|---------|
| **监控后台** | 一个网页，能看每条 SQL 执行了多少次、耗时多长、有没有慢 SQL | `StatViewServlet` + `StatFilter` |
| **安全防护** | 防火墙，拦截危险 SQL（如注入攻击）；数据库密码可以加密存放 | `WallFilter` + `ConfigFilter` |
| **Web 请求统计** | 按 URL 维度统计每个接口请求了多少次、耗时多少 | `WebStatFilter` |

**核心概念表**（本项目用到的 Druid 组件）：

| 组件 | 大白话作用 |
|------|-----------|
| `DruidDataSource` | 连接池本体（替换 HikariCP），负责连接复用与监控 |
| `StatViewServlet` | Druid 监控后台，Web 界面查看 SQL/连接/性能 |
| `WebStatFilter` | Web 请求监控过滤器，采集 URL 维度统计 |
| `WallFilter` | SQL 防火墙，拦截危险 SQL |
| `StatFilter` | 统计 SQL 执行耗时，支持慢 SQL 记录 |
| `ConfigFilter` | 数据库密码加密（publicKey 解密） |

> 一句话总结：**Druid = "能复用的连接池" + "能看监控的仪表盘" + "带防火墙的门卫"，所以项目选它。**

---

## 3. MyBatis 是什么（ORM / Mapper 接口 / XML 的关系）

### 3.1 ORM 是什么？（大白话）

**ORM（Object-Relational Mapping，对象关系映射）**：把"数据库里的一行行数据"和"Java 里的一个个对象"互相翻译的机制。

- **矛盾在哪**：数据库表是"行 + 列"（比如 t_user 表里一行 = id、nickname、avatar...），而 Java 代码里操作的是"对象"（比如 `UserDO` 类的实例，有 `id`、`nickname`、`avatar` 字段）。两者长得不一样，不能直接互相赋值。
- **生活类比**：数据库的表是"填好的纸质表格"，Java 对象是"电脑里的录入表单"，ORM 就是那个**帮你把纸上的内容抄进电脑、又把电脑内容打印到纸上**的助手。

### 3.2 MyBatis 是什么？

**MyBatis** 是一个**半自动**的 ORM 框架（SQL 由你手写，不像 JPA 那样自动生成）。

- **为什么是"半自动"**：SQL 语句（`SELECT * FROM t_user WHERE id = ?` 这种）**由你亲自写**，而不是框架自动生成。这样你对 SQL 有完全的控制权，能针对慢查询做手工调优——这也是很多团队选 MyBatis 的原因。
- **在本项目里**：MyBatis 负责三件事：① 把 Java 方法调用翻译成 SQL 发给数据库；② 把 SQL 查出来的每一行数据翻译成 Java 对象；③ 把 Java 对象的字段翻译回 SQL 参数写进数据库。

### 3.3 Mapper 接口和 XML 是什么关系？（本项目最常见的困惑）

MyBatis 里，一个数据库操作被拆成**两个文件**来写，它们靠"名字"配对：

- **Mapper 接口**（如 `UserDOMapper.java`）：只声明**方法名**，不写 SQL。相当于**菜单**——只写"这道菜叫什么"。
  ```java
  // 接口里只写方法签名，不写 SQL
  UserDO selectByPrimaryKey(Long id);
  ```
- **Mapper XML**（如 `UserDOMapper.xml`）：写**具体 SQL**。相当于**后厨的做法**——这道菜具体怎么炒。
  ```xml
  <!-- XML 里写具体 SQL，通过 namespace + id 和接口方法配对 -->
  <select id="selectByPrimaryKey" resultType="...">
    select * from t_user where id = #{id}
  </select>
  ```

**配对规则（绑定机制）**——MyBatis 靠两个"名字"把接口和 XML 绑在一起：

1. XML 的 `namespace` 属性 = Mapper 接口的**全限定名**（包名 + 类名），如 `com.quanxiaoha.xiaohashu.user.biz.domain.mapper.UserDOMapper`；
2. XML 里每个操作的 `id` = 接口里的**方法名**，如 `selectByPrimaryKey`。

> 生活类比：接口是"点菜单"，XML 是"后厨做法"。点菜单上写"宫保鸡丁"，后厨就按"宫保鸡丁"的做法做。菜单名（namespace/id）对不上，后厨就找不到做法 → 运行时报 `Invalid bound statement`。

---

## 4. 步骤一：根 POM 统一管理版本

### 这一步做什么、为什么

**做什么**：在项目根 [pom.xml](file:///D:/java/xiaohashu/xiaohashu/pom.xml) 里声明三个依赖的版本，并在 `<dependencyManagement>` 里统一管理。
**为什么**：本项目有多个模块（auth、user、note...），如果每个模块各写各的版本，容易版本冲突。在根 POM 统一声明，所有子模块引用依赖时**不用写版本号**，一处改动、处处生效。

### 4.1 在 `<properties>` 中声明版本

```xml
    <spring-boot.version>3.0.2</spring-boot.version>                       <!-- Spring Boot 版本 -->
    <mysql-connector-java.version>8.0.29</mysql-connector-java.version>    <!-- MySQL 驱动版本（驱动=让 Java 能和 MySQL 对话的"翻译官"） -->
    <druid.version>1.2.23</druid.version>                                  <!-- Druid 连接池版本 -->
```

### 4.2 在 `<dependencyManagement>` 中管理依赖

`dependencyManagement` 的作用：**只"统一定价"（定版本），不"真正引入"**。子模块真正用到时再声明依赖，但版本号由这里统一给，子模块不用写：

```xml
      <!-- Mybatis（半自动 ORM 框架，见第 3 节） -->
      <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>${spring-boot.version}</version>   <!-- 版本引用上面的属性 -->
      </dependency>

      <!-- MySQL 驱动（Java 与 MySQL 之间通信的"翻译官"） -->
      <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>${mysql-connector-java.version}</version>
      </dependency>

      <!-- Druid 数据库连接池（见第 2 节） -->
      <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>druid-spring-boot-3-starter</artifactId>
        <version>${druid.version}</version>
      </dependency>
```

> ⚠️ 注意：Spring Boot 3.x 必须用 `druid-spring-boot-3-starter`（基于 jakarta），**不能用** 2.x 的 `druid-spring-boot-starter`（javax）。原因：Spring Boot 3 换用了新的命名空间 jakarta，用旧依赖启动会报 `ClassNotFound: javax.servlet.*`。

---

## 5. 步骤二：业务模块引入依赖

### 这一步做什么、为什么

**做什么**：在需要用数据库的业务模块（如 [auth/pom.xml](file:///D:/java/xiaohashu/xiaohashu/xiaohashu-auth/pom.xml)、`xiaohashu-user-biz/pom.xml`）里声明这三个依赖。
**为什么**：步骤一只"定版本"没"真引入"，子模块声明后才会真正把依赖装进自己的 classpath（类路径，程序运行时找得到这些代码的地方）。

```xml
      <!-- Mybatis：ORM 框架，负责 SQL 与 Java 对象互转 -->
      <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <!-- 版本由根 POM 统一管理，这里无需再写 -->
      </dependency>
      <!-- MySQL 驱动：Java 连 MySQL 的"翻译官" -->
      <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
      </dependency>
      <!-- Druid 连接池：连接复用 + 监控 + 防火墙 -->
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>druid-spring-boot-3-starter</artifactId>
    </dependency>
```

> 本项目 `xiaohashu-user-biz` 模块的 pom 也完全相同地引入了这三个依赖（见 [user-biz/pom.xml](file:///D:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/pom.xml)）。

---

## 6. 步骤三：yml 配置数据源与连接池

### 这一步做什么、为什么

**做什么**：告诉 Spring Boot：① MyBatis 的 XML 文件放在哪；② 数据库地址、账号密码、用哪个连接池、连接池参数。
**为什么**：光有依赖还不够，Spring Boot 默认只会用 HikariCP 连接池；必须通过配置显式"点名"用 Druid，并告诉它数据库在哪。

### 6.1 Mybatis 配置（配置 XML 文件路径）

编辑 `application.yml`（各模块的通用配置），配置 MyBatis `xml` 文件路径：

```yaml
mybatis:
  # MyBatis xml 配置文件路径（classpath 项目资源目录下，mapper 目录里的所有 *.xml）
  mapper-locations: classpath:/mapper/**/*.xml
```

> 这条配置决定了"第 3 节说的 XML 文件去哪找"：MyBatis 启动时会去 `resources/mapper/` 目录下扫描所有 XML，和 Mapper 接口配对。

### 6.2 Druid 数据源配置

编辑 `application-dev.yml`（本地开发环境配置文件），添加数据源相关配置，**逐行注释**：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver # 数据库驱动类（MySQL 8 的驱动全类名）
    # 数据库连接信息（jdbc:mysql:// 主机:端口/库名?参数）
    url: jdbc:mysql://localhost:3306/xiaohashu?useUnicode=true&characterEncoding=utf-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root # 数据库用户名
    password: bqxe1Np8hZn06eOJSAyvTjsgvNzqzpTE38yE3/XTYQv+qd3o8KeacmJwoaEogA6SDpz535l9mKng6ayYYbeXpw== # 数据库密码（已用 ConfigFilter 加密，不是明文）
    type: com.alibaba.druid.pool.DruidDataSource # 指定连接池实现 = Druid（关键！不写就用默认的 HikariCP）
    druid: # Druid 连接池专属配置
      initial-size: 5 # 初始化连接池大小（启动时先建 5 条连接）
      min-idle: 5 # 最小连接池数量（平时至少保持 5 条空闲连接）
      max-active: 20 # 最大连接池数量（最多同时 20 条连接，再多就得排队）
      max-wait: 60000 # 连接时最大等待时间（单位：毫秒，拿不到连接最多等 60 秒）
      test-while-idle: true
      time-between-eviction-runs-millis: 60000 # 配置多久进行一次检测，检测需要关闭的连接（单位：毫秒）
      min-evictable-idle-time-millis: 300000 # 配置一个连接在连接池中最小生存的时间（单位：毫秒）
      max-evictable-idle-time-millis: 900000 # 配置一个连接在连接池中最大生存的时间（单位：毫秒）
      validation-query: SELECT 1 FROM DUAL # 配置测试连接是否可用的查询 sql（验证连接还活不活）
      connectionProperties: config.decrypt=true;config.decrypt.key=MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBALaEOoJXb07FjsP2xyKJmTCWr/3SNDVMpneWOy1kmqsrpx9QUjLnCc5PkJb59/avGHHOvWAbwNQ6xYbVC5LruhcCAwEAAQ== # 开启密码解密 + 公钥（ConfigFilter 用公钥解开上面的密文密码）
      test-on-borrow: false
      test-on-return: false
      pool-prepared-statements: false
      web-stat-filter:
        enabled: true # 开启 Web 请求监控（统计每个 URL 的请求情况）
      stat-view-servlet:
        enabled: true # 开启监控后台页面
        url-pattern: /druid/* # 配置监控后台访问路径（浏览器访问 http://localhost:端口/druid/）
        login-username: admin # 配置监控后台登录的用户名、密码
        login-password: admin
      filter:
        config:
          enabled: true # 开启密码解密过滤器（配合上面的 connectionProperties 解密密文密码）
        stat:
          enabled: true # 开启 SQL 统计过滤器
          log-slow-sql: true # 开启慢 sql 记录（执行太慢的 SQL 会打日志）
          slow-sql-millis: 2000 # 若执行耗时大于 2s，则视为慢 sql
          merge-sql: true
        wall: # 防火墙（拦截危险 SQL）
          config:
            multi-statement-allow: true # 允许一次执行多条 SQL（墙外默认拦截，需要时放开）

logging:
  level:
    com.quanxiaoha.xiaohashu.user.biz.domain.mapper: debug # 打印 Mapper 接口所在包下的 SQL 日志（方便调试）
```

> ⚠️ 关键点：`type` 必须显式指定为 `com.alibaba.druid.pool.DruidDataSource`，否则 Spring Boot 默认用 HikariCP（配置了 druid 参数也没用）。

---

## 7. 步骤四：整合 MyBatis Generator 代码生成器

### 这一步做什么、为什么

**做什么**：引入 MyBatis Generator（MyBatis 官方代码生成器，简称 MBG），根据数据库表**自动生成**三类文件：DO 实体类、Mapper 接口、Mapper XML。
**为什么**：每张表的手写代码长得都差不多（字段、getter/setter、增删改查），让工具生成能省大量重复劳动，而且保证风格统一。

### 7.1 添加 Maven 插件

编辑业务模块（如 `xiaohashu-user-biz`）的 `pom.xml`，添加生成器插件（版本已在根 POM `pluginManagement` 中声明，无需写版本）：

```xml
    <build>
        <plugins>
            <!-- MyBatis 代码生成器插件：运行 mvn mybatis-generator:generate 即可按表生成代码 -->
            <plugin>
                <groupId>org.mybatis.generator</groupId>
                <artifactId>mybatis-generator-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```

![image-20260803145102348](C:\Users\DELL\AppData\Roaming\Typora\typora-user-images\image-20260803145102348.png)
（截图示意：插件加载成功后，在 `src/main/resources` 下创建 `generatorConfig.xml` 配置文件的步骤）

### 7.2 编写 generatorConfig.xml

在 `src/main/resources` 下创建 `generatorConfig.xml`，配置数据库连接与生成路径，**逐行注释**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE generatorConfiguration
        PUBLIC "-//mybatis.org//DTD MyBatis Generator Configuration 1.0//EN"
        "http://mybatis.org/dtd/mybatis-generator-config_1_0.dtd">
<generatorConfiguration>
    <context id="mysqlTables" targetRuntime="MyBatis3" defaultModelType="flat">
        <!-- 自动检查关键字，为关键字增加反引号，如：`type`（防止"type"这类词和 SQL 关键字冲突） -->
        <property name="autoDelimitKeywords" value="true"/>
        <property name="beginningDelimiter" value="`"/>
        <property name="endingDelimiter" value="`"/>
        <!-- 指定生成的 Java 文件编码 -->
        <property name="javaFileEncoding" value="UTF-8"/>

        <!-- 对生成的注释进行控制 -->
        <commentGenerator>
            <!-- 由于此插件生成的注释不太美观，这里设置不生成任何注释 -->
            <property name="suppressAllComments" value="true"/>
        </commentGenerator>

        <!-- 数据库连接（生成器要连数据库读表结构，才能生成代码） -->
        <jdbcConnection driverClass="com.mysql.cj.jdbc.Driver"
                        connectionURL="jdbc:mysql://127.0.0.1:3306/xiaohashu"
                        userId="root"
                        password="填写你的数据库密码">
            <!-- 解决多个重名的表生成表结构不一致问题 -->
            <property name="nullCatalogMeansCurrent" value="true"/>
        </jdbcConnection>

        <!-- 不强制将所有的数值类型映射为 Java 的 BigDecimal 类型 -->
        <javaTypeResolver>
            <property name="forceBigDecimals" value="false"/>
        </javaTypeResolver>

        <!-- DO 实体类存放路径（生成的 JavaBean，对应数据库表的一行数据） -->
        <javaModelGenerator targetPackage="com.quanxiaoha.xiaohashu.auth.domain.dataobject"
                            targetProject="src/main/java"/>

        <!-- Mapper xml 文件存放路径（第 3 节说过的"后厨做法"） -->
        <sqlMapGenerator targetPackage="mapper"
                         targetProject="src/main/resources"/>

        <!-- Mapper 接口存放路径（第 3 节说过的"点菜单"） -->
        <javaClientGenerator type="XMLMAPPER" targetPackage="com.quanxiaoha.xiaohashu.auth.domain.mapper"
                             targetProject="src/main/java"/>

        <!-- 需要生成的表-实体类（ByExample：控制是否生成按条件操作的方法） -->
        <!-- tableName = 数据库表名，domainObjectName = 生成的 Java 类名 -->
        <table tableName="t_user" domainObjectName="UserDO"
               enableCountByExample="false"
               enableUpdateByExample="false"
               enableDeleteByExample="false"
               enableSelectByExample="false"/>
    </context>
</generatorConfiguration>
```

> 每个服务的 `DO` 实体类、`mapper` 接口、`xml` 映射文件的包路径会有所区别，小伙伴们可以直接从别的服务中，复制过来然后稍作修改一下。
> 本项目 `xiaohashu-user-biz` 模块的 [generatorConfig.xml](file:///D:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/resources/generatorConfig.xml) 就是按这个套路改的：包名换成 `com.quanxiaoha.xiaohashu.user.biz.domain.dataobject / mapper`，表同样是 `t_user` → `UserDO`。

### 7.3 执行生成

在模块目录下执行 Maven 命令（插件会读 `generatorConfig.xml`，连数据库生成代码）：

```bash
mvn mybatis-generator:generate
```

### 7.4 生成产物

**生成后修改一下 `UserDO` 实体类，删除掉 `get/set` 方法，并添加上 Lombok 相关注解：`@Data @AllArgsConstructor @NoArgsConstructor @Builder`，以及将 `Date` 日期类修改为 `LocalDateTime` Java8 新的日期类**（Lombok 的 `@Data` 会自动生成 getter/setter，不用手写；`LocalDateTime` 比 `Date` 更规范、不可变）。

| 产物 | 位置 | 说明 |
|------|------|------|
| DO 实体类 | `domain/dataobject/UserDO.java` | 对应数据库表的一行数据 |
| Mapper 接口 | `domain/mapper/UserMapper.java` | 声明方法的"点菜单" |
| Mapper XML | `resources/mapper/UserMapper.xml` | 写 SQL 的"后厨做法" |

> 注：若 `table` 的 `domainObjectName` 配置为 `UserDO`（如本模块实际配置），则生成的接口与 XML 名为 `UserDOMapper` / `UserDOMapper.xml`（本项目 user 模块实际生成的就是 `UserDOMapper.xml`，见 [resources/mapper](file:///D:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/resources/mapper) 目录）。上表按 auth 模块的产物命名展示。

---

## 8. 步骤五：启动类添加 @MapperScan 扫描 Mapper 接口

### 这一步做什么、为什么

**做什么**：在启动类上添加 `@MapperScan` 注解，指定 Mapper 接口所在的包。
**为什么**：MyBatis 需要把 Mapper 接口注册成 Spring Bean（交给 Spring 管理的对象），否则 Service 里注入 `UserDOMapper` 会失败，运行时报 `No qualifying bean of type 'XxxMapper'`。

在启动类 [XiaohashuAuthApplication.java](file:///D:/java/xiaohashu/xiaohashu/xiaohashu-auth/src/main/java/com/quanxiaoha/xiaohashu/auth/XiaohashuAuthApplication.java) 上添加：

```java
@MapperScan("com.quanxiaoha.xiaohashu.auth.domain.mapper") // 扫描 Mapper 接口所在包，注册为 Spring Bean
@SpringBootApplication
public class XiaohashuAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaohashuAuthApplication.class, args); // 启动 Spring Boot 应用
    }
}
```

> 作用：将指定包下的 Mapper 接口注册为 Spring Bean，否则 MyBatis 无法注入 Mapper，运行时报 `No qualifying bean of type 'XxxMapper'`。
> 本项目其他模块同理：如 user 模块启动类 `XiaohashuUserBizApplication` 上是 `@MapperScan("com.quanxiaoha.xiaohashu.user.biz.domain.mapper")`，路径必须和本模块 Mapper 接口实际所在包一致。

---

## 9. 步骤六：验证

**做什么**：启动应用，把整条"Controller → Service → Mapper → SQL → 数据库"链路跑通，确认 MyBatis + Druid 都正常工作。

1. 启动应用，确认启动类 `@MapperScan` 的路径与 Mapper 接口实际包名一致；
2. 在 Service 中注入 Mapper（如 `userMapper.selectByPrimaryKey(id)`），调用后日志出现 SQL，说明 MyBatis + Druid 链路正常；
3. Mapper XML 无红波浪线，`namespace` 与接口全限定名一致，方法 `id` 与接口方法名对应（见第 3 节配对规则）；
4. 访问 `http://localhost:8080/druid/`（admin/admin）可在 SQL 监控中看到上述 SQL 统计。

---

## 10. 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 数据源仍是 HikariCP | 没写 `type` 或依赖用错 | 加 `type: com.alibaba.druid.pool.DruidDataSource`；Spring Boot 3 用 `druid-spring-boot-3-starter` |
| 启动报 `ClassNotFound: javax.servlet.*` | Spring Boot 3 引入了错误的 druid 依赖 | 换 `druid-spring-boot-3-starter`（jakarta） |
| 监控后台 404 | `stat-view-servlet` 未开或 url-pattern 不对 | 确认 `enabled: true` 且访问 `/druid/` 路径 |
| 密码解密失败（`decrypt error`） | key 填错或 ConfigFilter 未开启 | 检查 `config.decrypt.key` 用公钥、`filter.config.enabled: true` |
| 防火墙拦截正常 SQL | wall 规则过严 | 按需调整 `filter.wall.config`，如 `multi-statement-allow: true` |
| `Invalid bound statement (not found)` | Mapper 接口与 XML 未绑定 | 检查 `mapper-locations: classpath:/mapper/**/*.xml` 与 XML 的 `namespace` 是否一致 |
| `No qualifying bean of type 'XxxMapper'` | 未扫描到 Mapper 接口 | 确认启动类有 `@MapperScan` 且路径正确 |
| 代码生成报错 | 数据库连不上 / 表名不存在 | 检查 `generatorConfig.xml` 的 `jdbcConnection` 与 `table` 配置 |
| 下划线字段映射不上 | 未开启驼峰映射 | 加 `mybatis.configuration.map-underscore-to-camel-case: true` |
