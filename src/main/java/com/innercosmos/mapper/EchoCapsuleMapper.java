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
}
