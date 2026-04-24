package com.securellm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiDetectionServiceTest {

    private PiiDetectionService service;

    @BeforeEach
    void setUp() {
        service = new PiiDetectionService();
    }

    @Nested
    class Emails {
        @Test
        void redactsStandardEmail() {
            assertThat(service.redactPii("Contact me at john.doe@example.com please"))
                .isEqualTo("Contact me at [REDACTED-EMAIL] please");
        }

        @Test
        void redactsEmailWithPlusTag() {
            assertThat(service.redactPii("user+tag@subdomain.example.co.uk"))
                .contains("[REDACTED-EMAIL]");
        }

        @Test
        void doesNotRedactNonEmail() {
            assertThat(service.redactPii("not an email address"))
                .isEqualTo("not an email address");
        }
    }

    @Nested
    class SocialSecurityNumbers {
        @ParameterizedTest
        @ValueSource(strings = {"123-45-6789", "123 45 6789", "123456789"})
        void redactsSsn(String ssn) {
            assertThat(service.redactPii("SSN: " + ssn)).contains("[REDACTED-SSN]");
        }

        @Test
        void doesNotRedactPartialNumber() {
            // 5 digits is not an SSN
            assertThat(service.redactPii("order 12345")).doesNotContain("[REDACTED-SSN]");
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
        void redactsCardNumber(String card) {
            assertThat(service.redactPii("Card: " + card)).contains("[REDACTED-CARD]");
        }
    }

    @Nested
    class PhoneNumbers {
        @ParameterizedTest
        @ValueSource(strings = {
            "(555) 867-5309",
            "555-867-5309",
            "5558675309",
            "+1 555 867 5309"
        })
        void redactsUsPhone(String phone) {
            assertThat(service.redactPii("Call " + phone)).contains("[REDACTED-PHONE]");
        }
    }

    @Nested
    class IpAddresses {
        @Test
        void redactsIpv4() {
            assertThat(service.redactPii("Server at 192.168.1.100"))
                .isEqualTo("Server at [REDACTED-IP]");
        }

        @Test
        void doesNotRedactVersionNumbers() {
            // "1.2.3" only has 3 octets — should not match
            assertThat(service.redactPii("version 1.2.3")).doesNotContain("[REDACTED-IP]");
        }
    }

    @Nested
    class MultiplePiiTypes {
        @Test
        void redactsAllPiiInSingleString() {
            String input = "Email john@example.com, SSN 123-45-6789, IP 10.0.0.1";
            String result = service.redactPii(input);
            assertThat(result)
                .contains("[REDACTED-EMAIL]")
                .contains("[REDACTED-SSN]")
                .contains("[REDACTED-IP]")
                .doesNotContain("john@example.com")
                .doesNotContain("123-45-6789")
                .doesNotContain("10.0.0.1");
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void nullInputReturnsNull() {
            assertThat(service.redactPii(null)).isNull();
        }

        @Test
        void blankInputReturnsSameBlank() {
            assertThat(service.redactPii("   ")).isEqualTo("   ");
        }

        @Test
        void cleanTextIsUnchanged() {
            String clean = "The quick brown fox jumps over the lazy dog.";
            assertThat(service.redactPii(clean)).isEqualTo(clean);
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
