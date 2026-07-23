package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Gemini audit 3.3 (CONFIRMED/P1): slow letters (慢信) have safety filtering (LetterSafetyFilter --
 * harassment/contact-solicitation/coercive-language/high-risk-content keyword gates) but no
 * PII/credential-specific policy. This is a deliberate DETECT-AND-GATE flow, not a "keyword
 * -deletion sanitize": it never rewrites, redacts, or silently drops any part of the user's
 * private letter text. It only classifies detected categories into two tiers:
 * <ul>
 *   <li>HARD-BLOCK: credentials/secrets (passwords, API keys/tokens, national ID numbers, bank
 *       card numbers) -- sending is refused outright with a clear reason; there is no
 *       confirmation override for this tier.</li>
 *   <li>SOFT-CONFIRM: other PII (phone numbers, emails, street addresses) -- sending requires the
 *       sender's explicit confirmation; the caller (SlowLetterServiceImpl) records a minimal
 *       consent RECEIPT (category names only, e.g. "PHONE,EMAIL") -- never the raw matched
 *       PII text itself -- once confirmed.</li>
 * </ul>
 */
@Component
public class PiiCredentialDetector {

    private static final Pattern PASSWORD =
            Pattern.compile("(?i)(password|pwd|密码|口令)\\s*(?:[:：=]|是)\\s*\\S+");
    private static final Pattern API_KEY =
            Pattern.compile("(?i)(api[_-]?key|access[_-]?token|secret[_-]?key|私钥|密钥|token)\\s*(?:[:：=]|是)\\s*\\S+");
    private static final Pattern NATIONAL_ID = Pattern.compile("\\b\\d{17}[\\dXx]\\b");
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16}\\b|\\b\\d{19}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b1[3-9]\\d{9}\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // Deliberately conservative: an address cue needs BOTH an administrative-division word
    // (省/市/区/县) AND a street/building-level word (路/街/号/栋/单元/小区) within a short span,
    // so an isolated "市" (as in "菜市场") or "号" (as in "第一号选手") alone never counts.
    private static final Pattern ADDRESS = Pattern.compile("(省|市|区|县).{0,20}(路|街|号|栋|单元|小区)");

    public ScanResult scan(String text) {
        if (text == null || text.isBlank()) {
            return new ScanResult(List.of(), List.of());
        }
        List<String> hardBlock = new ArrayList<>();
        List<String> softConfirm = new ArrayList<>();

        boolean nationalId = NATIONAL_ID.matcher(text).find();
        if (nationalId) hardBlock.add("NATIONAL_ID");
        if (PASSWORD.matcher(text).find()) hardBlock.add("PASSWORD");
        if (API_KEY.matcher(text).find()) hardBlock.add("API_KEY");
        // A bank-card-length digit run that ALSO happens to satisfy the 18-digit national ID
        // shape is reported once, as the more specific NATIONAL_ID category.
        if (!nationalId && BANK_CARD.matcher(text).find()) hardBlock.add("BANK_CARD");

        if (PHONE.matcher(text).find()) softConfirm.add("PHONE");
        if (EMAIL.matcher(text).find()) softConfirm.add("EMAIL");
        if (ADDRESS.matcher(text).find()) softConfirm.add("ADDRESS");

        return new ScanResult(hardBlock, softConfirm);
    }

    public static final class ScanResult {
        public final List<String> hardBlockCategories;
        public final List<String> softConfirmCategories;

        public ScanResult(List<String> hardBlockCategories, List<String> softConfirmCategories) {
            this.hardBlockCategories = List.copyOf(hardBlockCategories);
            this.softConfirmCategories = List.copyOf(softConfirmCategories);
        }

        public boolean hasHardBlock() {
            return !hardBlockCategories.isEmpty();
        }

        public boolean hasSoftConfirm() {
            return !softConfirmCategories.isEmpty();
        }
    }
}
