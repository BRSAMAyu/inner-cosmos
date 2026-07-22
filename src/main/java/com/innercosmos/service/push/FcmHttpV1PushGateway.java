package com.innercosmos.service.push;

import com.innercosmos.service.PushGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** FCM HTTP v1 transport. OAuth access tokens and project identity are injected outside the repository. */
@Component
public class FcmHttpV1PushGateway implements PushGateway {
    private final RestClient client;
    private final String projectId;
    private final String accessToken;

    public FcmHttpV1PushGateway(RestClient.Builder builder,
                               @Value("${inner-cosmos.push.fcm.project-id:}") String projectId,
                               @Value("${inner-cosmos.push.fcm.oauth-access-token:}") String accessToken) {
        this.client = builder.baseUrl("https://fcm.googleapis.com").build();
        this.projectId = projectId; this.accessToken = accessToken;
    }

    @Override public String transport() { return "FCM"; }

    @Override
    public SendResult send(Message message) {
        if (projectId.isBlank() || accessToken.isBlank()) {
            return SendResult.failed(false, false, "EXTERNAL_CREDENTIAL_GATE");
        }
        try {
            @SuppressWarnings("unchecked") Map<String, Object> response = client.post()
                .uri("/v1/projects/{project}/messages:send", projectId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", Map.of(
                    "token", message.token(),
                    "notification", Map.of("title", message.title(), "body", message.body()),
                    "data", Map.of("wakeIntent", String.valueOf(message.wakeIntentId()), "deepLink", message.deepLink())
                )))
                .retrieve().body(Map.class);
            return SendResult.delivered(response == null ? null : String.valueOf(response.get("name")));
        } catch (HttpClientErrorException error) {
            int status = error.getStatusCode().value();
            boolean invalid = status == 400 || status == 404;
            return SendResult.failed(status == 429 || status >= 500, invalid, "FCM_HTTP_" + status);
        } catch (RuntimeException error) {
            return SendResult.failed(true, false, "FCM_TRANSPORT_ERROR");
        }
    }
}
