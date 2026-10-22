package org.example.stringnormaliser;

import static org.example.stringnormaliser.TermRule.ALLOWED_CHARACTERS;
import static org.example.stringnormaliser.TermRule.HAS_LETTER;
import static org.example.stringnormaliser.TermRule.MAX_LENGTH;
import static org.example.stringnormaliser.TermRule.NOT_BLANK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TermRuleTest {

  @Test
  void checksTheLengthFirstSoNoOtherRuleScansAnOverLongTerm() {
    assertEquals(
        List.of(MAX_LENGTH, NOT_BLANK, ALLOWED_CHARACTERS, HAS_LETTER), List.of(TermRule.values()));
  }

  @Nested
  class MaxLength {

    @Test
    void acceptsATermAtTheLimit() {
      assertTrue(MAX_LENGTH.isSatisfiedBy("a".repeat(Normaliser.MAX_TERM_LENGTH)));
    }

    @Test
    void rejectsATermOverTheLimit() {
      assertFalse(MAX_LENGTH.isSatisfiedBy("a".repeat(Normaliser.MAX_TERM_LENGTH + 1)));
    }

    @Test
    void reportsTheLengthRatherThanTheTerm() {
      assertEquals(
          "terms must be at most 200 characters but was 1000",
          MAX_LENGTH.failureMessage("a".repeat(1_000)));
    }
  }

  @Nested
  class NotBlank {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void rejectsBlankTerms(String blank) {
      assertFalse(NOT_BLANK.isSatisfiedBy(blank));
    }

    @Test
    void acceptsATermWithAnythingButWhitespace() {
      assertTrue(NOT_BLANK.isSatisfiedBy(" - "));
    }
  }

  @Nested
  class AllowedCharacters {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "Software engineer",
          "C# / C++ developer",
          "R&D (lead)",
          "Engineer: Java; Ops",
          "Director's assistant, U.K.",
          "Engineer 3D",
          "-",
        })
    void acceptsAsciiLettersDigitsSpacesAndCommonPunctuation(String term) {
      assertTrue(ALLOWED_CHARACTERS.isSatisfiedBy(term));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "Architect!",
          "C@terer",
          "Quantity\tsurveyor",
          "Quantity\nsurveyor",
          "Ingénieur",
          "Engineer – Java",
          "Engineer™",
          "Developer 🚀",
          "\"Architect\"",
          "Architect\\",
        })
    void rejectsEverythingElse(String term) {
      assertFalse(ALLOWED_CHARACTERS.isSatisfiedBy(term));
    }
  }

  @Nested
  class HasLetter {

    @ParameterizedTest
    @ValueSource(strings = {"C++", "3D", "a"})
    void acceptsATermWithALetter(String term) {
      assertTrue(HAS_LETTER.isSatisfiedBy(term));
    }

    @ParameterizedTest
    @ValueSource(strings = {"---", "123", "#+#", "( )"})
    void rejectsPunctuationOrNumbersAlone(String term) {
      assertFalse(HAS_LETTER.isSatisfiedBy(term));
    }
  }

  @Nested
  class FailureMessage {

    @Test
    void namesTheRuleAndQuotesTheTerm() {
      assertEquals(
          "terms must contain at least one letter but was \"---\"",
          HAS_LETTER.failureMessage("---"));
    }

    @Test
    void escapesEverythingOutsidePrintableAsciiAndTheQuoteAndBackslash() {
      assertEquals(
          "terms may contain only letters A-Z, digits 0-9, spaces and # + & ' ( ) . , / : ; -"
              + " but was \"a\\u0009b\\u000Ac\\u00E9\\u0022\\u005C\\uD83D\\uDE80\"",
          ALLOWED_CHARACTERS.failureMessage("a\tb\ncé\"\\🚀"));
    }

    @Test
    void quotesABlankTermSoItsWhitespaceShows() {
      assertEquals("terms must not be blank but was \" \\u0009\"", NOT_BLANK.failureMessage(" \t"));
    }
  }
}
