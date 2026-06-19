package com.innercosmos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innercosmos.entity.EchoCapsule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EchoCapsuleMapper extends BaseMapper<EchoCapsule> {

    default List<EchoCapsule> findByOwner(Long userId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EchoCapsule>()
                .eq("owner_user_id", userId));
    }

    /**
     * IC-CAP-002 B-4 (FIX-3): the user's PUBLIC capsules only. "Public" matches the
     * PersonaChatServiceImpl semantic: is_public = TRUE AND visibility_status = 'PUBLIC'.
     * Used by nightly echo-energy decay, which Spec §4 B-4 scopes to public capsules.
     */
    default List<EchoCapsule> findPublicByOwner(Long userId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EchoCapsule>()
                .eq("owner_user_id", userId)
                .eq("is_public", true)
                .eq("visibility_status", "PUBLIC"));
    }
}
