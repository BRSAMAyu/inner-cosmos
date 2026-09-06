package com.innercosmos.service.consent;

/**
 * CP-07 consent purpose registry (spec: cp07-pia-and-consent-contract-spec.md §2).
 * Grouping and defaults are code-authoritative; the DB stores only decisions.
 */
public enum ConsentPurpose {
    /** Core dialog/memory loop — service-necessary, not rejectable short of account deletion. */
    CORE_SERVICE(Group.REQUIRED, Decision.GRANTED,
            "提供对话、记忆与内宇宙核心功能所必需", "删除账号即终止", true),
    /** Sending user content to the selected real model provider (mainland by default). */
    AI_PROVIDER_EGRESS(Group.OPTIONAL_ASK, Decision.NOT_GRANTED,
            "将你的对话内容发送到所选的境内大模型服务以生成回应", "拒绝后 AI 回应功能不可用；本地功能与数据权利不受影响", true),
    /** Star-sea plaza discoverability of authorized facets. */
    PUBLIC_DISCOVERABLE(Group.OPTIONAL, Decision.DECLINED,
            "让你的授权侧面在星海广场被他人发现", "撤回后立即不可见", true),
    /** Scheduled proactive care messages (J07). */
    PROACTIVE_CARE(Group.OPTIONAL, Decision.DECLINED,
            "在你预约或允许的时间发送主动关心", "撤回后全部排队提醒作废", true),
    /** CP-03 metric events (dual-written with tb_analysis_consent). */
    ANALYTICS(Group.OPTIONAL, Decision.GRANTED,
            "以去标识聚合方式衡量服务质量（不含对话正文）", "拒绝后你的行为不再计入指标", true),
    /** Voice ASR/TTS — sensitive (biometric-adjacent) under PIPL, separate consent. */
    VOICE_PROCESSING(Group.SENSITIVE, Decision.DECLINED,
            "语音转文字与语音朗读（涉及声学特征处理）", "拒绝后语音功能停用，文字功能不受影响", true),
    /** Memory→capsule compilation is managed per-memory by DataUseGrant; display only. */
    CAPSULE_COMPILE(Group.MANAGED_ELSEWHERE, Decision.MANAGED,
            "逐条记忆授权后编译为共鸣体", "撤回对应授权，共鸣体下线并失效派生物", false);

    public enum Group { REQUIRED, OPTIONAL_ASK, OPTIONAL, SENSITIVE, MANAGED_ELSEWHERE }

    /** Effective state when the user has no recorded decision. */
    public enum Decision { GRANTED, DECLINED, NOT_GRANTED, MANAGED }

    public final Group group;
    public final Decision defaultDecision;
    public final String description;
    public final String withdrawalEffect;
    /** Whether the user may flip this purpose from the consent center. */
    public final boolean userSettable;

    ConsentPurpose(Group group, Decision defaultDecision, String description,
                   String withdrawalEffect, boolean userSettable) {
        this.group = group;
        this.defaultDecision = defaultDecision;
        this.description = description;
        this.withdrawalEffect = withdrawalEffect;
        this.userSettable = userSettable;
    }

    /** Spec version of the purpose texts; bumping this triggers re-consent flows later. */
    public static final String CURRENT_VERSION = "PV-2026-09";
}
