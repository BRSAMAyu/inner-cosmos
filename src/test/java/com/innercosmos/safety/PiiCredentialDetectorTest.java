package com.innercosmos.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gemini audit 3.3 (CONFIRMED/P1): pure unit coverage of the detect-and-gate classifier.
 * Credentials/secrets/national-ID/bank-card numbers must classify as HARD-BLOCK; phone/email/
 * address as SOFT-CONFIRM; ordinary text triggers neither.
 */
class PiiCredentialDetectorTest {

    private final PiiCredentialDetector detector = new PiiCredentialDetector();

    @Test
    @DisplayName("3.3: a password/credential phrase is HARD-BLOCK")
    void password_isHardBlock() {
        var result = detector.scan("我的密码是Tr0ub4dor&3，别告诉别人");
        assertTrue(result.hasHardBlock());
        assertTrue(result.hardBlockCategories.contains("PASSWORD"));
        assertFalse(result.hasSoftConfirm());
    }

    @Test
    @DisplayName("3.3: an API key/token phrase is HARD-BLOCK")
    void apiKey_isHardBlock() {
        var result = detector.scan("这是我的api_key: sk-abc123def456ghi789");
        assertTrue(result.hasHardBlock());
        assertTrue(result.hardBlockCategories.contains("API_KEY"));
    }

    @Test
    @DisplayName("3.3: an 18-digit Chinese national ID number is HARD-BLOCK")
    void nationalId_isHardBlock() {
        var result = detector.scan("身份证号 110101199003078515 麻烦帮我核对一下");
        assertTrue(result.hasHardBlock());
        assertTrue(result.hardBlockCategories.contains("NATIONAL_ID"));
    }

    @Test
    @DisplayName("3.3: a 16-digit bank card number is HARD-BLOCK")
    void bankCard_isHardBlock() {
        var result = detector.scan("卡号是6222021234567890 帮我转一下账");
        assertTrue(result.hasHardBlock());
        assertTrue(result.hardBlockCategories.contains("BANK_CARD"));
    }

    @Test
    @DisplayName("3.3: a phone number is SOFT-CONFIRM, not hard-blocked")
    void phone_isSoftConfirmOnly() {
        var result = detector.scan("有空打给我，我的号码是13812345678");
        assertFalse(result.hasHardBlock());
        assertTrue(result.hasSoftConfirm());
        assertTrue(result.softConfirmCategories.contains("PHONE"));
    }

    @Test
    @DisplayName("3.3: an email address is SOFT-CONFIRM, not hard-blocked")
    void email_isSoftConfirmOnly() {
        var result = detector.scan("可以发邮件到 person.name+test@example.com 给我");
        assertFalse(result.hasHardBlock());
        assertTrue(result.softConfirmCategories.contains("EMAIL"));
    }

    @Test
    @DisplayName("3.3: a street address is SOFT-CONFIRM, not hard-blocked")
    void address_isSoftConfirmOnly() {
        var result = detector.scan("我住在浙江省杭州市西湖区文三路100号");
        assertFalse(result.hasHardBlock());
        assertTrue(result.softConfirmCategories.contains("ADDRESS"));
    }

    @Test
    @DisplayName("3.3: an isolated administrative word alone (no street-level word nearby) is NOT flagged as an address")
    void isolatedAdministrativeWord_isNotFlaggedAsAddress() {
        var result = detector.scan("今天去菜市场买了很多菜，心情很好");
        assertFalse(result.hasSoftConfirm());
    }

    @Test
    @DisplayName("3.3: ordinary letter content with no PII/credentials triggers neither tier")
    void ordinaryText_triggersNeitherTier() {
        var result = detector.scan("我在第三段停了下来，想慢慢回你一封信。谢谢你愿意听我说这些。");
        assertFalse(result.hasHardBlock());
        assertFalse(result.hasSoftConfirm());
    }

    @Test
    @DisplayName("3.3: blank/null text triggers neither tier")
    void blankText_triggersNeitherTier() {
        assertFalse(detector.scan(null).hasHardBlock());
        assertFalse(detector.scan("").hasSoftConfirm());
        assertFalse(detector.scan("   ").hasHardBlock());
    }
}
