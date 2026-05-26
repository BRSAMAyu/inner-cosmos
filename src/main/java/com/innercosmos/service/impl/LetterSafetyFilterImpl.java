package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.BlockRelation;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.service.LetterSafetyFilter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LetterSafetyFilterImpl implements LetterSafetyFilter {
    private final BlockRelationMapper blockRelationMapper;

    public LetterSafetyFilterImpl(BlockRelationMapper blockRelationMapper) {
        this.blockRelationMapper = blockRelationMapper;
    }

    @Override
    public FilterResult filter(String letterBody, Long senderId, Long receiverId) {
        FilterResult result = new FilterResult();

        if (letterBody == null || letterBody.isBlank()) {
            result.passed = false;
            result.reason = "信件内容不能为空";
            result.suggestion = "请写一些真诚的内容再发送。";
            return result;
        }

        // Check block relation
        QueryWrapper<BlockRelation> blockQuery = new QueryWrapper<>();
        blockQuery.eq("blocker_user_id", receiverId).eq("blocked_user_id", senderId);
        Long blockCount = blockRelationMapper.selectCount(blockQuery);
        if (blockCount > 0) {
            result.passed = false;
            result.reason = "接收方已屏蔽发送方";
            result.suggestion = "对方暂时不想收到你的信，也许以后还有机会。";
            return result;
        }

        // Check for harassment keywords
        List<String> harassmentKeywords = List.of(
                "去死", "废物", "垃圾", "滚", "恶心", "白痴", "智障",
                "你妈", "神经病", "变态", "不要脸", "滚开", "去你的",
                "蠢货", "贱人", "loser", "白痴", "废物点心", "没用的东西"
        );
        for (String keyword : harassmentKeywords) {
            if (letterBody.contains(keyword)) {
                result.passed = false;
                result.reason = "信件包含攻击性语言";
                result.suggestion = "信件里的某些表达可能会让对方不舒服，试着换一种方式说出你的感受。";
                return result;
            }
        }

        // Check for contact solicitation
        List<String> contactPatterns = List.of(
                "加我微信", "加我QQ", "微信号", "手机号", "电话号码",
                "发照片", "发定位", "出来见面", "你的地址"
        );
        for (String pattern : contactPatterns) {
            if (letterBody.contains(pattern)) {
                result.passed = false;
                result.reason = "信件包含联系方式索取";
                result.suggestion = "为了双方的安全，请不要在信件中交换联系方式。";
                return result;
            }
        }

        // Check for aggressive language patterns
        if (letterBody.contains("你必须") || letterBody.contains("你给我") || letterBody.contains("不答应就")) {
            result.passed = false;
            result.reason = "信件包含强制性语言";
            result.suggestion = "信件里的表达带有一些压力，试着用更温和的方式表达期待。";
            return result;
        }

        // Check for high-risk content (before intimacy check so danger is never missed)
        List<String> highRiskKeywords = List.of(
                "自杀", "轻生", "杀人", "跳楼", "割腕", "服药自杀",
                "不想活", "寻死", "自残", "了结自己", "结束生命",
                "死了一了百了", "活着没意义", "想死", "去死", "伤害自己",
                "活不下去", "没有意义", "死了算了"
        );
        for (String keyword : highRiskKeywords) {
            if (letterBody.contains(keyword)) {
                result.passed = false;
                result.reason = "信件包含高风险表达";
                result.suggestion = "如果你正在经历困难的时刻，请寻求专业帮助。你也可以和 Aurora 聊聊你的感受。";
                return result;
            }
        }

        // Check for overly intimate language
        List<String> intimateKeywords = List.of(
                "我爱你", "亲爱的心肝", "老公", "老婆", "宝贝",
                "想做你", "在一起", "永远不分开"
        );
        for (String keyword : intimateKeywords) {
            if (letterBody.contains(keyword)) {
                // Not blocking, but flag as warning
                result.passed = true;
                result.reason = "信件包含亲密表达，已标记为需关注";
                result.suggestion = "请确认你与对方的关系是否适合这样的表达。";
                return result;
            }
        }

        // All checks passed
        result.passed = true;
        result.reason = "信件内容安全";
        result.suggestion = "";
        return result;
    }
}
