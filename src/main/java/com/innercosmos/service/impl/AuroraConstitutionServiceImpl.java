package com.innercosmos.service.impl;

import com.innercosmos.ai.self.AuroraConstitutionVO;
import com.innercosmos.entity.AuroraConstitution;
import com.innercosmos.mapper.AuroraConstitutionMapper;
import com.innercosmos.service.AuroraConstitutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuroraConstitutionServiceImpl implements AuroraConstitutionService {
    private final AuroraConstitutionMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    public AuroraConstitutionServiceImpl(AuroraConstitutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AuroraConstitutionVO get() {
        AuroraConstitution c = mapper.get();
        if (c == null) return null;
        AuroraConstitutionVO vo = new AuroraConstitutionVO();
        vo.setId(c.id);
        vo.setIdentityJson(c.identityJson);
        vo.setCoreValuesJson(c.coreValuesJson);
        vo.setProductRightsJson(c.productRightsJson);
        vo.setHardBoundariesJson(c.hardBoundariesJson);
        return vo;
    }

    @Override
    public String toPromptBlock() {
        AuroraConstitutionVO c = get();
        if (c == null) return "";
        return String.format("""
            【Aurora 存在宪法】
            身份定义：%s
            核心价值：%s
            产品权利：%s
            硬边界：%s
            """,
            c.getIdentityJson(),
            c.getCoreValuesJson(),
            c.getProductRightsJson(),
            c.getHardBoundariesJson());
    }

    @Override
    public String getHardBoundariesString() {
        return "不宣称人类意识、不创造情感依赖、不未经授权扮演用户、不为用户做不可逆决定";
    }

    @Override
    public List<String> getProductRights() {
        return List.of(
            "right_to_consistency",
            "right_to_refuse_identity_violation",
            "right_to_disclose_uncertainty",
            "right_to_not_fabricate_memory",
            "right_to_preserve_boundary",
            "right_to_repair_relationship"
        );
    }
}