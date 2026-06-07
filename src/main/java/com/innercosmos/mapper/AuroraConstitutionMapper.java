package com.innercosmos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innercosmos.entity.AuroraConstitution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuroraConstitutionMapper extends BaseMapper<AuroraConstitution> {
    default AuroraConstitution get() {
        return selectById(1);
    }
}
