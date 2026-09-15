package com.innercosmos.service;

public interface PushDeliveryService {
    /**
     * CP-26 锁屏文案脱敏：把唤醒意图排入厂商推送 outbox（tb_push_delivery，每个启用且未撤销的
     * 设备一行）。调用方仍传完整的 App 内文案（title/body）——App 内通知继续展示完整内容，
     * 详情在 App 内看；但当 {@code inner-cosmos.wake.push-sanitized} 开启（默认开，隐私优先，
     * 与前端 WakeLockScreenPrivacy 的默认一致）时，落库的 title/body 只承载与前端逐字一致的
     * 中性唤起「Aurora / Aurora 想起你」，用户自述的约定内容（reasonForUser/message 类字段）
     * 绝不进推送正文——厂商推送到达的锁屏/横幅是设备上最公开的展示面。关闭该开关是运维的
     * 显式选择（保留完整预览文案）。deep_link 永不改写，既有投递行不回溯修改。
     */
    void enqueueWakeIntent(Long userId, Long wakeIntentId, String title, String body);
}
