package com.innercosmos.ai.action;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuroraNaturalActionParserTest {
    private final AuroraNaturalActionParser parser = new AuroraNaturalActionParser();

    @Test
    void rememberThisUsesThePreviousUserMessageWithoutInventingContent() {
        var decision = parser.parse("请记住这件事",
                "现场网络不稳定时，先切到本地离线备用流程。", "Asia/Singapore");

        assertThat(decision.intent().type()).isEqualTo(AuroraNaturalActionParser.REMEMBER);
        assertThat(decision.intent().payload())
                .containsEntry("content", "现场网络不稳定时，先切到本地离线备用流程。")
                .containsEntry("title", "现场网络不稳定时，先切到本地离线备用流程");
    }

    @Test
    void englishRememberThatKeepsTheExplicitContent() {
        var decision = parser.parse("Remember that I prefer direct questions.",
                "unrelated earlier message", "Europe/London");

        assertThat(decision.intent().type()).isEqualTo(AuroraNaturalActionParser.REMEMBER);
        assertThat(decision.intent().payload().get("content")).isEqualTo("I prefer direct questions.");
        assertThat(decision.intent().english()).isTrue();
    }

    @Test
    void parsesChineseReminderIntoRealScheduleInputs() {
        var decision = parser.parse("明天早上8点提醒我带上充电器",
                null, "Asia/Singapore");

        assertThat(decision.intent().type()).isEqualTo(AuroraNaturalActionParser.REMINDER);
        assertThat(decision.intent().payload())
                .containsEntry("when", "明天早上8点")
                .containsEntry("content", "带上充电器")
                .containsEntry("timezone", "Asia/Singapore");
    }

    @Test
    void parsesEnglishRelativeReminder() {
        var decision = parser.parse("Remind me in 2 hours to check the build.",
                null, "America/New_York");

        assertThat(decision.intent().type()).isEqualTo(AuroraNaturalActionParser.REMINDER);
        assertThat(decision.intent().payload())
                .containsEntry("when", "in 2 hours")
                .containsEntry("content", "check the build")
                .containsEntry("timezone", "America/New_York");
        assertThat(decision.intent().summary())
                .isEqualTo("Schedule a real reminder for “check the build” in 2 hours");
    }

    @Test
    void vagueReminderRequestsMissingInformationInsteadOfScheduling() {
        var decision = parser.parse("提醒我交作业", null, "Asia/Singapore");

        assertThat(decision.recognized()).isTrue();
        assertThat(decision.intent()).isNull();
        assertThat(decision.clarification()).contains("需要时间和内容都明确");
    }

    @Test
    void settingsAreClosedVocabularyAndStillOnlyProposals() {
        var noRecall = parser.parse("不要再读取我的记忆", null, "Asia/Singapore");
        assertThat(noRecall.intent().type()).isEqualTo(AuroraNaturalActionParser.PROFILE_SETTING);
        assertThat(noRecall.intent().payload())
                .containsEntry("setting", "allowMemoryRecall")
                .containsEntry("value", "false");

        var moreProactive = parser.parse("Please be more proactive", null, "Asia/Singapore");
        assertThat(moreProactive.intent().payload())
                .containsEntry("setting", "proactiveSensitivity")
                .containsEntry("value", "4");
    }
}
