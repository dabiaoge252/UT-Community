# Lua 脚本使用总结（零基础版，Redis 场景）

> 更新时间：2026-08-05
> 适用人群：**没学过 Lua、也没在 Redis 里写过脚本**的开发者。从"Lua 是什么、Redis 为什么要用它"讲起，逐步深入到本项目代码，所有概念配大白话类比 + 逐行中文注释。

## 目录

1. [先看懂的：Lua 是什么、Redis 为什么需要它](#1-先看懂的lua-是什么redis-为什么需要它)
2. [脚本在 Redis 里是怎么跑的](#2-脚本在-redis-里是怎么跑的)
3. [Redis Lua 脚本常用语法](#3-redis-lua-脚本常用语法)
4. [项目中用到的 Lua 脚本（3 个逐行解析）](#4-项目中用到的-lua-脚本3-个逐行解析)
5. [Java 中如何调用 Lua 脚本](#5-java-中如何调用-lua-脚本)
6. [使用注意点（6 条）](#6-使用注意点6-条)
7. [什么时候该用 Lua（选型清单）](#7-什么时候该用-lua选型清单)

---

## 1. 先看懂的：Lua 是什么、Redis 为什么需要它

### 1.1 Lua 是什么？（大白话）

**Lua（读作"撸啊"）** 是一种**轻量级脚本语言**（script language，一种写起来很简单的编程语言），它很小、很快、很容易嵌入到其他软件里当"插件"用。

- **生活类比**：Lua 就像贴在冰箱门上的一张**便签纸**。你不用写一大本操作手册（比如一本完整的 Java 书），只需要在便签上写几条简单指令（"下班买牛奶""周五浇花"），冰箱自己就能看懂并照着做。
- **对 Redis 来说**：Redis 是一个"数据仓库"软件，它自己内置了一个**Lua 解释器**（能读懂并执行 Lua 代码的"大脑"）。于是你可以把一串操作写成一段 Lua 代码发给 Redis，让 Redis 一次性执行完。

> 一句话总结：**Lua = 给 Redis 写"操作便签"用的简单语言。**

### 1.2 Redis 为什么需要 Lua？（核心：原子性）

要理解这一点，先要知道 Redis 的**单线程**特性：Redis 同一时刻只能处理**一条**命令，就像只有一个窗口的柜台。

- 一条命令时没问题：柜台一次只服务一个人。
- **多条命令时就有漏洞**：比如你要做"①查有没有位置 → ②查有没有重复 → ③放进去"三步，这三步是**分开的三条命令**。在①和②之间，别人可能插队先执行了操作——这就是**并发问题**（多人同时操作同一份数据，互相干扰）。
- **Lua 脚本解决方式**：把三步写进**同一段 Lua 脚本**里。Redis 会把这段脚本当作**一个整体**来执行，执行期间**其他命令一概插不进来**。这个"整体执行、中途不被打断"的性质，专业说法叫**原子性**（atomic，意思是像原子一样不可再分割）。

- **生活类比**：想象去银行柜台办"转账"。如果分三步问柜员（先查余额→再扣钱→再入账），中间别人插队就可能出乱子；但如果填**一张完整的转账单**交给柜员，柜员一口气办完，谁也插不了队。**Lua 脚本 = 那张一次办完的转账单**。

> 一句话总结：**Redis 用 Lua = 把"多条命令"焊成"一条不可打断的整体命令"，从根上杜绝并发互相干扰。**

### 1.3 本项目为什么用 Lua？（真实场景）

本项目用 Redis 存"**关注关系**"：每个用户的关注列表是一个 **ZSET**（有序集合，可以理解为带"分数"的一排卡片，分数=关注时间）。

当用户**关注**另一个用户时，业务上要连续做四步校验/操作（见 [RelationServiceImpl.follow](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/service/impl/RelationServiceImpl.java#L84-L96)）：

```
① 判断这个用户的关注列表 ZSET 在不在 Redis 里（EXISTS）
② 校验关注人数有没有达到上限 1000（ZCARD）
③ 校验目标用户是不是已经关注过了（ZSCORE）
④ 都没问题才真正添加关注关系（ZADD）
```

- 如果拆成 4 条 Redis 命令，**并发**下（比如同一秒内 1000 人同时操作），可能出现"重复关注"或"超过 1000 人上限"的问题；
- 用 **Lua 一次搞定、整体原子执行**，这四个步骤中间谁也插不进来，彻底杜绝并发问题。

---

## 2. 脚本在 Redis 里是怎么跑的

先建立整体画面，一共 5 步：

```
① Java 代码把 Lua 脚本内容 + 参数 发给 Redis（本项目用 redisTemplate.execute）
        ↓
② Redis 内置的 Lua 解释器 拿到脚本，开始逐行执行
        ↓
③ 脚本里通过 redis.call('命令', ...) 调用 Redis 命令，并把结果拿回脚本里继续用
        ↓
④ 脚本执行完毕，用 return 把最终结果返回给 Redis
        ↓
⑤ Redis 把结果回传给 Java 代码，Java 拿到数字（如 0 / -1）做业务分支
```

关键点：

| 环节 | 大白话 | 说明 |
|------|--------|------|
| **脚本从哪来** | 一段 `.lua` 文件里的文本 | 本项目放在 `resources/lua/` 目录下，Java 启动时读进来 |
| **谁来执行** | Redis 自己的 Lua 解释器 | Redis 内部"长"了一个 Lua 引擎，不用我们自己装 |
| **参数怎么传** | 通过 `KEYS[]` 和 `ARGV[]` 两个"信箱" | `KEYS` 放 Redis 的 key，`ARGV` 放其他普通参数（详见第 3 节） |
| **执行期间别人能插队吗** | 不能 | 整个脚本期间 Redis 不干别的事（原子性），所以脚本不能写太长太慢 |
| **结果怎么回来** | 脚本 `return` 什么，Java 就收到什么 | 本项目约定返回数字状态码（0 成功、负数表示各种失败原因） |

---

## 3. Redis Lua 脚本常用语法

> 本节只挑本项目用到的语法讲，够用即可。所有名词第一次出现都给大白话解释。

### 3.1 参数怎么传进来（KEYS 和 ARGV）

脚本开头通常先把传入的参数取到变量里：

```lua
local key = KEYS[1]   -- KEYS 是"信箱1"：装 Redis 的 key（键名）。KEYS[1] 就是第一个 key
local arg1 = ARGV[1]  -- ARGV 是"信箱2"：装普通参数。ARGV[1] 就是第一个普通参数
local arg2 = ARGV[2]  -- 第二个普通参数
```

> 约定：**key 放 KEYS，其他参数放 ARGV**。原因：Redis 集群模式下，同一个脚本里出现的所有 key 必须落在同一个节点（一台机器）上。把 key 单独放 KEYS 里，Redis 才能根据这些 key 正确计算该把脚本发给哪台机器；如果全塞进 ARGV，Redis 就不知道哪些是 key，容易跨节点报错。

### 3.2 怎么在脚本里调 Redis 命令（redis.call / redis.pcall）

```lua
-- redis.call：执行一条 Redis 命令，并把命令的返回结果拿回 Lua 里继续用
redis.call('ZADD', key, score, member)

-- redis.pcall：和 call 几乎一样，唯一区别是出错时的表现不同：
--   call  出错会立刻终止整个脚本并抛异常（中断执行）
--   pcall 出错不中断，而是返回一个"错误对象"，你可以自己判断
redis.pcall('ZADD', key, score, member)
```

#### 常用命令对照表（本项目用到的）

| Redis 命令 | 大白话作用 | 项目中的使用 |
|-----------|-----------|-------------|
| `EXISTS key` | 判断 key 存不存在（返回 1=存在，0=不存在） | `follow_check_and_add.lua` 判断关注列表 ZSET 是否存在 |
| `ZCARD key` | 数一数 ZSET 里有几个成员 | 校验关注数是否达到上限（1000） |
| `ZSCORE key member` | 查 ZSET 里某个成员的分数（查不到返回 nil/空） | 校验是否已关注该用户 |
| `ZADD key score member` | 往 ZSET 里添加成员（score 是分数，本项目存关注时间戳） | 添加关注关系 |
| `EXPIRE key seconds` | 给 key 设置过期时间（多少秒后自动消失） | 给关注列表 ZSET 设置随机 TTL（生存时间） |
| `SET / GET / INCR / HSET / LPUSH` | 其他常用命令（存字符串、读、自增、存哈希、存列表） | 通用场景 |

### 3.3 控制流与常用语法（if / for / table.insert / unpack / 类型转换）

```lua
-- ============ 条件判断：if ... then / elseif ... then / else / end ============
if exists == 0 then        -- 如果 exists 等于 0
    return -1              -- 就返回 -1
elseif size >= 1000 then   -- 否则如果 size 大于等于 1000
    return -2              -- 就返回 -2
else                       -- 否则
    return 0               -- 就返回 0
end                        -- 每个 if 都要用 end 收尾（这是 Lua 的固定写法）

-- ============ for 循环（i 从 1 开始，到 #ARGV - 1 为止，每次加 2）============
-- 解释：#ARGV 表示"ARGV 里一共有几个参数"；1, N, 2 的意思是"从 1 数到 N，每步加 2"
for i = 1, #ARGV - 1, 2 do
    table.insert(list, ARGV[i])   -- table.insert：往一个表（类似 Java 的 List）末尾追加一个元素
end

-- ============ 取表长度 ============
local len = #ARGV   -- # 是 Lua 的"长度符号"，#ARGV = ARGV 参数个数

-- ============ 展开表为多个参数（unpack）============
-- 把表里的所有元素"拆开"平铺成一个个参数，传给命令
-- 注意版本差异：Lua 5.1 用 unpack，Lua 5.3+ 改名叫 table.unpack（详见第 6 节注意点 6）
redis.call('ZADD', key, unpack(zaddArgs))

-- ============ 类型转换 ============
local num = tonumber(ARGV[1])  -- tonumber：字符串转数字（string → number）
local str = tostring(num)      -- tostring：数字转字符串（number → string）
```

### 3.4 脚本的返回值（return）

Lua 脚本靠 `return` 把结果还给 Java，支持数字、字符串、布尔、表（返回 `nil` 表示什么都不返回）：

```lua
return 0        -- 返回数字 0 → Java 端可声明返回 Long 类型接收
return -1       -- 返回数字 -1 → 本项目用它约定"业务失败"状态码
return 'OK'     -- 返回字符串 → Java 端用 String 接收
return {1, 2}   -- 返回一个表（数组）→ Java 端用 List 接收
```

---

## 4. 项目中用到的 Lua 脚本（3 个逐行解析）

脚本位置：[xiaohashu-user-relation-biz/resources/lua](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/resources/lua)

该目录下共有 5 个脚本，本节解析**关注功能**用到的 3 个核心脚本：

### 4.1 follow_check_and_add.lua（校验 + 添加，核心脚本）

作用：先做三重校验，全部通过才把关注关系写进 Redis。**逐行解析**：

```lua
-- LUA 脚本：校验并添加关注关系

-- KEYS[1]：传入的 Redis key，也就是"当前用户的关注列表"这个 ZSET 的键名
local key = KEYS[1]
-- ARGV[1]：关注的用户 ID（要把谁加进关注列表）
local followUserId = ARGV[1]
-- ARGV[2]：时间戳（当前时间转成的毫秒数，作为 ZSET 的分数，用来排序）
local timestamp = ARGV[2]

-- 第一步校验：用 EXISTS 命令检查这个 ZSET 在不在 Redis 里
-- 返回 1 = 存在，0 = 不存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    -- ZSET 不存在：返回 -1，Java 端看到 -1 就知道"缓存里没数据，要去数据库同步"
    return -1
end

-- 第二步校验：用 ZCARD 命令数一下关注列表里现在有多少人
local size = redis.call('ZCARD', key)
if size >= 1000 then
    -- 关注数达到上限 1000（FOLLOWING_COUNT_LIMIT）：返回 -2
    -- Java 端看到 -2 就抛"关注用户已达上限"业务异常
    return -2
end

-- 第三步校验：用 ZSCORE 查一下目标用户是不是已经在关注列表里了
-- 查得到（返回分数，非 nil）说明已经关注过
if redis.call('ZSCORE', key, followUserId) then
    -- 已关注（ALREADY_FOLLOWED）：返回 -3
    -- Java 端看到 -3 就抛"您已经关注了该用户"业务异常
    return -3
end

-- 三重校验全部通过，用 ZADD 真正添加关注关系（score = 关注时间戳，member = 被关注的用户 ID）
redis.call('ZADD', key, timestamp, followUserId)
return 0   -- 返回 0：表示添加成功，Java 端正常返回
```

**返回值与业务异常映射**（见 [RelationServiceImpl.checkLuaScriptResult](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/service/impl/RelationServiceImpl.java#L277-L288)）：

| Lua 返回值 | 含义 | Java 端处理 |
|-----------|------|------------|
| `-1` | ZSET 不存在 | 走 DB 全量同步分支（查数据库把历史关注关系同步进 Redis） |
| `-2` | 关注数达上限 | 抛 `FOLLOWING_COUNT_LIMIT` 异常（"您关注的用户已达上限"） |
| `-3` | 已关注 | 抛 `ALREADY_FOLLOWED` 异常（"您已经关注了该用户"） |
| `0` | 添加成功 | 正常返回 |

### 4.2 follow_add_and_expire.lua（直接添加 + 设置过期）

作用：当"用户从未关注过人"（数据库里该用户的关注记录为空）时，不需要校验，直接 ZADD 添加一条关注关系，并顺手设置一个**随机过期时间**（缓存不永久存，防止 Redis 内存越堆越多）。**逐行解析**：

```lua
-- KEYS[1]：当前用户的关注列表 ZSET 的 key
local key = KEYS[1]
-- ARGV[1]：关注的用户 ID
local followUserId = ARGV[1]
-- ARGV[2]：时间戳（作为 ZSET 分数）
local timestamp = ARGV[2]
-- ARGV[3]：过期时间（单位：秒，由 Java 端算好传进来，本项目是"保底 1 天 + 随机秒数"）
local expireSeconds = ARGV[3]

-- 用 ZADD 添加关注关系
redis.call('ZADD', key, timestamp, followUserId)
-- 用 EXPIRE 给这个 ZSET 设置过期时间（到期自动删除，不占内存）
redis.call('EXPIRE', key, expireSeconds)
return 0   -- 返回 0 表示成功
```

### 4.3 follow_batch_add_and_expire.lua（批量同步 + 设置过期）

作用：当"数据库里已有历史关注记录"时，把全部记录一次性批量同步进 ZSET（避免一条一条写、来回网络开销太大）。Java 端会把参数组装成"分数、值、分数、值……最后一个参数是过期时间"这样交替排列的列表传进来。**逐行解析**：

```lua
-- KEYS[1]：当前用户的关注列表 ZSET 的 key
local key = KEYS[1]

-- 先准备一个空表（可以理解为一个空篮子），用来收集待添加的参数
local zaddArgs = {}

-- 遍历 ARGV 参数（i 从 1 开始，到倒数第 2 个为止，每次加 2）
-- 因为参数是"分数、值、分数、值……"交替排列的，所以每次取一对
for i = 1, #ARGV - 1, 2 do
    table.insert(zaddArgs, ARGV[i])      -- 分数（关注时间）
    table.insert(zaddArgs, ARGV[i+1])    -- 值（关注的用户 ID）
end

-- 调用 ZADD 批量插入数据
-- unpack 把 zaddArgs 里的所有元素"拆开"平铺成参数，等价于 ZADD key 分数1 值1 分数2 值2 ...
redis.call('ZADD', key, unpack(zaddArgs))

-- 设置 ZSet 的过期时间
-- ARGV 的最后一个参数是过期时间（Java 端约定放在最后）
local expireTime = ARGV[#ARGV]
redis.call('EXPIRE', key, expireTime)

return 0   -- 返回 0 表示成功
```

### 4.4 三个脚本是怎么配合的（整体流程）

> 一句话流程：**先跑 `check` 判断 ZSET 是否存在 → 不存在时查 DB → 有历史数据跑 `batch` 全量同步、没历史数据跑 `add` 直接添加 → 再跑一次 `check` 补上最新关注。**

对照 [RelationServiceImpl.follow](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/service/impl/RelationServiceImpl.java#L96-L131) 的真实代码，流程如下：

```
① 执行 follow_check_and_add.lua（check 脚本）
      ↓
② 返回 -1（ZSET 不存在）→ 查数据库 selectByUserId 拿到该用户的全部关注记录
      ↓
③ 记录为空（用户从没关注过人）？──是──▶ 执行 follow_add_and_expire.lua（直接加 + 设过期）
      ↓ 否
④ 执行 follow_batch_add_and_expire.lua（全量同步 + 设过期）
      ↓
⑤ 再执行一次 follow_check_and_add.lua，把"最新这条关注"补加进去
```

注意点：脚本 3 执行完毕后，**最新的这条关注**并不在同步的旧数据里，所以 Java 端会**再跑一次 check 脚本**把最新的关注关系补上（见上面第 ⑤ 步）。

---

## 5. Java 中如何调用 Lua 脚本

以 [RelationServiceImpl.follow](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/service/impl/RelationServiceImpl.java#L84-L98) 为例，完整调用代码**逐行注释**：

```java
// ① 创建"脚本对象"：DefaultRedisScript 是 Spring Data Redis 提供的工具类，
//    用来描述"一段要在 Redis 服务端执行的 Lua 脚本"
//    <Long> 表示这段脚本执行完的返回值，Java 这边用 Long 类型接收
DefaultRedisScript<Long> script = new DefaultRedisScript<>();

// ② 指定 Lua 脚本从哪来：从 classpath（项目 resources 目录）下读取 /lua/follow_check_and_add.lua 文件
//    ClassPathResource：定位 classpath 里的资源文件
//    ResourceScriptSource：把文件内容变成"脚本内容来源"
script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua")));

// ③ 指定脚本返回值的类型：Lua 里 return 的数字 → Java 的 Long
//    必须和脚本里 return 的类型一致，否则反序列化会报错
script.setResultType(Long.class);

// ④ 把当前时间转成时间戳（毫秒），作为 ZSET 的 score（分数）
long timestamp = DateUtils.localDateTime2Timestamp(LocalDateTime.now());

// ⑤ 执行 Lua 脚本，execute 方法的参数对应关系：
//    第 1 个参数：脚本对象 script（脚本内容 + 返回值类型都封装在里面）
//    第 2 个参数：KEYS 列表（对应脚本里的 KEYS[1]），这里是"当前用户的关注列表 key"
//    第 3 个参数起：ARGV 可变参数（对应脚本里的 ARGV[1]、ARGV[2]...）
//                  ARGV[1] = followUserId（关注的用户 ID）
//                  ARGV[2] = timestamp（时间戳）
Long result = redisTemplate.execute(script,
        Collections.singletonList(followingRedisKey),  // KEYS[1]
        followUserId,                                  // ARGV[1]
        timestamp);                                    // ARGV[2]

// ⑥ 拿到返回值后做业务分支：checkLuaScriptResult 里把 -2/-3 翻译成业务异常抛出
checkLuaScriptResult(result);
```

### Java 调用要点（4 条）

1. **`DefaultRedisScript<T>`**：指定脚本来源（`ResourceScriptSource` + `ClassPathResource` 定位 `.lua` 文件）和返回类型（`setResultType` 声明返回值对应的 Java 类型）；
2. **`redisTemplate.execute(script, keys, args...)`**：第一个参数是脚本对象，第二个是 KEYS 集合（对应 `KEYS[]`），后面是 ARGV 可变参数（对应 `ARGV[]`）；
3. **返回值类型**：`Long.class` 接收数字结果，`String.class` 接收字符串结果，`List.class` 接收数组（表）结果；
4. **结果校验**：拿到返回值后根据约定状态码做分支处理（本项目用 `checkLuaScriptResult` 把 -2 / -3 转成业务异常）。

---

## 6. 使用注意点（6 条）

1. **原子性**：脚本执行期间其他命令不会插入，可安全替代"多条命令 + 事务"，但脚本应尽量**短小**、避免耗时操作（如循环大集合），否则执行期间 Redis 一直不响应其他请求，会**阻塞 Redis**（Redis 单线程，一个脚本卡住，所有人都等着）；
2. **KEYS 与 ARGV 分离**：Key 必须通过 `KEYS[]` 传，普通参数通过 `ARGV[]` 传——集群模式下 Redis 按 KEYS 计算哈希槽（决定数据存在哪台机器），全放 ARGV 会导致跨节点报错；
3. **错误处理**：`redis.call` 出错会终止脚本并抛异常，`redis.pcall` 会返回错误对象而不中断，可根据场景选择；
4. **返回类型要匹配**：Java 端 `setResultType` 与脚本 `return` 的类型必须一致，否则反序列化报错；
5. **不要在脚本里做 IO/网络操作**：Lua 脚本只应操作 Redis 数据（不要在里面发 HTTP 请求、读写磁盘、连其他数据库），否则既慢又容易出问题；
6. **版本差异**：`unpack` 在 Lua 5.3 中更名为 `table.unpack`。Redis 7 内置 Lua 5.1（Redis 7.x 默认仍兼容），本项目脚本里用的 `unpack` 可以正常运行。

---

## 7. 什么时候该用 Lua（选型清单）

> 开发新功能时先对号入座：**需要"多条 Redis 命令原子执行"** 就是 Lua 的主场；只干一件简单的事，就不要为了用而用。

### 7.1 该用 Lua 的场景（√）

| 场景 | 为什么用 Lua | 本项目对应 |
|------|-------------|-----------|
| **判重 + 写入必须原子**（先查再写，防止并发下重复） | Java 里"先 GET 再 SET"两步之间有缝隙，两个请求会同时通过判断 → 用 Lua 一步搞定 | 关注：`follow_check_and_add.lua`（判重 + 上限校验 + ZADD 一条脚本完成）；点赞：`bloom_note_like_check.lua`（BF.EXISTS + BF.ADD） |
| **校验 + 扣减 / 删除必须原子**（比如"余额够不够" + 扣钱） | 分开执行会超扣 / 误删 | 取关：`unfollow_check_and_delete.lua`；取消点赞：`bloom_note_unlike_check.lua` |
| **减少网络往返**（多条命令打包成一次执行） | 一次 Redis 往返替代 N 次，快一个量级 | 批量同步关注/粉丝/点赞 ZSET：`follow_batch_add_and_expire.lua`、`batch_add_note_like_zset_and_expire.lua` |
| **循环批量写 + 设过期** | Java 端写 N 条要 N 次网络，Lua 一次完成 | `bloom_batch_add_note_like_and_expire.lua`（循环 BF.ADD + EXPIRE） |

**核心判断标准一句话**：**这组 Redis 操作是不是"要么全成、要么全不成，且中间不能有别的命令插队"？** 是 → Lua；不是 → 可以不用。

### 7.2 不该用 Lua 的场景（×）

1. **脚本太长 / 循环大集合**：Redis 单线程执行脚本，一个脚本卡 1 秒，整个 Redis 卡 1 秒，所有请求排队——**脚本要短小**；
2. **有 IO / 网络操作**（发 HTTP、读写文件）：脚本里不能做，会阻塞 Redis；
3. **纯复杂业务逻辑**（几十行 if/for）：业务放 Java 更清晰、好测试，Redis 只负责"原子小动作"；
4. **跨多个 key 且集群模式**：脚本内操作多个 key 时，所有 key 必须落在同一哈希槽，否则集群报错（尽量让脚本只动一个 key 族）。

### 7.3 与其它方案怎么搭（一张图理清）

| 方案 | 管什么 | 本项目例子 |
|------|--------|-----------|
| **Lua** | Redis 上的"原子小动作"（判重 + 写、批量 + 过期） | 关注/点赞的 ZSET + 布隆过滤器的判重写入 |
| **Pipeline** | Redis 上的"批量快写"（不保证原子，只省网络） | 批量回填用户缓存 |
| **事务 @Transactional** | 数据库（MySQL）本地事务，跨表操作要么全成要么全回滚 | 关注落库写"关注表 + 粉丝表"两条记录 |
| **MQ + 幂等** | 跨服务 / 异步的最终一致性 | 关注/点赞落库后通知 count 服务计数 |
