package org.example.stringnormaliser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MatchTest {

  @Test
  void holdsTheCanonicalAndScore() {
    Match match = new Match("Accountant", 0.5);

    assertEquals("Accountant", match.canonical());
    assertEquals(0.5, match.score());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 1.0})
  void acceptsScoresAtTheEdgesOfTheRange(double score) {
    assertEquals(score, new Match("Accountant", score).score());
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
  void rejectsAScoreOutsideZeroToOne(double score) {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> new Match("Accountant", score));
    assertEquals("score must be between 0.0 and 1.0 but was " + score, thrown.getMessage());
  }

  @Test
  void rejectsNullCanonical() {
    NullPointerException thrown =
        assertThrows(NullPointerException.class, () -> new Match(null, 0.5));
    assertEquals("canonical must not be null", thrown.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void rejectsBlankCanonical(String blank) {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> new Match(blank, 0.5));
    assertEquals("canonical must not be blank", thrown.getMessage());
  }
}
