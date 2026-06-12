package com.innercosmos.service;

import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.impl.DataMaskingServiceImpl;
import com.innercosmos.vo.CapsulePreviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataMaskingServiceTest {

    @Mock
    private MemoryCardMapper memoryCardMapper;

    @Mock
    private AuthorizedMemoryRefMapper authorizedMemoryRefMapper;

    private DataMaskingServiceImpl dataMaskingService;

    @BeforeEach
    void setUp() {
        dataMaskingService = new DataMaskingServiceImpl(memoryCardMapper, authorizedMemoryRefMapper);
    }

    // --- maskText ---

    @Test
    @DisplayName("maskText handles null input")
    void maskText_null_returnsEmpty() {
        assertEquals("", dataMaskingService.maskText(null, "STRICT"));
    }

    @Test
    @DisplayName("maskText handles empty string")
    void maskText_empty_returnsEmpty() {
        assertEquals("", dataMaskingService.maskText("", "STRICT"));
    }

    @Test
    @DisplayName("maskText handles blank string")
    void maskText_blank_returnsEmpty() {
        assertEquals("", dataMaskingService.maskText("   ", "STRICT"));
    }

    @Test
    @DisplayName("maskText masks phone numbers in STRICT mode")
    void maskText_strict_masksPhoneNumbers() {
        String input = "Call me at 13812345678 tomorrow";
        String result = dataMaskingService.maskText(input, "STRICT");
        assertFalse(result.contains("13812345678"), "Phone number should be masked");
        assertTrue(result.contains("***********"));
    }

    @Test
    @DisplayName("maskText masks phone numbers in MODERATE mode")
    void maskText_moderate_masksPhoneNumbers() {
        String input = "Call me at 13812345678 tomorrow";
        String result = dataMaskingService.maskText(input, "MODERATE");
        assertFalse(result.contains("13812345678"), "Phone number should be masked");
    }

    @Test
    @DisplayName("maskText masks phone numbers in LOW mode")
    void maskText_low_masksPhoneNumbers() {
        String input = "Call me at 13812345678 tomorrow";
        String result = dataMaskingService.maskText(input, "LOW");
        assertFalse(result.contains("13812345678"), "Phone number should be masked even in LOW mode");
    }

    @Test
    @DisplayName("maskText masks email addresses in STRICT mode")
    void maskText_strict_masksEmails() {
        String input = "My email is test@example.com ok";
        String result = dataMaskingService.maskText(input, "STRICT");
        assertFalse(result.contains("test@example.com"), "Email should be masked");
        assertTrue(result.contains("***@***.***"));
    }

    @Test
    @DisplayName("maskText masks email addresses in MODERATE mode")
    void maskText_moderate_masksEmails() {
        String input = "My email is test@example.com ok";
        String result = dataMaskingService.maskText(input, "MODERATE");
        assertFalse(result.contains("test@example.com"), "Email should be masked in MODERATE mode");
    }

    @Test
    @DisplayName("maskText preserves safe content")
    void maskText_preservesSafeContent() {
        String input = "Today is a sunny day and I feel happy";
        String result = dataMaskingService.maskText(input, "STRICT");
        assertEquals(input, result, "Safe text should not be modified");
    }

    @Test
    @DisplayName("maskText with null privacy level defaults to LOW masking")
    void maskText_nullLevel_defaultsToLow() {
        String input = "Hello world 13812345678";
        String result = dataMaskingService.maskText(input, null);
        // LOW mode still masks contact info
        assertFalse(result.contains("13812345678"));
        assertTrue(result.contains("Hello world"));
    }

    @Test
    @DisplayName("maskText case insensitive for privacy level")
    void maskText_caseInsensitiveLevel() {
        String input = "13812345678";
        String upper = dataMaskingService.maskText(input, "STRICT");
        String lower = dataMaskingService.maskText(input, "strict");
        assertEquals(upper, lower, "Privacy level should be case-insensitive");
    }

    @Test
    @DisplayName("maskText STRICT masks school names")
    void maskText_strict_masksSchoolNames() {
        // Build a school name using char codes to avoid encoding issues
        // "bei jing da xue" (Beijing University) = U+5317 U+4EAC U+5927 U+5B66
        String school = String.valueOf(new char[]{0x5317, 0x4EAC, 0x5927, 0x5B66});
        String input = "I study at " + school;
        String result = dataMaskingService.maskText(input, "STRICT");
        assertTrue(result.contains("***" + String.valueOf(new char[]{0x5B66, 0x6821})),
                "School name should be masked in STRICT mode");
    }

    // --- previewFromMemory ---

    @Test
    @DisplayName("previewFromMemory with null memoryIds returns empty preview")
    void previewFromMemory_nullMemoryIds_returnsEmptyPreview() {
        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, null, "STRICT", null, null);

        assertNotNull(result);
        assertNotNull(result.removedSensitiveItems);
        assertNotNull(result.publicTags);
        assertNotNull(result.riskWarnings);
        assertNotNull(result.abstractSummary);
        assertNotNull(result.suggestedPseudonym);
        assertNotNull(result.personaPromptDraft);
    }

    @Test
    @DisplayName("previewFromMemory with empty memoryIds returns empty preview")
    void previewFromMemory_emptyMemoryIds_returnsEmptyPreview() {
        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, Collections.emptyList(), "STRICT", null, null);

        assertNotNull(result);
        assertNotNull(result.abstractSummary);
    }

    @Test
    @DisplayName("previewFromMemory with valid cards returns preview")
    void previewFromMemory_validCards_returnsPreview() {
        MemoryCard card = new MemoryCard();
        card.id = 1L;
        card.userId = 1L;
        card.summary = "A happy memory about sunshine";
        card.memoryType = "EMOTION";
        card.intensityScore = 5.0;
        card.keywordTags = "[happy, sunshine]";
        card.status = "ACTIVE";

        when(memoryCardMapper.selectList(any())).thenReturn(List.of(card));

        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, List.of(1L), "STRICT", null, null);

        assertNotNull(result);
        assertNotNull(result.abstractSummary);
        assertFalse(result.publicTags.isEmpty(), "Should extract public tags from keyword tags");
    }

    @Test
    @DisplayName("previewFromMemory with high intensity score adds risk warning")
    void previewFromMemory_highIntensity_addsRiskWarning() {
        MemoryCard card = new MemoryCard();
        card.id = 1L;
        card.userId = 1L;
        card.summary = "Intense memory";
        card.memoryType = "EMOTION";
        card.intensityScore = 9.0;
        card.status = "ACTIVE";

        when(memoryCardMapper.selectList(any())).thenReturn(List.of(card));

        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, List.of(1L), "STRICT", null, null);

        assertFalse(result.riskWarnings.isEmpty(),
                "High intensity should produce a risk warning");
    }

    @Test
    @DisplayName("previewFromMemory with blocked topics adds warnings")
    void previewFromMemory_blockedTopics_addsWarnings() {
        MemoryCard card = new MemoryCard();
        card.id = 1L;
        card.userId = 1L;
        card.summary = "Memory about restricted topic";
        card.memoryType = "TODO";
        card.intensityScore = 3.0;
        card.keywordTags = "[restricted, safe]";
        card.status = "ACTIVE";

        when(memoryCardMapper.selectList(any())).thenReturn(List.of(card));

        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, List.of(1L), "LOW", null, List.of("restricted"));

        boolean hasBlockedWarning = result.riskWarnings.stream()
                .anyMatch(w -> w.contains("restricted"));
        assertTrue(hasBlockedWarning, "Should warn about blocked topic");
        assertTrue(result.removedSensitiveItems.contains("restricted"));
    }

    @Test
    @DisplayName("previewFromMemory filters sensitive tags from public tags")
    void previewFromMemory_filtersSensitiveTags() {
        MemoryCard card = new MemoryCard();
        card.id = 1L;
        card.userId = 1L;
        card.summary = "Memory with mixed tags";
        card.memoryType = "COGNITION";
        card.intensityScore = 3.0;
        // Include both safe and sensitive keywords
        card.keywordTags = "[happy, " + String.valueOf(new char[]{0x5BC6, 0x7801}) + "]";
        card.status = "ACTIVE";

        when(memoryCardMapper.selectList(any())).thenReturn(List.of(card));

        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, List.of(1L), "STRICT", null, null);

        assertTrue(result.publicTags.contains("happy"));
        // The sensitive tag (mi ma = password) should be filtered
        assertFalse(result.publicTags.contains(String.valueOf(new char[]{0x5BC6, 0x7801})));
    }

    @Test
    @DisplayName("previewFromMemory generates pseudonym based on memory type")
    void previewFromMemory_generatesPseudonym() {
        MemoryCard card = new MemoryCard();
        card.id = 1L;
        card.userId = 1L;
        card.summary = "Action memory";
        card.memoryType = "TODO";
        card.intensityScore = 3.0;
        card.keywordTags = "[]";
        card.status = "ACTIVE";

        when(memoryCardMapper.selectList(any())).thenReturn(List.of(card));

        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, List.of(1L), "LOW", null, null);

        assertNotNull(result.suggestedPseudonym);
        assertFalse(result.suggestedPseudonym.isEmpty());
    }

    @Test
    @DisplayName("previewFromMemory generates persona prompt draft")
    void previewFromMemory_generatesPersonaPrompt() {
        MemoryCard card = new MemoryCard();
        card.id = 1L;
        card.userId = 1L;
        card.summary = "Happy memory";
        card.memoryType = "EMOTION";
        card.intensityScore = 3.0;
        card.keywordTags = "[happy]";
        card.status = "ACTIVE";

        when(memoryCardMapper.selectList(any())).thenReturn(List.of(card));

        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, List.of(1L), "LOW", null, null);

        assertNotNull(result.personaPromptDraft);
        assertFalse(result.personaPromptDraft.isEmpty());
    }

    @Test
    @DisplayName("previewFromMemory with contact info in summary flags sensitive content")
    void previewFromMemory_contactInfo_flagsSensitive() {
        MemoryCard card = new MemoryCard();
        card.id = 1L;
        card.userId = 1L;
        card.summary = "My phone is 13812345678 call me";
        card.memoryType = "TODO";
        card.intensityScore = 3.0;
        card.keywordTags = "[]";
        card.status = "ACTIVE";

        when(memoryCardMapper.selectList(any())).thenReturn(List.of(card));

        CapsulePreviewVO result = dataMaskingService.previewFromMemory(
                1L, List.of(1L), "STRICT", null, null);

        assertTrue(result.removedSensitiveItems.stream()
                .anyMatch(item -> item.contains("")));
        // The abstract summary should have the phone number masked
        assertFalse(result.abstractSummary.contains("13812345678"));
    }
}
