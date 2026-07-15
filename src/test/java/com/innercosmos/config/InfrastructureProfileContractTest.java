package com.innercosmos.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InfrastructureProfileContractTest {

    @Test
    void academyProfileUsesOnlyProvenAdaptersAndPreservesProductSemantics() throws IOException {
        String profile = resource("application-academy-eks.yml");

        assertThat(profile)
                .contains("profile: academy-eks")
                .contains("region: us-east-1")
                .contains("event-dispatcher: jdbc-outbox")
                .contains("database: in-cluster-postgresql-static-pv")
                .contains("redis: in-cluster-disposable-tls")
                .contains("aws-workload-identity: false")
                .contains("sqs-runtime: false")
                .contains("product-semantics: complete")
                .doesNotContain("event-dispatcher: sqs-outbox");
    }

    @Test
    void localAndCommercialProfilesKeepSeparateEvidenceClaims() throws IOException {
        String local = resource("application-local-complete.yml");
        String commercial = resource("application-commercial-sg.yml");

        assertThat(local)
                .contains("profile: local-complete")
                .contains("region: developer-controlled")
                .contains("product-semantics: complete")
                .doesNotContain("ap-southeast-1");
        assertThat(commercial)
                .contains("profile: commercial-sg")
                .contains("region: ap-southeast-1")
                .contains("event-dispatcher: sqs-outbox")
                .contains("aws-workload-identity: required")
                .contains("product-semantics: complete");
    }

    private String resource(String name) throws IOException {
        return Files.readString(Path.of("src/main/resources", name));
    }
}
