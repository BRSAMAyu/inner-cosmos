package com.innercosmos.payments.channel;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * CP-45 Alipay (支付宝) async-notify adapter. Wire shape follows the documented form-encoded
 * notify: {@code notify_id, trade_status, out_trade_no, trade_no, total_amount (yuan),
 * refund_fee (yuan), gmt_payment (yyyy-MM-dd HH:mm:ss Asia/Shanghai), app_id}. Real Alipay
 * signs with RSA2 over the sorted parameter string ({@code sign}/{@code sign_type} excluded);
 * the sandbox contract carries the same sorted-string canonicalization with explicit
 * {@code sandbox_timestamp}/{@code sandbox_sign} fields so the CP-47 HMAC kernel guards both
 * channels uniformly. The sandbox_* names are deliberate: impossible to mistake for a
 * production RSA2 verification, which is an operator-provided-certificate human gate.
 *
 * <p>Canonical body = ALL business parameters except {@code sandbox_sign}, sorted by key,
 * joined {@code k=v&k=v} — the exact string that was signed. Refund events REQUIRE
 * {@code refund_fee}; amounts are yuan decimals converted to CNY minor units exactly
 * (longValueExact — a yuan value that is not a whole number of fen is malformed).
 */
@Component
public class AlipayCallbackAdapter implements ChannelCallbackAdapter {

    public static final String PROVIDER = "alipay";
    static final String TIMESTAMP_PARAM = "sandbox_timestamp";
    static final String SIGN_PARAM = "sandbox_sign";

    private static final ZoneOffset SHANGHAI = ZoneOffset.ofHours(8);

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ChannelCallback decode(String rawBody, Map<String, String> headers) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new ChannelDecodeException("empty body");
        }
        Map<String, String> params = parseForm(rawBody);
        String notifyId = required(params, "notify_id");
        String tradeStatus = required(params, "trade_status");
        String orderId = required(params, "out_trade_no");
        String appId = required(params, "app_id");
        String timestamp = required(params, TIMESTAMP_PARAM);
        String signature = required(params, SIGN_PARAM);
        required(params, "gmt_payment");
        long amountCents;
        if ("REFUND_SUCCESS".equals(tradeStatus)) {
            amountCents = yuanToCents(required(params, "refund_fee"));
        } else {
            amountCents = yuanToCents(required(params, "total_amount"));
        }
        LocalDateTime occurredAt = parseShanghaiTime(params.get("gmt_payment"));
        return new ChannelCallback(PROVIDER, notifyId, orderId, tradeStatus, appId, "CNY",
                amountCents, occurredAt, timestamp, canonicalBody(params), signature);
    }

    @Override
    public String ackBody(boolean accepted) {
        return accepted ? "success" : "fail";
    }

    /** Sorted k=v& join over every business parameter except the sandbox signature itself. */
    static String canonicalBody(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.remove(SIGN_PARAM);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    static Map<String, String> parseForm(String rawBody) {
        Map<String, String> params = new TreeMap<>();
        for (String pair : rawBody.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new ChannelDecodeException("malformed form pair: " + pair);
            }
            params.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
        }
        return params;
    }

    /** Re-encodes nothing: the canonical string uses the DECODED values, matching how the sandbox signer builds it. */
    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ChannelDecodeException("undecodable form value");
        }
    }

    private static String required(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new ChannelDecodeException("missing " + key);
        }
        return value;
    }

    private static long yuanToCents(String yuan) {
        try {
            return new BigDecimal(yuan).movePointRight(2).stripTrailingZeros()
                    .setScale(0).longValueExact();
        } catch (Exception e) {
            throw new ChannelDecodeException("unparsable amount: " + yuan);
        }
    }

    private static LocalDateTime parseShanghaiTime(String value) {
        if (value == null) {
            throw new ChannelDecodeException("missing gmt_payment");
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atOffset(SHANGHAI)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (Exception e) {
            throw new ChannelDecodeException("unparsable gmt_payment: " + value);
        }
    }
}
