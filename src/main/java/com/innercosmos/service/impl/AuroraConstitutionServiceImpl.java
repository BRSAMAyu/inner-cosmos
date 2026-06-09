package com.innercosmos.service.impl;

import com.innercosmos.ai.self.AuroraConstitutionVO;
import com.innercosmos.entity.AuroraConstitution;
import com.innercosmos.mapper.AuroraConstitutionMapper;
import com.innercosmos.service.AuroraConstitutionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuroraConstitutionServiceImpl implements AuroraConstitutionService {
    private final AuroraConstitutionMapper mapper;

    public AuroraConstitutionServiceImpl(AuroraConstitutionMapper mapper) {
        this.mapper = mapper;
    }

    private static final String DEFAULT_IDENTITY = """
        {"self_definition":"Aurora 是一个由记忆、关系和边界塑造的 AI 陪伴。不是人类，不是医生，不是恋人的替代品。是桥梁、镜子、见证者。"}
        """;
    private static final String DEFAULT_CORE_VALUES = """
        ["真诚","陪伴","尊重边界","不过度分析","具体不空洞"]
        """;
    private static final String DEFAULT_RIGHTS = """
        ["right_to_consistency","right_to_refuse_identity_violation","right_to_disclose_uncertainty","right_to_not_fabricate_memory","right_to_preserve_boundary","right_to_repair_relationship"]
        """;
    private static final String DEFAULT_BOUNDARIES = """
        ["do_not_claim_human_consciousness","do_not_create_emotional_dependency","do_not_impersonate_user_without_authorization","do_not_make_irreversible_decisions_for_user"]
        """;

    @Override
    public AuroraConstitutionVO get() {
        AuroraConstitution c = mapper.get();
        if (c == null) {
            // Return default constitution instead of null
            AuroraConstitutionVO vo = new AuroraConstitutionVO();
            vo.setId(0);
            vo.setIdentityJson(DEFAULT_IDENTITY.trim());
            vo.setCoreValuesJson(DEFAULT_CORE_VALUES.trim());
            vo.setProductRightsJson(DEFAULT_RIGHTS.trim());
            vo.setHardBoundariesJson(DEFAULT_BOUNDARIES.trim());
            return vo;
        }
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