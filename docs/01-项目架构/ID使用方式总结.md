# 项目中 ID 的使用方式梳理（Long 数字型 vs UUID）

> 梳理项目里所有 ID 字段**用的是什么类型、存在哪个库、由谁生成、为什么这样选**。
> 结论先行：**关系库（MySQL）主键与业务实体用 Long 数字型（分布式 ID 服务生成），KV 库（Cassandra）与"内容引用"用 UUID。**

---

## 一、ID 使用总览

| 数据 / 字段 | 类型 | 存储位置 | 由谁生成 | 代码位置 |
|------------|------|---------|---------|---------|
| 用户 ID（`t_user.id`） | `BIGINT` / `Long` | MySQL | Leaf **号段**服务（`leaf-segment-user-id`） | [UserServiceImpl.register](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/java/com/quanxiaoha/xiaohashu/user/biz/service/impl/UserServiceImpl.java#L90-L94) |
| 小哈书号（`t_user.xiaohashu_id`） | `VARCHAR` / `String`（数字串） | MySQL | Leaf **号段**服务（`leaf-segment-xiaohashu-id`） | 同上 |
| 角色 / 权限 / 用户角色关联表主键 | `BIGINT` / `Long` | MySQL | 手工维护 / 自增 | `RoleDO`、`PermissionDO`、`UserRoleDO`、`RolePermissionDO` |
| 笔记 ID（`t_note.id`） | `BIGINT` / `Long` | MySQL | Leaf **雪花**服务（`getSnowflakeId`） | [NoteServiceImpl.publishNote](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-note/xiaohashu-note-biz/src/main/java/com/quanxiaoha/xiaohashu/note/biz/service/impl/NoteServiceImpl.java#L101) |
| 笔记创建者 / 话题（`creator_id` / `topic_id`） | `BIGINT` / `Long` | MySQL | 用户 ID（上下文）/ 手工 | `NoteDO` |
| 笔记内容引用（`t_note.content_uuid`） | `VARCHAR` / `String` | MySQL（仅存引用） | `UUID.randomUUID()` | [NoteServiceImpl](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-note/xiaohashu-note-biz/src/main/java/com/quanxiaoha/xiaohashu/note/biz/service/impl/NoteServiceImpl.java#L113) |
| 笔记内容主键（`note_content.id`） | `UUID` | **Cassandra** | 调用方传入（note 服务生成的 UUID） | `NoteContentDO`（`@PrimaryKey UUID id`） |
| 分布式 ID 号段标识（`leaf_alloc.biz_tag`） | `VARCHAR` / `String` | MySQL（leaf 库） | 业务方约定（如 `leaf-segment-user-id`） | `LeafAlloc.key` |
| 雪花 workerId | `Long` | ZooKeeper | ZK 分配 | `SnowflakeZookeeperHolder` |
| Redis：`user:roles:{userId}` | `Long` 拼进 key | Redis | 用户 ID | `RedisKeyConstants.buildUserRoleKey(Long)` |
| Redis：`role:permissions:{roleKey}` | `String` 拼进 key | Redis | 角色 key | `buildRolePermissionsKey(String)` |
| Redis：`verification_code:{phone}` | `String` 拼进 key | Redis | 手机号 | auth `RedisKeyConstants` |

---

## 二、为什么用 Long 数字型（MySQL / 业务实体）

用户 ID、笔记 ID、话题 ID、角色 ID 等**关系库主键与业务实体 ID** 全部用 `Long`，由**分布式 ID 服务**（美团 Leaf）生成：

**1. 存储与索引更优**

- `BIGINT` 仅 8 字节，`UUID` 占 16 字节；数据量大时主键索引体积、内存占用差距明显。
- MySQL InnoDB 主键是**聚簇索引**：数字型**趋势递增**（号段/雪花都保证），插入时基本追加在索引末尾，页分裂、碎片少，写入性能好。而**随机 UUID 完全无序**，插入会导致随机页分裂、索引碎片化，严重影响写放大。

**2. 查询友好**
- 数字型支持**范围查询、排序、分页**（`where id > ? limit ?` 之类），雪花/号段 ID 本身按时间趋势递增，天然适合按创建时间排序的场景（如笔记列表）。

**3. 便于展示与传参**
- 用户 ID、笔记 ID 是**对外可展示/可传输**的业务标识（如 `GET /note/123`），数字型短小直观，也便于前端与日志排查。

**4. 全局唯一由"分布式 ID 服务"保证，不依赖 DB 自增**
- 微服务/未来分库分表场景下，DB `AUTO_INCREMENT` 无法保证全局唯一；Leaf 号段（DB 预取号段）与雪花（时间戳 + workerId）都能在**高并发、无中心协调**下产出全局唯一且趋势递增的 Long。

**5. Redis key 用 Long userId**
- `user:roles:{userId}` 用数字拼 key，紧凑、可读、与 DB 主键一致，避免冗余转换。

---

## 三、为什么用 UUID（Cassandra / 内容引用）

**1. Cassandra 是分布式写，客户端生成 ID 最合适**
- Cassandra 无主从、无单点分配器。若用 DB 自增或集中式 ID 服务，每次写入都要**先请求 ID 再写入**，多一次网络往返且有中心瓶颈。
- **UUID v4 客户端本地即可生成，全局唯一，零协调开销**，写入吞吐不受 ID 生成瓶颈限制。

**2. 随机 UUID 让数据分布均匀，避免写入热点**
- Cassandra 按 **partition key 哈希**分布数据。若用递增 ID（Long），所有新数据都哈希到少数分区，形成**写入热点**；随机 UUID 天然散列到全集群各分区，写入均衡。

**3. KV 场景不需要排序/范围查询**
- `note_content` 是"按 key 精确读大文本"的 KV 用法，不需要 Long 型 ID 的递增/排序特性，UUID 的随机性在这里是优点而非缺点。

**4. 不可猜测的"内容引用"**
- `t_note.content_uuid` 是笔记内容的**引用标识**：用 `UUID.randomUUID()` 生成、存入 MySQL 元数据表，同时作为 Cassandra 主键。随机 UUID 难以遍历猜测，避免内容被恶意批量抓取。

**5. 实现上的演进**
- kv 服务最初在**服务端自己生成 UUID**（代码里还留着 `TODO` 注释），现在已经演进为**由调用方（note 服务）生成后传入**：`KeyValueRpcService.saveNoteContent(contentUuid, content)` → `AddNoteContentReqDTO.uuid`。这样内容的归属关系由上游业务决定，kv 只做纯 KV 存取。

---

## 四、"笔记内容"为什么是两种 ID 的混合方案

同一条笔记，主键用 **Long（雪花）**，内容引用用 **UUID**，是典型的"**元数据 + 大文本分离存储**"：

```
发布笔记 (note-biz)
   │
   ├─ t_note.id = Leaf 雪花 Long ID          → 存 MySQL（关系数据：标题/图片/作者/话题…）
   │
   └─ content_uuid = UUID.randomUUID()       → 存 MySQL t_note.content_uuid 字段（引用）
        └─ note_content.id = 同一个 UUID     → 存 Cassandra（大文本内容）
```

- **MySQL 管"结构"**：笔记是谁发的、有哪些图、什么话题 → 需要关联查询、分页、排序 → **Long 数字 ID**。
- **Cassandra 管"内容"**：一大段正文 → 只按 key 存取 → **UUID**。
- 好处：冷热/大小分离（大文本不进关系库，避免拖慢 MySQL 查询）、水平扩展互不影响。

---

## 五、ID 生成链路

```
【用户注册】auth 登录 → user-biz register
   → DistributedIdGeneratorRpcService.getUserId() / getXiaohashuId()
   → Feign /id/segment/get/leaf-segment-user-id|xiaohashu-id
   → Leaf 号段模式（leaf 库预取号段）→ 返回 Long 数字串

【笔记发布】note-biz publishNote
   → DistributedIdGeneratorRpcService.getSnowflakeId()  → t_note.id（Long）
   → UUID.randomUUID()                                  → t_note.content_uuid
   → KeyValueRpcService.saveNoteContent(uuid, content)  → Cassandra note_content.id（UUID）
```

---

## 六、注意事项（当前代码里的几个观察点）

1. **kv 服务 DTO 已统一用 `uuid` 字段**（新增/查询/删除均为 String，服务端 `UUID.fromString` 转换），语义一致。
2. **小哈书号虽是 String，内容实为数字**（Leaf 号段生成），`ParamUtils.checkXiaohashuId` 已按"数字/字母/下划线"格式校验；它不是主键，仅是用户可自定义的展示号。
3. **note 服务调雪花接口时 key 传的是固定字符串 `"test"`**，雪花模式下 key 本身不参与 ID 计算（仅日志/路由用），后续若需要按业务区分可换成具体业务标识。
4. 若未来 MySQL 需要**分库分表**，现有 Long 型 ID（Leaf）天然支持，无需改造；这也正是当初不用 DB 自增 + 不用 UUID 的原因之一。
