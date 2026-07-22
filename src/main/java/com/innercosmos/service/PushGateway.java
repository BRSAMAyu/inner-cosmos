package com.innercosmos.service;

public interface PushGateway {
    String transport();
    SendResult send(Message message);

    record Message(String token, String title, String body, String deepLink, Long wakeIntentId) {}
    record SendResult(boolean delivered, boolean retryable, boolean invalidToken,
                      String providerMessageId, String errorClass) {
        public static SendResult delivered(String id) { return new SendResult(true, false, false, id, null); }
        public static SendResult failed(boolean retryable, boolean invalidToken, String errorClass) {
            return new SendResult(false, retryable, invalidToken, null, errorClass);
        }
    }
}
