package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * M-030 regression, now pinned at the service layer since
 * {@link com.innercosmos.controller.UserPreferenceController} was refactored (G2.ARCH-MODULES) to
 * go through {@link UserService} instead of injecting {@code UserProfileMapper} directly:
 * {@code setPreferredModel} must look the profile up by {@code user_id} (the FK), never by the
 * profile's own primary key, which can legitimately differ from the owning user's id.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:preferred-model-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.task.scheduling.enabled=false",
        "inner-cosmos.demo.seed-enabled=false",
        "memory.embedding.enabled=false",
        "llm.provider=mock"
})
class UserServiceImplPreferredModelTest {
    @Autowired UserService userService;
    @Autowired UserProfileMapper userProfileMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void setPreferredModel_looksUpByUserIdNotByProfilePrimaryKey() {
        Long ownerA = createUser("preferred-model-owner-a");
        Long ownerB = createUser("preferred-model-owner-b");

        // Insert B's profile first so its PK is smaller than A's user id, and A's profile PK ends
        // up different from A's user id -- selectById(userId) would silently miss or hit the wrong row.
        UserProfile profileB = new UserProfile();
        profileB.userId = ownerB;
        userProfileMapper.insert(profileB);
        UserProfile profileA = new UserProfile();
        profileA.userId = ownerA;
        userProfileMapper.insert(profileA);
        org.junit.jupiter.api.Assertions.assertNotEquals(ownerA, profileA.id,
                "test fixture must produce a profile PK that differs from the owning user id");

        userService.setPreferredModel(ownerA, "deepseek");

        UserProfile reloadedA = userProfileMapper.selectOne(new QueryWrapper<UserProfile>().eq("user_id", ownerA));
        UserProfile reloadedB = userProfileMapper.selectOne(new QueryWrapper<UserProfile>().eq("user_id", ownerB));
        assertEquals("DEEPSEEK", reloadedA.preferredModel);
        assertNull(reloadedB.preferredModel, "the other user's profile must be untouched");
    }

    @Test
    void setPreferredModel_blankProviderClearsThePreference() {
        Long owner = createUser("preferred-model-clear-owner");
        UserProfile profile = new UserProfile();
        profile.userId = owner;
        profile.preferredModel = "GLM";
        userProfileMapper.insert(profile);

        userService.setPreferredModel(owner, "");

        UserProfile reloaded = userProfileMapper.selectOne(new QueryWrapper<UserProfile>().eq("user_id", owner));
        assertNull(reloaded.preferredModel);
    }

    private Long createUser(String username) {
        jdbc.update("INSERT INTO tb_user(username,password_hash,role,status) VALUES (?,?,?,?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username=?", Long.class, username);
    }
}
