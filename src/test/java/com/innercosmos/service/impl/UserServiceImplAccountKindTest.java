package com.innercosmos.service.impl;

import com.innercosmos.common.Constants;
import com.innercosmos.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceImplAccountKindTest {

    @Test
    void repositoryOwnedJourneyAccountsArePersistedAsSynthetic() {
        assertEquals("SYNTHETIC", UserServiceImpl.accountKindForRegistration("benchdeepseek192301019"));
        assertEquals("SYNTHETIC", UserServiceImpl.accountKindForRegistration("semantic95014242"));
    }

    @Test
    void ordinaryAccountsRemainHuman() {
        assertEquals("HUMAN", UserServiceImpl.accountKindForRegistration("lin"));
        assertEquals("HUMAN", UserServiceImpl.accountKindForRegistration("demo-proof-reader"));
        assertEquals("HUMAN", UserServiceImpl.accountKindForRegistration("demoproofa1784895548098"));
        assertEquals("HUMAN", UserServiceImpl.accountKindForRegistration(null));
    }

    @Test
    void publicDemoPersonaRequiresActiveNonAdminCuratedAccountKind() {
        User persona = new User();
        persona.status = Constants.STATUS_ACTIVE;
        persona.role = Constants.ROLE_USER;
        persona.accountKind = "SHOWCASE";
        assertTrue(UserServiceImpl.isPublicDemoPersona(persona));

        persona.role = Constants.ROLE_ADMIN;
        assertFalse(UserServiceImpl.isPublicDemoPersona(persona));
        persona.role = Constants.ROLE_USER;
        persona.accountKind = "HUMAN";
        assertFalse(UserServiceImpl.isPublicDemoPersona(persona));
    }
}
