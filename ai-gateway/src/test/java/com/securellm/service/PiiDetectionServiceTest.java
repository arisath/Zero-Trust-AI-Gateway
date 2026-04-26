package com.securellm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PiiDetectionServiceTest {

    private PiiDetectionService service;

    @BeforeEach
    void setUp() {
        service = new PiiDetectionService();
    }

    // -------------------------------------------------------------------------
    // tokenize — detection
    // -------------------------------------------------------------------------

    @Nested
    class Emails {
        @Test
        void tokenizesStandardEmail() {
            PiiDetectionService.TokenizedResult r = service.tokenize("Contact me at john.doe@example.com please");
            assertThat(r.text()).isEqualTo("Contact me at __PII_EMAIL_1__ please");
            assertThat(r.tokenMap()).containsEntry("__PII_EMAIL_1__", "john.doe@example.com");
        }

        @Test
        void tokenizesEmailWithPlusTag() {
            PiiDetectionService.TokenizedResult r = service.tokenize("user+tag@subdomain.example.co.uk");
            assertThat(r.text()).contains("__PII_EMAIL_1__");
        }

        @Test
        void noEmailPassesThrough() {
            PiiDetectionService.TokenizedResult r = service.tokenize("not an email address");
            assertThat(r.text()).isEqualTo("not an email address");
            assertThat(r.tokenMap()).isEmpty();
        }
    }

    @Nested
    class SocialSecurityNumbers {
        @ParameterizedTest
        @ValueSource(strings = {"123-45-6789", "123 45 6789", "123456789"})
        void tokenizesSsn(String ssn) {
            PiiDetectionService.TokenizedResult r = service.tokenize("SSN: " + ssn);
            assertThat(r.text()).contains("__PII_SSN_1__");
            assertThat(r.tokenMap()).containsEntry("__PII_SSN_1__", ssn);
        }

        @Test
        void doesNotTokenizePartialNumber() {
            assertThat(service.tokenize("order 12345").tokenMap()).isEmpty();
        }
    }

    @Nested
    class CreditCards {
        @ParameterizedTest
        @ValueSource(strings = {
            "4111111111111111",
            "4111 1111 1111 1111",
            "4111-1111-1111-1111"
        })
        void tokenizesCardNumber(String card) {
            assertThat(service.tokenize("Card: " + card).text()).contains("__PII_CARD_1__");
        }
    }

    @Nested
    class PhoneNumbers {
        @ParameterizedTest
        @ValueSource(strings = {
            "(555) 867-5309",
            "555-867-5309",
            "+1 555 867 5309"
        })
        void tokenizesUsPhone(String phone) {
            assertThat(service.tokenize("Call " + phone).text()).contains("__PII_PHONE_1__");
        }

        @Test
        void bareDigitsDontMatch() {
            // bare 10-digit number from e.g. math output must not be tokenized
            assertThat(service.tokenize("result is 5558675309").tokenMap()).isEmpty();
        }
    }

    @Nested
    class IpAddresses {
        @Test
        void tokenizesIpv4() {
            PiiDetectionService.TokenizedResult r = service.tokenize("Server at 192.168.1.100");
            assertThat(r.text()).isEqualTo("Server at __PII_IP_1__");
            assertThat(r.tokenMap()).containsEntry("__PII_IP_1__", "192.168.1.100");
        }

        @Test
        void doesNotTokenizeVersionNumbers() {
            assertThat(service.tokenize("version 1.2.3").tokenMap()).isEmpty();
        }
    }

    @Nested
    class MultiplePiiTypes {
        @Test
        void tokenizesAllPiiInSingleString() {
            String input = "Email john@example.com, SSN 123-45-6789, IP 10.0.0.1";
            PiiDetectionService.TokenizedResult r = service.tokenize(input);
            assertThat(r.text())
                .contains("__PII_EMAIL_1__")
                .contains("__PII_SSN_1__")
                .contains("__PII_IP_1__")
                .doesNotContain("john@example.com")
                .doesNotContain("123-45-6789")
                .doesNotContain("10.0.0.1");
        }
    }

    // -------------------------------------------------------------------------
    // detokenize — restoration
    // -------------------------------------------------------------------------

    @Nested
    class Detokenize {
        @Test
        void restoresTokensToOriginalValues() {
            PiiDetectionService.TokenizedResult r = service.tokenize("Call me at john@example.com");
            String restored = service.detokenize(r.text(), r.tokenMap());
            assertThat(restored).isEqualTo("Call me at john@example.com");
        }

        @Test
        void roundTripPreservesFullString() {
            String input = "Email john@example.com, SSN 123-45-6789, IP 10.0.0.1";
            PiiDetectionService.TokenizedResult r = service.tokenize(input);
            assertThat(service.detokenize(r.text(), r.tokenMap())).isEqualTo(input);
        }

        @Test
        void emptyTokenMapReturnsInputUnchanged() {
            assertThat(service.detokenize("clean text", Map.of())).isEqualTo("clean text");
        }

        @Test
        void nullInputReturnsNull() {
            assertThat(service.detokenize(null, Map.of())).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Nested
    class EdgeCases {
        @Test
        void nullInputReturnsNullResult() {
            PiiDetectionService.TokenizedResult r = service.tokenize(null);
            assertThat(r.text()).isNull();
            assertThat(r.tokenMap()).isEmpty();
        }

        @Test
        void blankInputReturnsSameBlank() {
            PiiDetectionService.TokenizedResult r = service.tokenize("   ");
            assertThat(r.text()).isEqualTo("   ");
        }

        @Test
        void cleanTextIsUnchanged() {
            String clean = "The quick brown fox jumps over the lazy dog.";
            assertThat(service.tokenize(clean).text()).isEqualTo(clean);
        }

        @Test
        void containsPiiReturnsTrueForEmail() {
            assertThat(service.containsPii("reach me at a@b.com")).isTrue();
        }

        @Test
        void containsPiiReturnsFalseForCleanText() {
            assertThat(service.containsPii("nothing sensitive here")).isFalse();
        }
    }
}
