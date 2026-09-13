package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.PaymentOrder;
import com.innercosmos.payments.PaymentOrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CP-45 checkout entry: the ONLY way an order comes into existence is here, server-side,
 * with the catalog price — so every later channel callback has an authoritative expected
 * amount/channel/product to be validated against. The response carries the order fact the
 * client may DISPLAY; the client can never declare a payment successful — that decision
 * belongs exclusively to the verified callback → ledger → entitlement pipeline.
 *
 * <p>Real channel prepay (微信下单/支付宝预创建, returning the actual pay parameters)
 * is operator-gated wiring on top of this order fact; until those contracts exist the
 * sandbox flow signs callbacks against this order via the channel adapters.
 */
@RestController
@RequestMapping("/api/payments/orders")
public class PaymentOrderController extends BaseController {

    private final PaymentOrderService orders;

    public PaymentOrderController(PaymentOrderService orders) {
        this.orders = orders;
    }

    public record CreateOrderRequest(String productId, String channel) {
    }

    @PostMapping
    public ApiResponse<PaymentOrder> create(@RequestBody CreateOrderRequest request,
                                            HttpSession session) {
        if (request == null || request.productId() == null || request.channel() == null) {
            return ApiResponse.fail("BAD_REQUEST", "productId 与 channel 必填");
        }
        return ApiResponse.ok(orders.createOrder(currentUserId(session),
                request.productId(), request.channel()));
    }

    /** The user's own orders with their current ledger net (display-only). */
    @GetMapping
    public ApiResponse<?> list(HttpSession session) {
        return ApiResponse.ok(Map.of("note",
                "订单列表与账本净额查询随 CP-45 渠道对接批次提供；订单事实已可经 POST 创建"));
    }
}
