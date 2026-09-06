# 数据访问层 Mapper 汇总

> 汇总各服务 `domain/mapper` 下每个 Mapper 方法的用途及对应 SQL（对应 SQL 均在 `src/main/resources/mapper/*.xml` 中）。
> 更新时间：2026-08-12

## 目录

1. [xiaohashu-note（笔记服务）](#1-xiaohashu-note笔记服务)
2. [xiaohashu-user（用户服务）](#2-xiaohashu-user用户服务)
3. [xiaohashu-count（计数服务）](#3-xiaohashu-count计数服务)
4. [xiaohashu-user-relation（用户关系服务）](#4-xiaohashu-user-relation用户关系服务)
5. [xiaohashu-data-align（数据对齐服务）](#5-xiaohashu-data-align数据对齐服务)

> 说明：各 Mapper 均含 MyBatis Generator 生成的"六件套"通用方法（`deleteByPrimaryKey` / `insert` / `insertSelective` / `selectByPrimaryKey` / `updateByPrimaryKeySelective` / `updateByPrimaryKey`），除特别标注外均为标准单表 SQL（按主键 id 操作），不再逐一展开，重点列出自定义方法。

---

## 1. xiaohashu-note（笔记服务）

Mapper 目录：[domain/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-note/xiaohashu-note-biz/src/main/java/com/quanxiaoha/xiaohashu/note/biz/domain/mapper)，XML 目录：[resources/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-note/xiaohashu-note-biz/src/main/resources/mapper)

### 1.1 NoteDOMapper（表 t_note：笔记主表）

> 通用方法中注意两点：`selectByPrimaryKey` 带 `id = ? and status = 1`（仅查有效笔记）；`updateByPrimaryKey` 只更新部分字段（title、topic 等，不含 visible/status/creator_id）。

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `updateVisibleOnlyMe(NoteDO)` | 将笔记可见性改为"仅自己可见" | `update t_note set visible=?, update_time=? where id=? and status=1` |
| `updateIsTop(NoteDO)` | 设置/取消笔记置顶（校验作者本人） | `update t_note set is_top=?, update_time=? where id=? and creator_id=?` |
| `selectCountByNoteId(Long)` | 判断笔记是否存在且有效 | `select count(1) from t_note where id=? and status=1` |
| `selectCreatorIdByNoteId(Long)` | 查询笔记发布者 ID（鉴权） | `select creator_id from t_note where id=? and status=1` |

### 1.2 NoteLikeDOMapper（表 t_note_like：点赞关系，status=1 有效）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `selectCountByUserIdAndNoteId(userId, noteId)` | 判断某用户是否已点赞某笔记 | `select count(1) ... where user_id=? and note_id=? and status=1 limit 1` |
| `selectNoteIsLiked(userId, noteId)` | 同上（SQL 完全相同，冗余方法） | 同上 |
| `selectByUserId(userId)` | 查询用户所有已点赞笔记 ID | `select note_id ... where user_id=? and status=1` |
| `selectLikedByUserIdAndLimit(userId, limit)` | 分页拉取"我赞过的"列表 | `select note_id, create_time ... where user_id=? and status=1 order by create_time desc limit ?` |
| `insertOrUpdate(NoteLikeDO)` | 点赞 upsert（重复点赞刷新时间/状态） | `insert ... values(...) on duplicate key update create_time=?, status=?`（唯一键 user_id+note_id） |
| `update2UnlikeByUserIdAndNoteId(NoteLikeDO)` | 取消点赞（status 置 0，软删除） | `update t_note_like set status=?, create_time=? where user_id=? and note_id=? and status=1` |

### 1.3 NoteCollectionDOMapper（表 t_note_collection：收藏关系，status=1 有效）

结构与 NoteLikeDOMapper 完全对称：

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `selectCountByUserIdAndNoteId(userId, noteId)` | 判断是否已收藏 | `select count(1) ... where user_id=? and note_id=? and status=1 limit 1` |
| `selectNoteIsCollected(userId, noteId)` | 同上（冗余方法） | 同上 |
| `selectByUserId(userId)` | 查询用户全部收藏笔记 ID | `select note_id ... where user_id=? and status=1` |
| `selectCollectedByUserIdAndLimit(userId, limit)` | 分页拉取"我收藏的"列表 | `select note_id, create_time ... where user_id=? and status=1 order by create_time desc limit ?` |
| `insertOrUpdate(NoteCollectionDO)` | 收藏 upsert | `insert ... on duplicate key update create_time=?, status=?`（唯一键 user_id+note_id） |
| `update2UnCollectByUserIdAndNoteId(NoteCollectionDO)` | 取消收藏（status 置 0） | `update t_note_collection set status=?, create_time=? where user_id=? and note_id=? and status=1` |

### 1.4 TopicDOMapper（表 t_topic：话题）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `selectNameByPrimaryKey(Long)` | 按主键查话题名 | `select name from t_topic where id=?` |

### 1.5 ChannelDOMapper / ChannelTopicRelDOMapper

- 表 `t_channel`（频道）、`t_channel_topic_rel`（频道-话题关联）；
- 均只有 MBG 通用六件套，无自定义方法。

---

## 2. xiaohashu-user（用户服务）

Mapper 目录：[domain/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/java/com/quanxiaoha/xiaohashu/user/biz/domain/mapper)，XML 目录：[resources/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user/xiaohashu-user-biz/src/main/resources/mapper)

### 2.1 UserDOMapper（表 t_user）

> `insert` 带 `useGeneratedKeys="true"`（主键回填），其余 Mapper 的 insert 无此配置。

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `selectByPhone(String)` | 按手机号查询（登录校验） | `select id, password from t_user where phone=?` |
| `selectByIds(List<Long> ids)` | 批量查询用户基础信息 | `select id, nickname, avatar, introduction from t_user where status=0 and is_deleted=0 and id in (...)`（foreach IN） |

### 2.2 RoleDOMapper（表 t_role）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `selectEnabledList()` | 查询所有启用角色 | `select id, role_key, role_name from t_role where status=0 and is_deleted=0` |

### 2.3 PermissionDOMapper（表 t_permission）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `selectAppEnabledList()` | 查询 APP 端启用权限 | `select id, name, permission_key from t_permission where status=0 and type=3 and is_deleted=0` |

### 2.4 RolePermissionDOMapper（表 t_role_permission_rel）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `selectByRoleIds(List<Long>)` | 按角色 ID 批量查关联权限 | `select role_id, permission_id from t_role_permission_rel where role_id in (...)`（foreach IN） |

### 2.5 UserRoleDOMapper（表 t_user_role_rel）

- 只有 MBG 通用六件套，无自定义方法。

---

## 3. xiaohashu-count（计数服务）

Mapper 目录：[domain/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-count/xiaohashu-count-biz/src/main/java/com/quanxiaoha/xiaohashu/count/biz/domain/mapper)，XML 目录：[resources/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-count/xiaohashu-count-biz/src/main/resources/mapper)

> 核心特点：计数更新全部使用 MySQL `INSERT ... ON DUPLICATE KEY UPDATE` 原子累加（upsert），依赖 `user_id` / `note_id` 唯一索引，不存在即插入、存在即 `原值 + count`，count 可正可负；避免"先查后更"的并发问题。两表无 `is_deleted` 字段。

### 3.1 NoteCountDOMapper（表 t_note_count：笔记计数）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `insertOrUpdateLikeTotalByNoteId(count, noteId)` | 笔记点赞数累加 | `insert into t_note_count(note_id, like_total) values(?,?) on duplicate key update like_total = like_total + ?` |
| `insertOrUpdateCollectTotalByNoteId(count, noteId)` | 笔记收藏数累加 | 同上，更新 `collect_total = collect_total + ?` |

### 3.2 UserCountDOMapper（表 t_user_count：用户计数）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `insertOrUpdateFansTotalByUserId(count, userId)` | 粉丝总数累加 | upsert，更新 `fans_total` |
| `insertOrUpdateFollowingTotalByUserId(count, userId)` | 关注总数累加 | upsert，更新 `following_total` |
| `insertOrUpdateLikeTotalByUserId(count, userId)` | 用户获赞总数累加 | upsert，更新 `like_total` |
| `insertOrUpdateCollectTotalByUserId(count, userId)` | 用户获收藏总数累加 | upsert，更新 `collect_total` |
| `insertOrUpdateNoteTotalByUserId(Long count, userId)` | 用户发布笔记数累加（注意：count 为 Long，其余为 Integer） | upsert，更新 `note_total` |

---

## 4. xiaohashu-user-relation（用户关系服务）

Mapper 目录：[domain/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/java/com/quanxiaoha/xiaohashu/user/relation/biz/domain/mapper)，XML 目录：[resources/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-user-relation/xiaohashu-user-relation-biz/src/main/resources/mapper)

### 4.1 FansDOMapper（表 t_fans：粉丝关系）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `deleteByUserIdAndFansUserId(userId, fansUserId)` | 删除粉丝关系（取关/移除粉丝） | `delete from t_fans where user_id=? and fans_user_id=?` |
| `selectCountByUserId(userId)` | 查询粉丝总数 | `select count(1) from t_fans where user_id=?` |
| `selectPageListByUserId(userId, offset, limit)` | 分页查询粉丝列表 | `select fans_user_id ... where user_id=? order by create_time desc limit ?,?` |
| `select5000FansByUserId(userId)` | 查询最新 5000 位粉丝（缓存/推送） | `select fans_user_id, create_time ... where user_id=? order by create_time desc limit 5000` |

### 4.2 FollowingDOMapper（表 t_following：关注关系）

| 方法（自定义） | 作用 | SQL 摘要 |
|---|---|---|
| `selectByUserId(userId)` | 查询用户全部关注记录 | `select ... from t_following where user_id=?` |
| `deleteByUserIdAndFollowingUserId(userId, unfollowUserId)` | 删除关注记录（取关） | `delete from t_following where user_id=? and following_user_id=?` |
| `selectCountByUserId(userId)` | 查询关注总数 | `select count(1) from t_following where user_id=?` |
| `selectPageListByUserId(userId, offset, limit)` | 分页查询关注列表 | `select following_user_id ... where user_id=? order by create_time desc limit ?,?` |
| `selectAllByUserId(userId)` | 查询关注用户列表（上限 1000） | `select following_user_id, create_time ... where user_id=? limit 1000` |

---

## 5. xiaohashu-data-align（数据对齐服务）

Mapper 目录：[domain/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/java/com/quanxiaoha/xiaohashu/data/align/domain/mapper)，XML 目录：[resources/mapper](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/resources/mapper)

> 表名规范：`t_data_align_{业务名}_count_temp_${tableNameSuffix}`，后缀 = 日期 + 分片序号（如 `20260812_0`），由调用方传入。除 UpdateMapper 外均为动态表名。

### 5.1 InsertMapper（写日增量临时表：只记"谁变了"）

| 方法 | 作用 | SQL 摘要 |
|---|---|---|
| `insert2DataAlignNoteLikeCountTempTable(suffix, noteId)` | 记录"笔记点赞数"变更 | `insert into t_data_align_note_like_count_temp_${suffix}(note_id) values(?)` |
| `insert2DataAlignUserLikeCountTempTable(suffix, userId)` | 记录"用户被点赞数"变更 | 同上，列 `(user_id)` |
| `insert2DataAlignNoteCollectCountTempTable(suffix, noteId)` | 记录"笔记收藏数"变更 | 同上，列 `(note_id)` |
| `insert2DataAlignUserCollectCountTempTable(suffix, userId)` | 记录"用户被收藏数"变更 | 同上，列 `(user_id)` |
| `insert2DataAlignUserNotePublishCountTempTable(suffix, userId)` | 记录"用户发布笔记数"变更 | 同上，列 `(user_id)` |
| `insert2DataAlignUserFollowingCountTempTable(suffix, userId)` | 记录"用户关注数"变更 | 同上，列 `(user_id)` |
| `insert2DataAlignUserFansCountTempTable(suffix, userId)` | 记录"用户粉丝数"变更 | 同上，列 `(user_id)` |

### 5.2 SelectMapper（对齐任务读侧）

| 方法 | 作用 | SQL 摘要 |
|---|---|---|
| `selectBatchFromDataAlignFollowingCountTempTable(suffix, batchSize)` | 批量取关注数临时表前 N 个 user_id | `select user_id ... order by id limit ?` |
| `selectCountFromFollowingTableByUserId(userId)` | 从源表 t_following 统计关注总数 | `select count(*) from t_following where user_id=?` |
| `selectBatchFromDataAlignNoteLikeCountTempTable(suffix, batchSize)` | 批量取笔记点赞数临时表前 N 个 note_id | `select note_id ... order by id limit ?` |
| `selectCountFromNoteLikeTableByUserId(noteId)` | 从源表 t_note_like 统计笔记有效点赞总数 | `select count(*) from t_note_like where note_id=? and status=1` |
| `selectBatchFromDataAlignNoteCollectCountTempTable(suffix, batchSize)` | 批量取笔记收藏数临时表前 N 个 note_id | `select note_id ... order by id limit ?` |
| `selectCountFromNoteCollectionTableByNoteId(noteId)` | 从源表 t_note_collection 统计笔记有效收藏总数 | `select count(*) from t_note_collection where note_id=? and status=1` |
| `selectBatchFromDataAlignUserLikeCountTempTable(suffix, batchSize)` | 批量取用户获赞数临时表前 N 个 user_id | `select user_id ... order by id limit ?` |
| `selectCountFromUserLikeTableByUserId(userId)` | 统计用户发布的笔记获得的点赞总数 | `select count(*) from t_note_like nl join t_note n on nl.note_id=n.id where n.creator_id=? and nl.status=1` |
| `selectBatchFromDataAlignUserCollectCountTempTable(suffix, batchSize)` | 批量取用户获收藏数临时表前 N 个 user_id | `select user_id ... order by id limit ?` |
| `selectCountFromUserCollectTableByUserId(userId)` | 统计用户发布的笔记获得的收藏总数 | `select count(*) from t_note_collection nc join t_note n on nc.note_id=n.id where n.creator_id=? and nc.status=1` |
| `selectBatchFromDataAlignFansCountTempTable(suffix, batchSize)` | 批量取粉丝数临时表前 N 个 user_id | `select user_id ... order by id limit ?` |
| `selectCountFromFansTableByUserId(userId)` | 从源表 t_fans 统计粉丝总数 | `select count(*) from t_fans where user_id=?` |
| `selectBatchFromDataAlignNotePublishCountTempTable(suffix, batchSize)` | 批量取发布笔记数临时表前 N 个 user_id | `select user_id ... order by id limit ?` |
| `selectCountFromNoteTableByCreatorId(userId)` | 统计用户已发布的笔记总数 | `select count(*) from t_note where creator_id=? and status=1` |

### 5.3 UpdateMapper（回写计数表：固定表名）

| 方法 | 作用 | SQL 摘要 |
|---|---|---|
| `updateUserFollowingTotalByUserId(userId, followingTotal)` | 回写用户关注总数 | `update t_user_count set following_total=? where user_id=?` |
| `updateNoteLikeTotalByUserId(noteId, noteLikeTotal)` | 回写笔记点赞总数 | `update t_note_count set like_total=? where note_id=?` |
| `updateNoteCollectTotalByNoteId(noteId, noteCollectTotal)` | 回写笔记收藏总数 | `update t_note_count set collect_total=? where note_id=?` |
| `updateUserLikeTotalByUserId(userId, userLikeTotal)` | 回写用户获得的点赞总数 | `update t_user_count set like_total=? where user_id=?` |
| `updateUserCollectTotalByUserId(userId, userCollectTotal)` | 回写用户获得的收藏总数 | `update t_user_count set collect_total=? where user_id=?` |
| `updateUserFansTotalByUserId(userId, fansTotal)` | 回写用户粉丝总数 | `update t_user_count set fans_total=? where user_id=?` |
| `updateUserNoteTotalByUserId(userId, noteTotal)` | 回写用户已发布笔记总数 | `update t_user_count set note_total=? where user_id=?` |

### 5.4 DeleteMapper（对齐完成后清理临时表记录）

| 方法 | 作用 | SQL 摘要 |
|---|---|---|
| `batchDeleteDataAlignFollowingCountTempTable(suffix, userIds)` | 按 userId 集合批量删除关注数临时表记录 | `delete ... where user_id in (...)`（foreach） |
| `batchDeleteDataAlignNoteLikeCountTempTable(suffix, noteIds)` | 按 noteId 集合批量删除笔记点赞数临时表记录 | `delete ... where note_id in (...)`（foreach） |
| `batchDeleteDataAlignNoteCollectCountTempTable(suffix, noteIds)` | 按 noteId 集合批量删除笔记收藏数临时表记录 | `delete ... where note_id in (...)`（foreach） |
| `batchDeleteDataAlignUserLikeCountTempTable(suffix, userIds)` | 按 userId 集合批量删除用户获赞数临时表记录 | `delete ... where user_id in (...)`（foreach） |
| `batchDeleteDataAlignUserCollectCountTempTable(suffix, userIds)` | 按 userId 集合批量删除用户获收藏数临时表记录 | `delete ... where user_id in (...)`（foreach） |
| `batchDeleteDataAlignFansCountTempTable(suffix, userIds)` | 按 userId 集合批量删除粉丝数临时表记录 | `delete ... where user_id in (...)`（foreach） |
| `batchDeleteDataAlignNotePublishCountTempTable(suffix, userIds)` | 按 userId 集合批量删除发布笔记数临时表记录 | `delete ... where user_id in (...)`（foreach） |

### 5.5 CreateTableMapper（自动建表：7 类 × 分片）

均为 `CREATE TABLE IF NOT EXISTS`，InnoDB/utf8mb4，结构为 `id` 主键 + 业务 ID 列 + 唯一键（[CreateTableMapper.xml](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/resources/mapper/CreateTableMapper.xml)）：

| 方法 | 创建表 | 唯一键 |
|---|---|---|
| `createDataAlignFollowingCountTempTable` | `t_data_align_following_count_temp_${suffix}` | uk_user_id(user_id) |
| `createDataAlignFansCountTempTable` | `t_data_align_fans_count_temp_${suffix}` | uk_user_id(user_id) |
| `createDataAlignNoteCollectCountTempTable` | `t_data_align_note_collect_count_temp_${suffix}` | uk_note_id(note_id) |
| `createDataAlignUserCollectCountTempTable` | `t_data_align_user_collect_count_temp_${suffix}` | uk_user_id(user_id) |
| `createDataAlignUserLikeCountTempTable` | `t_data_align_user_like_count_temp_${suffix}` | uk_user_id(user_id) |
| `createDataAlignNoteLikeCountTempTable` | `t_data_align_note_like_count_temp_${suffix}` | uk_note_id(note_id) |
| `createDataAlignNotePublishCountTempTable` | `t_data_align_note_publish_count_temp_${suffix}` | uk_user_id(user_id) |

### 5.6 DeleteTableMapper（清理过期临时表：7 类 × 分片）

均为 `DROP TABLE IF EXISTS`（[DeleteTableMapper.xml](file:///d:/java/xiaohashu/xiaohashu/xiaohashu-data-align/src/main/resources/mapper/DeleteTableMapper.xml)）：`deleteDataAlignFollowingCountTempTable`、`deleteDataAlignFansCountTempTable`、`deleteDataAlignNoteCollectCountTempTable`、`deleteDataAlignUserCollectCountTempTable`、`deleteDataAlignUserLikeCountTempTable`、`deleteDataAlignNoteLikeCountTempTable`、`deleteDataAlignNotePublishCountTempTable`，参数均为 `tableNameSuffix`。
