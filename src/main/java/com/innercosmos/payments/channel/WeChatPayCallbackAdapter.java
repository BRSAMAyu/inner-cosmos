package com.innercosmos.payments.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;

/**
 * CP-45 WeChat Pay (微信支付) v3 async-notify adapter. Wire shape follows the documented v3
 * notify envelope: JSON body with {@code id}/{@code event_type}/{@code resource{mchid,
 * out_trade_no, transaction_id|out_refund_no, amount{total|refund, currency}, success_time}}
 * and {@code Wechatpay-Timestamp}/{@code Wechatpay-Signature} headers.
 *
 * <p>Canonical triple: timestamp from the header, canonicalBody = the RAW body bytes as
 * received (the signature binds exactly what arrived), signature from the header. Payment
 * events read {@code amount.total}; refund events REQUIRE {@code amount.refund} (absent
 * refund amount = malformed, fail-closed — never guess the refunded sum from total).
 * Non-CNY currency is malformed: the ledger speaks CNY minor units only.
 */
@Component
public class WeChatPayCallbackAdapter implements ChannelCallbackAdapter {

    public static final String PROVIDER = "wechatpay";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ChannelCallback decode(String rawBody, Map<String, String> headers) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new ChannelDecodeException("empty body");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new ChannelDecodeException("body is not valid JSON");
        }
        String timestamp = header(headers, "wechatpay-timestamp");
        String signature = header(headers, "wechatpay-signature");
        if (timestamp == null || signature == null) {
            throw new ChannelDecodeException("missing Wechatpay-Timestamp/Wechatpay-Signature headers");
        }
        String eventId = text(root, "id");
        String eventType = text(root, "event_type");
        JsonNode resource = root.get("resource");
        if (eventId == null || eventType == null || resource == null || resource.isNull()) {
            throw new ChannelDecodeException("notify envelope missing id/event_type/resource");
        }
        String orderId = text(resource, "out_trade_no");
        String mchid = text(resource, "mchid");
        if (orderId == null || mchid == null) {
            throw new ChannelDecodeException("resource missing out_trade_no/mchid");
        }
        JsonNode amount = resource.get("amount");
        if (amount == null || amount.isNull()) {
            throw new ChannelDecodeException("resource missing amount");
        }
        String currency = text(amount, "currency");
        if (!"CNY".equals(currency)) {
            throw new ChannelDecodeException("non-CNY currency: " + currency);
        }
        long amountCents;
        if ("REFUND.SUCCESS".equals(eventType)) {
            JsonNode refund = amount.get("refund");
            if (refund == null || refund.isNull() || !refund.canConvertToLong()) {
                throw new ChannelDecodeException("refund event without amount.refund");
            }
            amountCents = refund.asLong();
        } else {
            JsonNode total = amount.get("total");
            if (total == null || total.isNull() || !total.canConvertToLong()) {
                throw new ChannelDecodeException("payment event without amount.total");
            }
            amountCents = total.asLong();
        }
        if (amountCents < 0) {
            throw new ChannelDecodeException("negative amount");
        }
        LocalDateTime occurredAt = parseTime(text(resource, "success_time"));
        return new ChannelCallback(PROVIDER, eventId, orderId, eventType, mchid, currency,
                amountCents, occurredAt, timestamp.trim(), rawBody, signature.trim());
    }

    @Override
    public String ackBody(boolean accepted) {
        return accepted ? "{\"code\":\"SUCCESS\",\"message\":\"OK\"}"
                : "{\"code\":\"FAIL\",\"message\":\"REJECTED\"}";
    }

    private static String header(Map<String, String> headers, String lowerCaseName) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().toLowerCase(Locale.ROOT).equals(lowerCaseName)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null) {
            throw new ChannelDecodeException("missing success_time");
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception e) {
            throw new ChannelDecodeException("unparsable success_time: " + value);
        }
    }
}
