package com.innercosmos.payments;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.PriceVersion;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.PriceVersionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * CP-45 residual (§2-21): the append-only price table behind the order catalog. Reading
 * a price means "the current ACTIVE version"; changing a price means retiring the old
 * version and inserting a NEW one in a single transaction — never editing an amount in
 * place, so an order created before a change keeps pointing at the version it was priced
 * at and the audit trail (旧版本→新版本) is complete (价格版本不可变).
 *
 * <p>Single-ACTIVE-per-product is a hard partial unique index on PostgreSQL; H2/MySQL
 * (the test twin) has no partial indexes, so there it rests on the atomic retire+insert
 * switch. The (product_id, version) unique key rejects duplicate version numbers on both
 * databases — a concurrent double-switch surfaces as a DuplicateKeyException to the
 * operator instead of a silent overwrite.
 *
 * <p>Seeding: the first order for a catalog product lazily inserts version 1 from the
 * sandbox default table, so even the bootstrap price is an auditable version row. The
 * seeding race (two first-ever orders) is handled exactly like PaymentLedgerService's
 * insert race: per-statement autocommit, loser re-reads the winner's row (no enclosing
 * transaction — a caught constraint violation must not poison one).
 */
@Service
public class PriceVersionService {

    /** Catalog sandbox defaults (CNY minor units) — the price of version 1. */
    static final Map<String, Long> SEED_PRICES = Map.of("pro.monthly", 2500L);

    private final PriceVersionMapper mapper;

    public PriceVersionService(PriceVersionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * The version new orders for this product price at: the current ACTIVE row, seeding
     * version 1 from the catalog default if the product has never been priced. Concurrent
     * first-readers race on (product_id, 1); the unique key turns the loser's insert into
     * a re-read of the winner's row — the idempotent ack, not an error.
     */
    public PriceVersion currentActiveOrSeed(String productId) {
        PriceVersion active = selectActive(productId);
        if (active != null) {
            return active;
        }
        Long seed = SEED_PRICES.get(productId);
        if (seed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unknown product " + productId);
        }
        // Normally version 1; maxVersion+1 also repairs a price switch whose transaction
        // was lost mid-flight (product momentarily unpriced) without resurrecting v1.
        PriceVersion candidate = newVersion(productId, Math.max(1, maxVersion(productId) + 1),
                seed);
        try {
            mapper.insert(candidate);
            return candidate;
        } catch (DuplicateKeyException raced) {
            PriceVersion winner = selectActive(productId);
            if (winner == null) {
                throw raced; // not a seeding race — surface it instead of guessing
            }
            return winner;
        }
    }

    /**
     * A price change: retires every ACTIVE version of the product, then inserts the next
     * version as ACTIVE — one transaction, so readers never observe a half-applied switch.
     * Retiring first is what PostgreSQL's partial unique index (one ACTIVE per product)
     * requires; a transaction lost between the two statements rolls the retirement back
     * with it. Existing version rows are immutable — only status/retiredAt of superseded
     * rows ever move, amountCents never does.
     */
    @Transactional
    public PriceVersion changePrice(String productId, long newAmountCents, String currency) {
        if (newAmountCents <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "price must be positive");
        }
        mapper.update(null, new UpdateWrapper<PriceVersion>()
                .eq("product_id", productId)
                .eq("status", "ACTIVE")
                .set("status", "RETIRED")
                .set("retired_at", LocalDateTime.now(ZoneOffset.UTC)));
        PriceVersion inserted = newVersion(productId, maxVersion(productId) + 1, newAmountCents);
        if (currency != null && !currency.isBlank()) {
            inserted.currency = currency;
        }
        mapper.insert(inserted);
        return inserted;
    }

    /** Traceability lookup: the exact version an order pinned, RETIRED or not. */
    public PriceVersion findVersion(long priceVersionId) {
        return mapper.selectById(priceVersionId);
    }

    private PriceVersion selectActive(String productId) {
        return mapper.selectOne(new QueryWrapper<PriceVersion>()
                .eq("product_id", productId)
                .eq("status", "ACTIVE")
                .orderByDesc("version")
                .last("LIMIT 1"));
    }

    private int maxVersion(String productId) {
        PriceVersion top = mapper.selectOne(new QueryWrapper<PriceVersion>()
                .eq("product_id", productId)
                .orderByDesc("version")
                .last("LIMIT 1"));
        return top == null || top.version == null ? 0 : top.version;
    }

    private static PriceVersion newVersion(String productId, int version, long amountCents) {
        PriceVersion row = new PriceVersion();
        row.productId = productId;
        row.version = version;
        row.amountCents = amountCents;
        row.currency = "CNY";
        row.status = "ACTIVE";
        row.effectiveFrom = LocalDateTime.now(ZoneOffset.UTC);
        return row;
    }
}
