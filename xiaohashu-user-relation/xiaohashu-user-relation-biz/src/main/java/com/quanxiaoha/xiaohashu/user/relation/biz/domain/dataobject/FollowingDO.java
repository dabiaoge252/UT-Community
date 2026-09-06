package com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class FollowingDO {
    private Long id;

    private Long userId;

    private Long followingUserId;

    private LocalDateTime createTime;



}