package com.innercosmos.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "inner-cosmos.security.rate-limit")
public class RateLimitProperties {
    private final Band user = new Band(40, 40, 240);
    private final Band anonymous = new Band(20, 20, 60);
    // One token represents one user-initiated Aurora turn (the SSE continuation is exempt).
    // Twenty turns/minute leaves room for a live multi-turn demo while still bounding abuse.
    private final Band aurora = new Band(20, 20, 120);
    // Every other module that also calls a real AI provider synchronously in the request path
    // (thought-shredder, persona-chat visitor messages, capsule sandbox/genome recompile,
    // todo-split) -- distinct from "aurora" so a user's Aurora chat turns and, say, a thought-
    // shredder run don't compete for the exact same 5/min bucket, but still far below the
    // generic "user" band's 40/min so provider cost stays bounded (2026-07-27 audit P2).
    private final Band modelBacked = new Band(10, 10, 60);
    private final Band login = new Band(10, 10, 10);

    public Band getUser() { return user; }
    public Band getAnonymous() { return anonymous; }
    public Band getAurora() { return aurora; }
    public Band getModelBacked() { return modelBacked; }
    public Band getLogin() { return login; }

    public RateLimitPolicy user() { return user.policy(); }
    public RateLimitPolicy anonymous() { return anonymous.policy(); }
    public RateLimitPolicy aurora() { return aurora.policy(); }
    public RateLimitPolicy modelBacked() { return modelBacked.policy(); }
    public RateLimitPolicy login() { return login.policy(); }

    public static final class Band {
        private long capacity;
        private long refillPerMinute;
        private long advertisedLimit;

        Band(long capacity, long refillPerMinute, long advertisedLimit) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
            this.advertisedLimit = advertisedLimit;
        }

        public long getCapacity() { return capacity; }
        public void setCapacity(long capacity) { this.capacity = capacity; }
        public long getRefillPerMinute() { return refillPerMinute; }
        public void setRefillPerMinute(long refillPerMinute) { this.refillPerMinute = refillPerMinute; }
        public long getAdvertisedLimit() { return advertisedLimit; }
        public void setAdvertisedLimit(long advertisedLimit) { this.advertisedLimit = advertisedLimit; }

        RateLimitPolicy policy() {
            return new RateLimitPolicy(capacity, refillPerMinute, advertisedLimit);
        }
    }
}
