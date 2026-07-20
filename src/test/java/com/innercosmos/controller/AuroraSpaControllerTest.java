package com.innercosmos.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuroraSpaControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuroraSpaController()).build();

    @Test
    void extensionFreeNestedProductRouteForwardsToSpaShell() throws Exception {
        mvc.perform(get("/app/aurora/cosmos/starfield"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/app/aurora/index.html"));
    }

    @Test
    void staticAssetPathIsNotClaimedBySpaController() throws Exception {
        mvc.perform(get("/app/aurora/assets/missing.js"))
                .andExpect(status().isNotFound());
    }
}
