# Java Stream 流使用总结 —— 零基础版

> 更新时间：2026-08-21
> 适用人群：**没系统学过 Stream** 的开发者。
>
> 整理 Java 8 引入的 Stream API 的**核心概念**、**常用方法**、**API 速查表**与**项目中的真实示例**，写代码时快速查阅。
> 每个概念都有"大白话"解释，方法表格完整保留，示例尽量取自本项目真实代码。

## 目录

1. [先读 30 秒：什么是 Stream](#intro)
2. [一、Stream 的核心概念](#ch1)
3. [二、创建 Stream](#ch2)
4. [三、中间操作（Intermediate Operations）](#ch3)
5. [四、终止操作（Terminal Operations）](#ch4)
6. [五、Collectors 收集器详解](#ch5)
7. [六、常用 API 速查表](#ch6)
8. [七、项目中的真实 Stream 示例](#ch7)
9. [八、常见坑与使用贴士](#ch8)

---

## <a id="intro"></a>先读 30 秒：什么是 Stream

写 Java 时你经常要干这些事：**把一个列表里的每个元素转成另一种类型**（比如把 DO 列表转成 ID 列表）、**过滤掉不想要的元素**、**按某个字段分组**、**拼接成字符串**……用传统 `for` 循环写，代码又长又啰嗦：

```java
// 传统写法：把用户列表里的 ID 取出来
List<Long> userIds = new ArrayList<>();
for (UserDO user : userDOList) {
    userIds.add(user.getId());
}
```

用 Stream 一行搞定：

```java
// Stream 写法
List<Long> userIds = userDOList.stream().map(UserDO::getId).toList();
```

**Stream（流）** 就是 JDK 提供的一套"**对集合批量处理的声明式 API**"：你不用告诉它"怎么一步步循环"，只需要告诉它"**要做什么**"（过滤、转换、聚合），剩下的交给它。

一句话：**Stream = 用"流水线"的方式处理集合，代码更短、更可读、更不容易写错**。

---

## 一、Stream 的核心概念

> **这是什么**：用 Stream 前，先理解它最重要的 4 个特性，否则容易踩坑。

1. **流是一次性的（单次消费）**
   一个 Stream 只能被"消费"一次，用完即废。再想用必须重新创建。
   
   ```java
   Stream<String> s = list.stream();
   s.forEach(System.out::println);
   s.forEach(System.out::println); // ❌ 报错：stream has already been operated upon or closed
   ```
   
2. **流不会修改原始集合**
   Stream 的所有操作都是"读取 + 产出新结果"，原集合元素不变（元素本身是引用类型时，修改其属性另说）。

3. **惰性求值（延迟执行）**
   中间操作只是"登记需求"，**不立即执行**；只有遇到**终止操作**时才会真正开始计算。所以光写 `list.stream().filter(...)` 不接终止操作，等于白写。
   ```java
   list.stream().filter(x -> x > 3);  // 什么都不发生
   list.stream().filter(x -> x > 3).toList();  // 此时才真正执行
   ```

4. **流水线三段式：创建 → 中间操作（0~N 个）→ 终止操作（1 个）**
   
   ```java
   源数据.stream()          // ① 创建流
        .filter(...)        // ② 中间操作，可链式多个
        .map(...)           // ② 中间操作
        .collect(...);      // ③ 终止操作，触发执行，返回结果
   ```

---

## 二、创建 Stream

| 写法 | 作用 | 示例 |
|------|------|------|
| `集合.stream()` | 把集合（`List`/`Set`/`Map.keySet()` 等）转成串行流 | `list.stream()` |
| `集合.parallelStream()` | 转成并行流（多线程处理，小数据别用） | `list.parallelStream()` |
| `Arrays.stream(数组)` | 把数组转成流 | `Arrays.stream(args)` |
| `Stream.of(元素...)` | 直接用一个或多个元素造流 | `Stream.of("a", "b", "c")` |
| `Stream.iterate(种子, 操作)` | 无限流（常配 `limit` 使用） | `Stream.iterate(0, n -> n + 1)` |
| `Stream.generate(Supplier)` | 无限流（常配 `limit` 使用） | `Stream.generate(Math::random)` |
| `Map` 的流 | Map 本身不能直接 stream，用 `entrySet()` / `keySet()` / `values()` | `map.entrySet().stream()` |

---

## 三、中间操作（Intermediate Operations）

> **这是什么**：中间操作只负责"加工"，返回值仍是 Stream，可以无限链式调用。**注意：它们不会真正执行，直到遇到终止操作。**

### 3.1 `filter` —— 过滤（筛选）

按条件保留元素，`true` 保留、`false` 丢弃。

| 方法 | 作用 |
|------|------|
| `filter(Predicate<T>)` | 过滤掉不满足条件的元素 |

**示例**（项目：过滤掉 Redis 批量查询结果中的 null）：

```java
redisValues.stream()
        .filter(Objects::nonNull)   // 只保留非 null 元素
        .toList();
```

### 3.2 `map` —— 转换（映射）

把每个元素**转成另一种类型**，是最常用的操作。

| 方法 | 作用 |
|------|------|
| `map(Function<T, R>)` | 每个元素经过函数转换，变成新类型 R |
| `mapToInt` / `mapToLong` / `mapToDouble` | 转成基本类型流（可用于求和等） |

**示例**（项目：把用户 DO 列表转成用户 ID 列表）：

```java
List<Long> userIds = followingDOS.stream()
        .map(FollowingDO::getFollowingUserId)  // 方法引用等价于 f -> f.getFollowingUserId()
        .toList();
```

### 3.3 `flatMap` —— 扁平化

把"流里的每个元素又是一个流"摊平成一个流。典型场景：`List<List<T>>` → `List<T>`。

| 方法 | 作用 |
|------|------|
| `flatMap(Function<T, Stream<R>>)` | 每个元素展开成流，再合并成一个流 |

**示例**（把多篇文章的标签列表摊平成一个标签列表）：

```java
List<List<String>> articleTags = ...;
List<String> allTags = articleTags.stream()
        .flatMap(List::stream)   // 每个 List<String> 展开成流，再合并
        .distinct()              // 去重
        .toList();
```

### 3.4 `distinct` —— 去重

依赖元素的 `equals/hashCode` 去重。

### 3.5 `sorted` —— 排序

| 方法 | 作用 |
|------|------|
| `sorted()` | 自然排序（元素需实现 `Comparable`） |
| `sorted(Comparator<T>)` | 按自定义比较器排序 |

**示例**：

```java
list.stream()
        .sorted(Comparator.comparing(UserDO::getCreateTime).reversed())  // 按创建时间倒序
        .toList();
```

### 3.6 `limit` / `skip` —— 截取 / 跳过

| 方法 | 作用 |
|------|------|
| `limit(n)` | 只取前 n 个元素 |
| `skip(n)` | 跳过前 n 个元素 |

两者配合就是最简单的"分页"：

```java
list.stream()
        .skip((pageNum - 1) * pageSize)  // 跳过前面页的数据
        .limit(pageSize)                  // 取本页数据
        .toList();
```

### 3.7 `peek` —— 偷看（调试用）

对每个元素做点副作用（如打日志），但不改变元素。**生产代码慎用，主要用来调试中间状态**。

```java
list.stream()
        .peek(x -> System.out.println("过滤前: " + x))
        .filter(x -> x > 3)
        .peek(x -> System.out.println("过滤后: " + x))
        .toList();
```

---

## <a id="ch4"></a>四、终止操作（Terminal Operations）

> **这是什么**：终止操作**触发整个流水线真正执行**，执行完流就没了。它的返回值**不是** Stream，而是最终结果。

### 4.1 `forEach` —— 遍历执行

对每个元素执行动作，**无返回值**。

```java
list.stream().forEach(item -> System.out.println(item));
```

> ⚠️ 提醒：仅遍历用 `list.forEach(...)` 就行，`Collection` 本身就有 `forEach`，不用特意先 `.stream()`。Stream 里的 `forEach` 主要用在链式中间操作之后。

### 4.2 `collect` —— 收集（最重要）

把流里的元素"收集"成集合或其他结构，配合 `Collectors` 工具类使用，详见第五节 Collectors 收集器。

```java
List<String> result = list.stream().filter(...).collect(Collectors.toList());
```

### 4.3 `toList()` —— Java 16+ 便捷写法

`Stream.toList()` 是 JDK 16 新增的快捷方法，等价于 `collect(Collectors.toUnmodifiableList())`（**返回不可变 List**）。项目用的 Java 17，直接用没问题：

```java
List<Long> userIds = userDOList.stream().map(UserDO::getId).toList();
```

> ⚠️ 区别：`collect(Collectors.toList())` 返回**可变**列表；`toList()` 返回**不可变**列表，后续想 `add` 会抛 `UnsupportedOperationException`。

### 4.4 匹配 —— `anyMatch` / `allMatch` / `noneMatch`

| 方法 | 作用 | 返回 |
|------|------|------|
| `anyMatch(条件)` | 只要**有一个**满足就返回 true（短路） | `boolean` |
| `allMatch(条件)` | **全部**满足才返回 true | `boolean` |
| `noneMatch(条件)` | **全部**不满足才返回 true | `boolean` |

```java
boolean hasAdmin = userList.stream().anyMatch(u -> "admin".equals(u.getRole()));
```

### 4.5 查找 —— `findFirst` / `findAny`

| 方法 | 作用 |
|------|------|
| `findFirst()` | 返回**第一个**元素（`Optional<T>`） |
| `findAny()` | 返回**任意一个**元素（并行流下性能更好，`Optional<T>`） |

配合 `orElse` 取默认值：

```java
UserDO first = userList.stream()
        .filter(u -> "admin".equals(u.getRole()))
        .findFirst()
        .orElse(null);  // 没找到返回 null（或给个默认对象）
```

### 4.6 聚合 —— `count` / `min` / `max` / `reduce`

| 方法 | 作用 |
|------|------|
| `count()` | 元素个数（`long`） |
| `min(Comparator)` / `max(Comparator)` | 最小 / 最大元素（`Optional<T>`） |
| `reduce(初值, (a, b) -> ...)` | 把元素逐一带入累积成一个结果（求和、求积、拼字符串等） |

```java
long count = list.stream().count();
int sum = numbers.stream().reduce(0, Integer::sum);   // 求和（初值 0）
```

> 简单数值求和使用 `mapToInt(...).sum()` 更直观：`numbers.stream().mapToInt(Integer::intValue).sum()`。

### 4.7 数值流快捷聚合

| 写法 | 作用 |
|------|------|
| `mapToInt(x -> ...).sum()` | 求和 |
| `mapToInt(x -> ...).average()` | 平均值（`OptionalDouble`） |
| `mapToInt(x -> ...).max()` / `.min()` | 最大 / 最小值（`OptionalInt`） |

```java
long totalLikes = noteList.stream()
        .mapToLong(NoteDO::getLikeCount)
        .sum();
```

---

## 五、Collectors 收集器详解

> **这是什么**：`collect(Collectors.xxx)` 里的 `Collectors` 是一个"百宝箱"，提供各种收集规则。下面是项目里用得到的几种。

### 5.1 `toList` / `toSet` —— 收集成 List / Set

```java
List<String> list = stream.collect(Collectors.toList());
Set<String> set = stream.collect(Collectors.toSet());   // 顺带去重
```

### 5.2 `toMap` —— 收集成 Map

**项目示例**（把权限 DO 列表转成 `id → 对象` 的 Map，方便 O(1) 查找）：

```java
Map<Long, PermissionDO> permissionIdDOMap = permissionDOS.stream()
        .collect(Collectors.toMap(PermissionDO::getId, permissionDO -> permissionDO));
```

> ⚠️ 坑：`toMap` 遇到**重复 key 会直接抛异常**（`IllegalStateException: Duplicate key`）。确定可能重复时用三参重载指定合并策略（见本小节的注意）：
> ```java
> Collectors.toMap(PermissionDO::getId, p -> p, (oldV, newV) -> newV)  // 重复时取后者
> ```

### 5.3 `groupingBy` —— 分组

按某个字段分组，得到 `Map<分组字段类型, List<元素>>`，**项目中最常用的收集器**。

**项目示例**（MQ 消息按目标用户分组，凑一批批量入库）：

```java
Map<Long, List<CountFollowUnfollowMqDTO>> groupMap = msgList.stream()
        .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getTargetUserId));
```

**组合用法**（分组 + 映射：按角色分组，把每组只保留权限 ID）：

```java
Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOS.stream().collect(
        Collectors.groupingBy(RolePermissionDO::getRoleId,      // 按 roleId 分组
                Collectors.mapping(RolePermissionDO::getPermissionId,  // 组内只取 permissionId
                        Collectors.toList())));                        // 收集成 List
```

### 5.4 `joining` —— 拼接字符串

把元素用分隔符拼成一个字符串（元素自动调用 `toString`）。

**项目示例**（把接口入参数组拼成日志字符串）：

```java
String argsJsonStr = Arrays.stream(args)
        .map(toJsonStr())
        .collect(Collectors.joining(", "));
```

### 5.5 `mapping` —— 组内转换

在分组（`groupingBy`）或分区（`partitioningBy`）**内部**先对元素做一次转换，再收集。见上面 5.3 的组合用法。

### 5.6 `partitioningBy` —— 按 true/false 分区

按条件把元素分成"满足"和"不满足"两组，返回 `Map<Boolean, List<T>>`。

```java
Map<Boolean, List<UserDO>> part = userList.stream()
        .collect(Collectors.partitioningBy(u -> u.getStatus() == 1));
part.get(true);   // 启用用户
part.get(false);  // 禁用用户
```

### 5.7 其他常用

| 方法 | 作用 |
|------|------|
| `Collectors.counting()` | 计数 |
| `Collectors.summingLong(函数)` | 对某字段求和 |
| `Collectors.averagingDouble(函数)` | 对某字段求平均 |
| `Collectors.toCollection(构造器)` | 收集成指定类型集合（如 `LinkedList::new`） |
| `Collectors.joining()` / `joining(",")` / `joining(",", "[", "]")` | 无分隔符 / 带分隔符 / 带前后缀拼接 |

---

## <a id="ch6"></a>六、常用 API 速查表

> 对着这张表找方法，拿不准再回看上面小节。

| 分类 | 方法 | 作用 | 是否触发执行 |
|------|------|------|:---:|
| 创建 | `集合.stream()` | 集合转流 | - |
| 创建 | `Arrays.stream(数组)` | 数组转流 | - |
| 创建 | `Stream.of(元素...)` | 元素直接造流 | - |
| 中间 | `filter(条件)` | 过滤，保留满足条件的 | ❌ |
| 中间 | `map(转换)` | 每个元素转成新类型 | ❌ |
| 中间 | `flatMap(展开)` | 摊平嵌套流 | ❌ |
| 中间 | `distinct()` | 去重 | ❌ |
| 中间 | `sorted()` / `sorted(比较器)` | 排序 | ❌ |
| 中间 | `limit(n)` | 取前 n 个 | ❌ |
| 中间 | `skip(n)` | 跳过前 n 个 | ❌ |
| 中间 | `peek(动作)` | 偷看/调试，不改变元素 | ❌ |
| 终止 | `forEach(动作)` | 遍历执行，无返回 | ✅ |
| 终止 | `collect(Collectors.xxx)` | 收集成集合/Map/字符串 | ✅ |
| 终止 | `toList()` | 收集成不可变 List（Java 16+） | ✅ |
| 终止 | `toArray()` | 收集成数组 | ✅ |
| 终止 | `count()` | 计数 | ✅ |
| 终止 | `anyMatch / allMatch / noneMatch` | 匹配判断，返回 boolean | ✅ |
| 终止 | `findFirst() / findAny()` | 查找元素，返回 Optional | ✅ |
| 终止 | `min() / max()` | 最小/最大 | ✅ |
| 终止 | `reduce(初值, 累积)` | 累积计算（求和等） | ✅ |
| 终止 | `mapToInt/Long/Double + sum/avg/max/min` | 数值快捷聚合 | ✅ |
| 收集器 | `Collectors.toList() / toSet()` | 收集成 List / Set | ✅ |
| 收集器 | `Collectors.toMap(key, value)` | 收集成 Map（key 重复会抛异常） | ✅ |
| 收集器 | `Collectors.groupingBy(分组)` | 按字段分组 | ✅ |
| 收集器 | `Collectors.mapping(转换, 收集)` | 组内转换后再收集 | ✅ |
| 收集器 | `Collectors.partitioningBy(条件)` | 按 true/false 分区 | ✅ |
| 收集器 | `Collectors.joining(分隔符)` | 拼接字符串 | ✅ |

---

## <a id="ch7"></a>七、项目中的真实 Stream 示例

> 项目里已经大量使用 Stream，这里挑几个典型场景，照着写即可。

### 7.1 对象列表 → 某字段 ID 列表（map + toList）

[RelationServiceImpl.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/service/impl/RelationServiceImpl.java#L355)

```java
// 把关注列表里的"被关注用户 ID"全部取出来
List<Long> userIds = followingDOS.stream()
        .map(FollowingDO::getFollowingUserId)
        .toList();
```

### 7.2 过滤 null + 去重（filter）

[UserServiceImpl.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/java/com/quanxiaoha/xiaohashu/user/biz/service/impl/UserServiceImpl.java#L380)

```java
// Redis multiGet 批量查询，结果里可能混入 null，过滤掉
redisValues = redisValues.stream()
        .filter(Objects::nonNull)
        .toList();
```

### 7.3 按角色分组 + 组内映射（groupingBy + mapping）

[PushRolePermissions2RedisRunner.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/java/com/quanxiaoha/xiaohashu/user/biz/runner/PushRolePermissions2RedisRunner.java#L88-L90)

```java
// 角色-权限关联表 -> 按角色分组，组内只保留权限 ID
Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOS.stream().collect(
        Collectors.groupingBy(RolePermissionDO::getRoleId,
                Collectors.mapping(RolePermissionDO::getPermissionId, Collectors.toList()))
);
```

### 7.4 列表转 Map，方便 O(1) 查找（toMap）

[PushRolePermissions2RedisRunner.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/java/com/quanxiaoha/xiaohashu/user/biz/runner/PushRolePermissions2RedisRunner.java#L97-L98)

```java
Map<Long, PermissionDO> permissionIdDOMap = permissionDOS.stream()
        .collect(Collectors.toMap(PermissionDO::getId, permissionDO -> permissionDO));
```

### 7.5 字符串拼接（joining）

[ApiOperationLogAspect.java](file:///d:/java/xiaohashu/xiaohashu/xiaoha-framework/xiaoha-spring-boot-starter-biz-operationlog/src/main/java/com/quanxiaoha/framework/biz/operationlog/aspect/ApiOperationLogAspect.java#L45)

```java
// 把方法入参数组拼成日志里的参数字符串
String argsJsonStr = Arrays.stream(args)
        .map(toJsonStr())
        .collect(Collectors.joining(", "));
```

### 7.6 MQ 消息分组批量入库（groupingBy）

[CountFansConsumer.java](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-count/xiaohashu-count-biz/src/main/java/com/quanxiaoha/xiaohashu/count/biz/consumer/CountFansConsumer.java#L70)

```java
// 攒了一波关注/取关消息，按目标用户分组，方便按用户批量更新计数
Map<Long, List<CountFollowUnfollowMqDTO>> groupMap = list.stream()
        .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getTargetUserId));
```

---

## <a id="ch8"></a>八、常见坑与使用贴士

> **这是什么**：Stream 用起来顺手，但有几个经典的坑，写的时候对照自查。

1. **流只能用一次**：同一 Stream 不能消费两次，否则抛 `IllegalStateException`。需要重复处理就重新 `.stream()`。
2. **`toList()` 返回不可变列表**：后续 `add/remove` 会抛 `UnsupportedOperationException`。需要可变集合就用 `collect(Collectors.toList())` 或 `new ArrayList<>(stream.toList())`。
3. **`toMap` 遇到重复 key 抛异常**：key 可能重复时用三参重载 `(oldV, newV) -> newV` 指定合并策略（见 5.2 小节）。
4. **中间操作不写终止操作 = 白写**：惰性求值，没有终止操作，中间操作一个都不会执行，且没有返回值（编译不报错，纯属浪费）。
5. **`peek` 不是 `forEach`**：`peek` 是中间操作、可能不执行；`forEach` 是终止操作、必然执行。调试时注意。
6. **基本类型用对应流避免拆箱开销**：`int` 大量数据用 `mapToInt`，否则自动装箱/拆箱有性能损耗。
7. **`parallelStream()` 慎用**：并行流会引入多线程竞争和上下文切换，小数据、有状态操作（依赖外部变量累加）反而更慢更容易出并发 bug。需要并行先想清楚线程安全。
8. **流里别放 null**：部分操作（如 `toMap`、`sorted`）遇到 null 元素会 NPE。项目里常见做法是先 `filter(Objects::nonNull)`（见 7.2 小节）。
9. **Lambda 捕获的变量必须是 effectively final**：在 lambda 里引用外部变量，该变量不能在后面被重新赋值，否则编译报错。需要"变化"的值就声明一个新变量。
10. **优先方法引用**：`x -> x.getId()` 写成 `UserDO::getId` 更简洁；`x -> new ArrayList<>(x)` 可写成 `ArrayList::new`。
