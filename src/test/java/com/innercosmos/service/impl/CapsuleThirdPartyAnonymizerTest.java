package com.innercosmos.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapsuleThirdPartyAnonymizerTest {

    @Test
    void snapshotUsesStableAliasesWithoutMutatingUnrelatedText() {
        CapsuleThirdPartyAnonymizer.Session session =
                new CapsuleThirdPartyAnonymizer().beginSnapshot();

        String first = session.anonymize("朋友小林最近说，我妈妈也很担心。");
        String second = session.anonymize("我又和小林说了这件事。");

        assertTrue(first.contains("一位朋友"));
        assertTrue(first.contains("家人"));
        assertTrue(second.contains("一位朋友"));
        assertFalse(second.contains("朋友 A"));
        assertFalse(first.contains("小林"));
        assertFalse(first.contains("妈妈"));
        assertEquals("我喜欢安静地想一会儿", session.anonymize("我喜欢安静地想一会儿"));
    }
}
