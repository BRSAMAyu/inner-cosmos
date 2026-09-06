package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.metric.MetricCode;
import com.innercosmos.service.metric.MetricEventService;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CP-03: explicit user confirmation that a review / settlement / exchange was useful —
 * the K1 numerator. This is the only client-driven metric input; it records a bare fact
 * ("user confirmed value this week / for this thread"), never any content the user wrote.
 */
@RestController
@RequestMapping("/api/metrics")
public class ValueFeedbackController extends BaseController {

    private final MetricEventService metricEventService;
    private final Clock clock;

    public ValueFeedbackController(MetricEventService metricEventService, Clock clock) {
        this.metricEventService = metricEventService;
        this.clock = clock;
    }

    public record ValueFeedbackRequest(String scope, Boolean useful, Long threadId) {
    }

    public record ValueFeedbackResponse(boolean recorded) {
    }

    @PostMapping("/value-feedback")
    public ApiResponse<ValueFeedbackResponse> feedback(
            @RequestBody ValueFeedbackRequest request, HttpSession session) {
        Long userId = currentUserId(session);
        if (request == null || request.useful() == null || request.scope() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "scope 与 useful 必填");
        }
        // A "not useful" answer is honest signal but not a K1 fact; it is not stored as an
        // event (driver metrics handle response rates once invitation events exist).
        if (!request.useful()) {
            return ApiResponse.ok(new ValueFeedbackResponse(false));
        }
        Map<String, Object> props = new HashMap<>();
        switch (request.scope()) {
            case "PRIVATE_REVIEW" -> {
                props.put("scope", "PRIVATE_REVIEW");
                metricEventService.record(MetricCode.VALUE_CONFIRMED_PRIVATE, userId,
                        clock.instant(), null, null, null, null, props);
            }
            case "CONNECTION" -> {
                if (request.threadId() == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "CONNECTION 需要.threadId");
                }
                props.put("threadId", String.valueOf(request.threadId()));
                metricEventService.record(MetricCode.VALUE_CONFIRMED_CONNECTED, userId,
                        clock.instant(), "LETTER_THREAD", String.valueOf(request.threadId()),
                        null, null, props);
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "scope 必须是 PRIVATE_REVIEW 或 CONNECTION");
        }
        return ApiResponse.ok(new ValueFeedbackResponse(true));
    }
}
