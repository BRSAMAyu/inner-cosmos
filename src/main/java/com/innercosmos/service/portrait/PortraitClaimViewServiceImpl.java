package com.innercosmos.service.portrait;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.service.portrait.PortraitClaimViewService.ClaimView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PortraitClaimViewServiceImpl implements PortraitClaimViewService {

    /** The well-known portrait dimensions; absent material shows as UNKNOWN, never a template. */
    private static final List<String> KNOWN_DIMENSIONS = List.of(
            "价值偏好", "支持偏好", "表达习惯", "关系节律", "变化轨迹");

    private final UnderstandingClaimMapper claimMapper;

    public PortraitClaimViewServiceImpl(UnderstandingClaimMapper claimMapper) {
        this.claimMapper = claimMapper;
    }

    @Override
    public PortraitView view(Long userId) {
        List<UnderstandingClaim> active = claimMapper.selectList(
                new QueryWrapper<UnderstandingClaim>()
                        .eq("user_id", userId).eq("status", "ACTIVE")
                        .orderByDesc("version"));
        Map<String, List<UnderstandingClaim>> byKey = new LinkedHashMap<>();
        for (UnderstandingClaim claim : active) {
            byKey.computeIfAbsent(claim.claimKey, k -> new ArrayList<>()).add(claim);
        }
        List<ClaimView> views = new ArrayList<>();
        for (Map.Entry<String, List<UnderstandingClaim>> entry : byKey.entrySet()) {
            List<UnderstandingClaim> rows = entry.getValue();
            rows.sort(Comparator.comparing(c -> c.version == null ? 0 : c.version,
                    Comparator.reverseOrder()));
            for (int i = 0; i < rows.size(); i++) {
                UnderstandingClaim claim = rows.get(i);
                String state;
                if (i == 0 && rows.size() > 1 && !sameValue(claim, rows.get(1))) {
                    // Two live values disagree: newest still wins for use, but the conflict is
                    // surfaced instead of being drowned out by the older majority.
                    state = "CONFLICTING";
                } else if (isUserAuthority(claim.authorityLevel)) {
                    state = "CONFIRMED";
                } else {
                    state = "INFERRED";
                }
                views.add(new ClaimView(claim.id, claim.claimKey, claim.claimType, state,
                        claim.authorityLevel, claim.valueJson,
                        claim.version == null ? null : String.valueOf(claim.version),
                        scopeOf(claim.claimType), claim.sourceType));
            }
        }
        long covered = KNOWN_DIMENSIONS.stream().filter(byKey::containsKey).count();
        int unknown = KNOWN_DIMENSIONS.size() - (int) covered;
        List<ClaimView> suppressedRows = claimMapper.selectList(new QueryWrapper<UnderstandingClaim>()
                        .eq("user_id", userId).eq("status", "SUPPRESSED").orderByDesc("version"))
                .stream().map(claim -> new ClaimView(claim.id, claim.claimKey, claim.claimType,
                        "SUPPRESSED", claim.authorityLevel, claim.valueJson,
                        claim.version == null ? null : String.valueOf(claim.version),
                        scopeOf(claim.claimType), claim.sourceType))
                .toList();
        return new PortraitView(views, Math.max(0, unknown),
                "每项理解都标明来源与状态：已确认/推断/冲突。没有材料的维度显示为未知，"
                        + "不会用固定人格模板补齐，也不存在整体人格分数。",
                suppressedRows);
    }

    private static boolean isUserAuthority(String authorityLevel) {
        return "USER_CORRECTION".equals(authorityLevel) || "USER_CONFIRMED".equals(authorityLevel);
    }

    private static boolean sameValue(UnderstandingClaim a, UnderstandingClaim b) {
        return a.valueJson == null ? b.valueJson == null : a.valueJson.equals(b.valueJson);
    }

    private static String scopeOf(String claimType) {
        if (claimType == null) {
            return "PRIVATE";
        }
        return switch (claimType) {
            case "EXPRESSION_STYLE" -> "CAPSULE_RUNTIME";
            case "RELATION_RHYTHM" -> "SOCIAL";
            default -> "PRIVATE";
        };
    }
}
