package com.innercosmos.ai.capsule;

import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.UserLongTermMemory;
import com.innercosmos.entity.UserPortrait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PiiPrivacyFilterTest {

    @Mock
    private UserPortraitService portraitService;

    @Mock
    private AgentUserRelationshipService relationshipService;

    private PiiPrivacyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PiiPrivacyFilter(portraitService, relationshipService);
    }

    // Test case 1: Full name pseudonymization
    @Test
    void pseudonymizesFullName() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("real_name", "\"林澈\"")
        ));
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of());

        assertEquals("林同学", result.pseudonym());
    }

    @Test
    void pseudonymizesNullName() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of());
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of());

        assertEquals("TA", result.pseudonym());
    }

    // Test case 2: Precise address generalized to city
    @Test
    void stripsPreciseAddressToCity() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("address", "\"上海市徐汇区漕河泾开发区某路123号\"")
        ));
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of());

        assertNotNull(result.city());
        assertTrue(result.city().length() <= 6);
        assertFalse(result.city().contains("路"));
        assertFalse(result.city().contains("号"));
    }

    // Test case 3: Age converted to range
    @Test
    void convertsAgeToRange() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("age", "28")
        ));
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of());

        assertEquals("25-34", result.ageRange());
    }

    @Test
    void convertsAgeBoundaryCases() {
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("age", "25")
        ));
        assertEquals("25-34", filter.filter(filter.createSnapshot(1L, List.of()), Map.of()).ageRange());

        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("age", "24")
        ));
        assertEquals("20-29", filter.filter(filter.createSnapshot(1L, List.of()), Map.of()).ageRange());
    }

    // Test case 4: Occupation categorized
    @Test
    void categorizesOccupation() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("occupation", "\"前端工程师\"")
        ));
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of());

        assertEquals("互联网/技术", result.occupationCategory());
    }

    @Test
    void categorizesVariousOccupations() {
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        assertEquals("互联网/技术", categorize("产品经理"));
        assertEquals("互联网/技术", categorize("设计师"));
        assertEquals("金融/财务", categorize("银行柜员"));
        assertEquals("教育/培训", categorize("中学教师"));
        assertEquals("医疗/健康", categorize("护士"));
        assertEquals("学生", categorize("在读研究生"));
    }

    private String categorize(String occupation) {
        reset(portraitService);
        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("occupation", "\"" + occupation + "\"")
        ));
        return filter.filter(filter.createSnapshot(1L, List.of()), Map.of()).occupationCategory();
    }

    // Drop field transparency
    @Test
    void tracksDroppedFields() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("real_name", "\"张三\""),
                portraitEntry("address", "\"某地址\"")
        ));
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of("address", "DROP"));

        assertTrue(result.droppedFields().contains("address"));
        assertNull(result.city());
    }

    // Override policy test
    @Test
    void respectsPrivacyOverrides() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of(
                portraitEntry("age", "28")
        ));
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of("age", "DROP"));

        assertTrue(result.droppedFields().contains("age"));
        assertNull(result.ageRange());
    }

    // Aurora roles parsing
    @Test
    void parsesAuroraRolesFromRelationship() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of());
        AgentUserRelationship rel = new AgentUserRelationship();
        rel.auroraRoleInUserLife = "[\"倾听者\",\"陪伴者\"]";
        when(relationshipService.getOrInit(anyLong())).thenReturn(rel);

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of());

        assertEquals(List.of("倾听者", "陪伴者"), result.auroraRoles());
    }

    @Test
    void defaultsAuroraRolesWhenNull() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of());
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, List.of());
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of());

        assertEquals(List.of("倾听者"), result.auroraRoles());
    }

    // LTM values extraction
    @Test
    void extractsValuesFromLtm() {
        when(portraitService.getAll(anyLong())).thenReturn(List.of());
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());

        List<UserLongTermMemory> ltm = List.of(
                ltmEntry("VALUE", "真实表达",0.9),
                ltmEntry("BELIEF", "慢即是快", 0.8)
        );

        PiiPrivacyFilter.PortraitSnapshot snapshot = filter.createSnapshot(1L, ltm);
        PiiPrivacyFilter.FilteredPortrait result = filter.filter(snapshot, Map.of());

        assertTrue(result.values().contains("真实表达"));
        assertTrue(result.values().contains("慢即是快"));
    }

    private UserPortrait portraitEntry(String dim, String valueJson) {
        UserPortrait p = new UserPortrait();
        p.dim = dim;
        p.valueJson = valueJson;
        return p;
    }

    private UserLongTermMemory ltmEntry(String factType, String factValue, double confidence) {
        UserLongTermMemory m = new UserLongTermMemory();
        m.factType = factType;
        m.factValue = factValue;
        m.confidence = confidence;
        m.createdAt = LocalDateTime.now();
        return m;
    }
}