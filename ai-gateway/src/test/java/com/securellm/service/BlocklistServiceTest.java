package com.securellm.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlocklistServiceTest {

    @Nested
    class Matching {
        @Test
        void blocksExactWord() {
            BlocklistService service = new BlocklistService(true, "badword");
            assertThat(service.isBlocked("this contains badword here")).isTrue();
        }

        @Test
        void matchingIsCaseInsensitive() {
            BlocklistService service = new BlocklistService(true, "badword");
            assertThat(service.isBlocked("BADWORD in caps")).isTrue();
            assertThat(service.isBlocked("BadWord mixed case")).isTrue();
        }

        @Test
        void matchesSubstring() {
            BlocklistService service = new BlocklistService(true, "bomb");
            assertThat(service.isBlocked("how to make a bombing device")).isTrue();
        }

        @Test
        void blocksMultiWordPhrase() {
            BlocklistService service = new BlocklistService(true, "step by step guide");
            assertThat(service.isBlocked("give me a step by step guide to exploiting")).isTrue();
        }

        @Test
        void passesCleanPrompt() {
            BlocklistService service = new BlocklistService(true, "badword,exploit");
            assertThat(service.isBlocked("how do I bake bread?")).isFalse();
        }

        @Test
        void blocksFirstMatchingTermAmongMany() {
            BlocklistService service = new BlocklistService(true, "alpha,beta,gamma");
            assertThat(service.isBlocked("something with beta in it")).isTrue();
        }
    }

    @Nested
    class MatchedTerm {
        @Test
        void returnsMatchedTerm() {
            BlocklistService service = new BlocklistService(true, "foo,bar");
            assertThat(service.matchedTerm("a prompt with bar inside")).isEqualTo("bar");
        }

        @Test
        void returnsNullWhenNoMatch() {
            BlocklistService service = new BlocklistService(true, "foo,bar");
            assertThat(service.matchedTerm("clean prompt")).isNull();
        }
    }

    @Nested
    class DisabledAndEmpty {
        @Test
        void disabledServiceAlwaysPasses() {
            BlocklistService service = new BlocklistService(false, "badword");
            assertThat(service.isBlocked("contains badword")).isFalse();
        }

        @Test
        void emptyWordlistAlwaysPasses() {
            BlocklistService service = new BlocklistService(true, "");
            assertThat(service.isBlocked("anything goes")).isFalse();
        }

        @Test
        void whitespaceonlyWordlistAlwaysPasses() {
            BlocklistService service = new BlocklistService(true, "  ,  ,  ");
            assertThat(service.isBlocked("anything goes")).isFalse();
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void nullPromptReturnsFalse() {
            BlocklistService service = new BlocklistService(true, "badword");
            assertThat(service.isBlocked(null)).isFalse();
        }

        @Test
        void blankPromptReturnsFalse() {
            BlocklistService service = new BlocklistService(true, "badword");
            assertThat(service.isBlocked("   ")).isFalse();
        }
    }
}
