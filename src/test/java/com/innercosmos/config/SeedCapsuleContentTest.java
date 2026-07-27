package com.innercosmos.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedCapsuleContentTest {

    @Test
    void officialDemoLibraryIsCompleteDistinctAndPresentationReady() {
        var seeds = SeedCapsuleContent.seeds();

        assertThat(seeds).hasSize(10);
        assertThat(seeds).extracting(SeedCapsuleContent.SeedCapsule::name).doesNotHaveDuplicates();
        assertThat(seeds).allSatisfy(seed -> {
            assertThat(seed.intro()).hasSizeGreaterThan(24);
            assertThat(seed.tags()).hasSizeGreaterThanOrEqualTo(5);
            assertThat(seed.chatTopics()).isNotEmpty();
            assertThat(seed.responseContract()).hasSizeGreaterThan(25);
            assertThat(seed.responseContract()).doesNotContain("免责声明", "授权范围不足");
        });
        assertThat(seeds).extracting(SeedCapsuleContent.SeedCapsule::responseContract)
                .doesNotHaveDuplicates();
    }
}
