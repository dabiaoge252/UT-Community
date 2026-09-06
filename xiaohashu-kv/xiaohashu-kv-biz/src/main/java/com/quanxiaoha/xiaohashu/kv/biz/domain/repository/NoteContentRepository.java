package com.quanxiaoha.xiaohashu.kv.biz.domain.repository;

import com.quanxiaoha.xiaohashu.kv.biz.domain.dataobject.NoteContentDO;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

/**
 * @author: 犬小哈
 * @date: 2024/7/14 16:21
 * @version: v1.0.0
 * @description: TODO
 **/
public interface NoteContentRepository extends CassandraRepository<NoteContentDO, UUID> {
//CassandraRepository: 这是 Spring Data Cassandra 提供的一个泛型接口，它为 Cassandra 数据库提供了 CRUD和其他一些基本的操作方法。
/*<NoteContentDO, UUID>: 这里有两个类型参数：
    NoteContentDO: 表示与 Cassandra 数据库交互时使用的数据对象类型。通常情况下，这是一个 Java 类，它映射到数据库中的表。
    UUID: 表示 NoteContentDO 对象的主键类型。根据表的实际情况来定义，这里使用 UUID 作为主键类型。*/
}

