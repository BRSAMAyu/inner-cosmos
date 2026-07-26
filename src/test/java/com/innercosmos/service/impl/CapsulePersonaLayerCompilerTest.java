package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.service.DataMaskingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapsulePersonaLayerCompilerTest {

    @Test
    void snapshotsOnlyPublicSafeValueAndPreservesProvenance() {
        UnderstandingClaimMapper mapper = mock(UnderstandingClaimMapper.class);
        DataMaskingService masking = mock(DataMaskingService.class);
        UnderstandingClaim claim = new UnderstandingClaim();
        claim.id = 9L;
        claim.userId = 2L;
        claim.claimType = "VALUE";
        claim.valueJson = "{\"value\":\"我看重朋友小林的认真回应\"}";
        claim.confidence = 0.88;
        claim.evidenceRefs = "[101,102]";
        when(mapper.selectList(any())).thenReturn(List.of(claim));
        when(masking.maskText(any(), eq("STRICT"))).thenAnswer(invocation -> invocation.getArgument(0));

        CapsulePersonaLayerCompiler compiler = new CapsulePersonaLayerCompiler(
                mapper, masking, new CapsuleThirdPartyAnonymizer(), new ObjectMapper());
        List<Map<String, Object>> layer = compiler.compile(2L, List.of(9L), "STRICT");

        assertEquals(1, layer.size());
        assertEquals("VALUE", layer.getFirst().get("claimType"));
        assertEquals(List.of(101L, 102L), layer.getFirst().get("evidenceRefs"));
        assertFalse(String.valueOf(layer.getFirst().get("capsuleSafeValue")).contains("小林"));
        String preview = compiler.attach("{\"genomeIr\":{}}", layer);
        assertEquals(List.of(9L), compiler.claimIdsFromPreview(preview));
    }
}
