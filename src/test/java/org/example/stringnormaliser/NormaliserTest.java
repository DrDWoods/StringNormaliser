package org.example.stringnormaliser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NormaliserTest {

  private static final List<String> CANONICALS =
      List.of("Architect", "Software engineer", "Quantity surveyor", "Accountant");

  private static final String ALLOWED_CHARACTERS_MESSAGE =
      "may contain only letters A-Z, digits 0-9, spaces and # + & ' ( ) . , / : ; -";

  private final Normaliser normaliser = new Normaliser(CANONICALS);

  /** {@code count} distinct valid canonicals. */
  private static List<String> canonicalsNumbered(int count) {
    return IntStream.range(0, count).mapToObj(i -> "Role " + i).toList();
  }

  /** The message for a term or canonical with a disallowed character, shown escaped. */
  private static String disallowedCharacterMessage(String shown) {
    return "terms " + ALLOWED_CHARACTERS_MESSAGE + " but was \"" + shown + "\"";
  }

  /** The message for a blank term or canonical, shown escaped. */
  private static String blankMessage(String shown) {
    return "terms must not be blank but was \"" + shown + "\"";
  }

  @Nested
  class DefaultCanonicals {

    private final Normaliser defaultNormaliser = new Normaliser();

    @Test
    void supportsTheSampleUsage() {
      // One normaliser reused for several job titles, with each expected output asserted.
      String jt = "Java engineer";
      Normaliser n = new Normaliser();
      String normalisedTitle = n.normalise(jt);
      assertEquals("Software engineer", normalisedTitle);
      jt = "C# engineer";
      normalisedTitle = n.normalise(jt);
      assertEquals("Software engineer", normalisedTitle);
      jt = "Chief Accountant";
      normalisedTitle = n.normalise(jt);
      assertEquals("Accountant", normalisedTitle);
    }

    @ParameterizedTest(name = "\"{0}\" > \"{1}\"")
    @CsvSource({
      "Java engineer,     Software engineer",
      "C# engineer,       Software engineer",
      "Accountant,        Accountant",
      "Chief Accountant,  Accountant",
    })
    void matchesTheExampleTerms(String term, String expected) {
      assertEquals(expected, defaultNormaliser.normalise(term));
    }

    @Test
    void hasFourDefaultCanonicals() {
      assertEquals(CANONICALS, Normaliser.DEFAULT_CANONICALS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Architect", "Software engineer", "Quantity surveyor", "Accountant"})
    void matchesEachDefaultCanonical(String canonical) {
      assertEquals(canonical, defaultNormaliser.normalise(canonical));
    }

    @Test
    void theDefaultCanonicalsCannotBeChanged() {
      // IntelliJ flags modifying an immutable list, which is exactly what this test checks fails.
      //noinspection DataFlowIssue
      assertThrows(
          UnsupportedOperationException.class, () -> Normaliser.DEFAULT_CANONICALS.add("Plumber"));
      assertEquals(CANONICALS, Normaliser.DEFAULT_CANONICALS);
    }
  }

  @Nested
  class Matching {

    @ParameterizedTest(name = "\"{0}\" > \"{1}\"")
    @CsvSource({
      "Architect,                    Architect",
      "Senior Software Engineer,     Software engineer",
      "Quantity Surveyor (Junior),   Quantity surveyor",
      "Lead Solutions Architect,     Architect",
      "C++ engineer,                 Software engineer",
      "Trainee quantity surveyor,    Quantity surveyor",
    })
    void otherRealisticTerms(String term, String expected) {
      assertEquals(expected, normaliser.normalise(term));
    }

    @ParameterizedTest(name = "\"{0}\" > \"{1}\"")
    @CsvSource({
      "Surveyor quantity,        Quantity surveyor",
      "Engineer software,        Software engineer",
      "Accountant accountant,    Accountant",
      "Senior Senior Architect,  Architect",
    })
    void matchesDespiteWordOrderAndRepeatedWords(String term, String expected) {
      assertEquals(expected, normaliser.normalise(term));
    }

    @ParameterizedTest(name = "\"{0}\" > \"{1}\"")
    @CsvSource(
        quoteCharacter = '"',
        value = {
          "software-engineer,   Software engineer",
          "Engineer: Java,      Software engineer",
          "R&D (Architect),     Architect",
          "(Accountant),        Accountant",
          "Software engineer.,  Software engineer",
          "Accountant's,        Accountant",
          "Level 3 Architect,   Architect",
        })
    void toleratesPunctuationPossessivesAndNumbers(String term, String expected) {
      assertEquals(expected, normaliser.normalise(term));
    }

    @Test
    void ignoresApostrophesAroundAWord() {
      // Kept, they would make "'chef'" 2 edits from "chef": a word score of 0.67, below 0.8.
      Normaliser chefs = new Normaliser(List.of("Chef", "Architect"));

      assertEquals("Chef", chefs.normalise("'Chef'"));
    }

    @Test
    void ignoresWordsOfSymbolsAlone() {
      // Counted as words, "++" and "''" would lower the score from 1.0 to 0.5.
      Normaliser strict = new Normaliser(List.of("Architect"), 0.99);

      assertEquals("Architect", strict.normalise("Architect ++ ''"));
    }

    @ParameterizedTest(name = "\"{0}\" > \"{1}\"")
    @CsvSource({
      "Acountant,             Accountant",
      "Archtect,              Architect",
      "Chartered Accountants, Accountant",
      "Java enginer,          Software engineer",
    })
    void toleratesSmallTypos(String term, String expected) {
      assertEquals(expected, normaliser.normalise(term));
    }
  }

  @Nested
  class MixedCase {

    @ParameterizedTest(name = "\"{0}\" > \"{1}\"")
    @CsvSource({
      "ACCOUNTANT,         Accountant",
      "accountant,         Accountant",
      "aCcOuNtAnT,         Accountant",
      "SOFTWARE ENGINEER,  Software engineer",
      "quantity SURVEYOR,  Quantity surveyor",
    })
    void ignoresCase(String term, String expected) {
      assertEquals(expected, normaliser.normalise(term));
    }

    @Test
    void returnsTheCanonicalInItsOwnCase() {
      Normaliser shouting = new Normaliser(List.of("SOFTWARE Engineer"));

      assertEquals("SOFTWARE Engineer", shouting.normalise("software engineer"));
    }

    @Test
    void ignoresCaseWhateverTheDefaultLocale() {
      Locale original = Locale.getDefault();
      try {
        // Turkish lower-cases a plain capital I to a dotless i. The tokeniser would treat that as
        // a separator, splitting "architect" into "arch" and "tect".
        Locale.setDefault(Locale.forLanguageTag("tr"));
        Normaliser upperCase = new Normaliser(List.of("ARCHITECT"));

        assertEquals("ARCHITECT", upperCase.normalise("architect"));
        assertEquals("Architect", normaliser.normalise("ARCHITECT"));
      } finally {
        Locale.setDefault(original);
      }
    }
  }

  @Nested
  class Whitespace {

    @ParameterizedTest(name = "\"{0}\" > \"{1}\"")
    @CsvSource({
      "'   Accountant',            Accountant",
      "'Architect   ',             Architect",
      "'Quantity    surveyor',     Quantity surveyor",
      "'  software   ENGINEER ',   Software engineer",
    })
    void ignoresSpacesAroundAndBetweenWords(String term, String expected) {
      assertEquals(expected, normaliser.normalise(term));
    }

    @Test
    void returnsTheCanonicalWithoutSurroundingSpaces() {
      Normaliser padded = new Normaliser(List.of("  Software ENGINEER "));

      assertEquals("Software ENGINEER", padded.normalise("software engineer"));
    }

    @Test
    void keepsTheSpacesInsideACanonical() {
      Normaliser doubleSpaced = new Normaliser(List.of("Software  engineer"));

      assertEquals("Software  engineer", doubleSpaced.normalise("software engineer"));
    }

    static Stream<Arguments> whitespaceOtherThanSpaces() {
      return Stream.of(
          Arguments.of("Architect\t", "Architect\\u0009"),
          Arguments.of("Architect\n", "Architect\\u000A"),
          Arguments.of("Accountant\r", "Accountant\\u000D"),
          // Non-breaking space
          Arguments.of("Software\u00A0engineer", "Software\\u00A0engineer"),
          // Em space
          Arguments.of("Quantity\u2003surveyor", "Quantity\\u2003surveyor"),
          // Zero-width space
          Arguments.of("Account\u200Bant", "Account\\u200Bant"),
          // Byte order mark
          Arguments.of("\uFEFFAccountant", "\\uFEFFAccountant"));
    }

    @ParameterizedTest(name = "\"{1}\"")
    @MethodSource("whitespaceOtherThanSpaces")
    void rejectsATermWithWhitespaceOtherThanSpaces(String term, String shown) {
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> normaliser.normalise(term));
      assertEquals(disallowedCharacterMessage(shown), thrown.getMessage());
    }

    @ParameterizedTest(name = "\"{1}\"")
    @MethodSource("whitespaceOtherThanSpaces")
    void rejectsACanonicalWithWhitespaceOtherThanSpaces(String canonical, String shown) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> new Normaliser(List.of("Architect", canonical)));
      assertEquals(disallowedCharacterMessage(shown), thrown.getMessage());
    }

    @Test
    void acceptsATermPaddedToTheMaximumLength() {
      String longest = "Accountant" + " ".repeat(Normaliser.MAX_TERM_LENGTH - 10);

      assertEquals("Accountant", normaliser.normalise(longest));
    }

    @Test
    void countsSurroundingSpacesTowardsTheMaximumLength() {
      String tooLong = "Accountant" + " ".repeat(Normaliser.MAX_TERM_LENGTH - 9);

      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> normaliser.normalise(tooLong));
      assertEquals("terms must be at most 200 characters but was 201", thrown.getMessage());
    }
  }

  @Nested
  class TieBreak {

    @Test
    void prefersTheEarlierCanonicalOnATie() {
      // "Java engineer" scores 0.5 against both canonicals.
      Normaliser softwareFirst = new Normaliser(List.of("Software engineer", "Civil engineer"));
      Normaliser civilFirst = new Normaliser(List.of("Civil engineer", "Software engineer"));

      assertEquals("Software engineer", softwareFirst.normalise("Java engineer"));
      assertEquals("Civil engineer", civilFirst.normalise("Java engineer"));
    }

    @Test
    void prefersTheFirstOfTheTiedCanonicalsRatherThanTheFirstInTheList() {
      // "Java engineer" scores 0 against "Architect" and 0.5 against both engineers.
      Normaliser architectFirst =
          new Normaliser(List.of("Architect", "Civil engineer", "Software engineer"));

      assertEquals("Civil engineer", architectFirst.normalise("Java engineer"));
    }

    @Test
    void breaksATieAmongTheDefaultCanonicalsByTheirOrder() {
      // "Quantity engineer" scores 0.5 against both "Software engineer" and "Quantity surveyor".
      assertEquals("Software engineer", normaliser.normalise("Quantity engineer"));
    }

    @Test
    void breaksATieForATermContainingEveryCanonical() {
      // 0.5 against both two-word canonicals, which beats about 0.29 against the one-word ones.
      assertEquals(
          "Software engineer",
          normaliser.normalise("Architect Software engineer Quantity surveyor Accountant"));
    }

    @Test
    void picksTheHighestScoreWhereverItIsInTheList() {
      Normaliser seniorLast = new Normaliser(List.of("Civil engineer", "Senior software engineer"));

      assertEquals(
          "Senior software engineer", seniorLast.normalise("Senior Java software engineer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Architect", " Architect", "Architect ", "ARCHITECT", "architect"})
    void keepsTheFirstOfCanonicalsThatAreTheSameOnceCleaned(String first) {
      Normaliser duplicated = new Normaliser(List.of(first, "Architect"));

      assertEquals(first.strip(), duplicated.normalise("architect"));
    }

    @Test
    void prefersTheFirstOfCanonicalsThatDifferOnlyInInnerSpaces() {
      Normaliser doubleSpacedFirst =
          new Normaliser(List.of("Software  engineer", "Software engineer"));
      Normaliser singleSpacedFirst =
          new Normaliser(List.of("Software engineer", "Software  engineer"));

      assertEquals("Software  engineer", doubleSpacedFirst.normalise("software engineer"));
      assertEquals("Software engineer", singleSpacedFirst.normalise("software engineer"));
    }
  }

  @Nested
  class Blank {

    static Stream<Arguments> blankText() {
      String spaces = " ".repeat(Normaliser.MAX_TERM_LENGTH);
      return Stream.of(
          Arguments.of("", ""),
          Arguments.of(" ", " "),
          Arguments.of("\t\n", "\\u0009\\u000A"),
          // Em space
          Arguments.of("\u2003", "\\u2003"),
          // Ideographic space
          Arguments.of("\u3000", "\\u3000"),
          Arguments.of(spaces, spaces));
    }

    @ParameterizedTest(name = "\"{1}\"")
    @MethodSource("blankText")
    void rejectsABlankTerm(String term, String shown) {
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> normaliser.normalise(term));
      assertEquals(blankMessage(shown), thrown.getMessage());
    }

    @ParameterizedTest(name = "\"{1}\"")
    @MethodSource("blankText")
    void rejectsABlankCanonical(String canonical, String shown) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> new Normaliser(List.of("Architect", canonical)));
      assertEquals(blankMessage(shown), thrown.getMessage());
    }

    @Test
    void reportsANonBreakingSpaceAsADisallowedCharacterRatherThanBlank() {
      // String.isBlank does not count U+00A0 as whitespace.
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> normaliser.normalise("\u00A0"));
      assertEquals(disallowedCharacterMessage("\\u00A0"), thrown.getMessage());
    }

    @Test
    void checksTheLengthBeforeBlankness() {
      String tooLong = " ".repeat(Normaliser.MAX_TERM_LENGTH + 1);

      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> normaliser.normalise(tooLong));
      assertEquals("terms must be at most 200 characters but was 201", thrown.getMessage());
    }
  }

  @Nested
  class NoMatch {

    @ParameterizedTest
    @ValueSource(strings = {"Plumber", "Head chef", "Dev; Ops", "Bookkeeper", "3D"})
    void returnsNullForAnUnrelatedTerm(String term) {
      assertNull(normaliser.normalise(term));
    }

    @ParameterizedTest
    @ValueSource(strings = {"R&D", "C", "A B C", "C + +", "C ++"})
    void returnsNullForATermWithNoWords(String term) {
      // Single characters and symbols alone are dropped when tokenising, so nothing is left.
      assertNull(normaliser.normalise(term));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Engineering", "Architecture", "Surveying"})
    void returnsNullWhenTheClosestWordIsBelowTheWordThreshold(String term) {
      // Each is 3 edits from a canonical's word: about 0.73 against "engineer", 0.75 against
      // "architect" and about 0.67 against "surveyor", all below 0.8.
      assertNull(normaliser.normalise(term));
    }

    @Test
    void stillMatchesAWordExactlyAtTheWordThreshold() {
      // "acocuntant" is 2 edits from "accountant" in 10 letters: 0.8, exactly the threshold.
      assertEquals("Accountant", normaliser.normalise("Acocuntant"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Enigneer", "Java enigneer"})
    void doesNotMatchAWordWithTwoLettersSwapped(String term) {
      // A known limitation: Levenshtein distance counts a swap as 2 edits, so "enigneer" scores
      // 0.75 against "engineer", below the threshold.
      assertNull(normaliser.normalise(term));
    }

    @Test
    void neverMatchesACanonicalWithNoWords() {
      // "R&D" is a valid canonical, but has no words to compare, so it scores 0 against every term.
      Normaliser withInitialism = new Normaliser(List.of("R&D", "Accountant"));

      assertNull(withInitialism.normalise("R&D"));
    }
  }

  @Nested
  class Threshold {

    @Test
    void matchesOnlyAboveTheThreshold() {
      // "Chief Accountant" scores 2/3 against "Accountant".
      Normaliser lenient = new Normaliser(CANONICALS, 0.66);
      Normaliser strict = new Normaliser(CANONICALS, 0.67);

      assertEquals("Accountant", lenient.normalise("Chief Accountant"));
      assertNull(strict.normalise("Chief Accountant"));
    }

    @Test
    void doesNotMatchAScoreEqualToTheThreshold() {
      // "Java engineer" scores exactly 0.5 against "Software engineer".
      Normaliser aboveHalf = new Normaliser(List.of("Software engineer"), 0.5);

      assertNull(aboveHalf.normalise("Java engineer"));
    }

    @Test
    void doesNotMatchAZeroScoreByDefault() {
      Normaliser neverSimilar =
          new Normaliser(
              List.of("Architect"), (first, second) -> 0.0, Normaliser.DEFAULT_SCORE_THRESHOLD);

      assertNull(neverSimilar.normalise("Builder"));
    }

    @Test
    void treatsNegativeZeroAsZero() {
      Normaliser negativeZero = new Normaliser(CANONICALS, -0.0);

      assertEquals("Accountant", negativeZero.normalise("Chief Accountant"));
      assertNull(negativeZero.normalise("Plumber"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, Double.MIN_VALUE, 0.9999999999999999})
    void acceptsThresholdsAtTheEdgesOfTheRange(double threshold) {
      // An exact match scores 1.0, which is above every valid threshold.
      assertEquals("Accountant", new Normaliser(CANONICALS, threshold).normalise("Accountant"));
    }

    @ParameterizedTest
    @ValueSource(
        doubles = {
          -Double.MIN_VALUE,
          -0.1,
          1.0,
          1.1,
          Double.NaN,
          Double.NEGATIVE_INFINITY,
          Double.POSITIVE_INFINITY
        })
    void rejectsAThresholdOutsideZeroToOne(double threshold) {
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> new Normaliser(CANONICALS, threshold));
      assertEquals(
          "scoreThreshold must be at least 0.0 and less than 1.0 but was " + threshold,
          thrown.getMessage());
    }
  }

  @Nested
  class NullSafety {

    @Test
    void rejectsANullTerm() {
      NullPointerException thrown =
          assertThrows(NullPointerException.class, () -> normaliser.normalise(null));
      assertEquals("term must not be null", thrown.getMessage());
    }

    static Stream<Named<Executable>> constructorsGivenNullCanonicals() {
      return Stream.of(
          Named.of("Normaliser(List)", () -> new Normaliser(null)),
          Named.of("Normaliser(List, double)", () -> new Normaliser(null, 0.5)),
          Named.of(
              "Normaliser(List, WordSimilarityAlgorithm, double)",
              () -> new Normaliser(null, (first, second) -> 1.0, 0.5)));
    }

    @ParameterizedTest
    @MethodSource("constructorsGivenNullCanonicals")
    void everyConstructorRejectsNullCanonicals(Executable construction) {
      NullPointerException thrown = assertThrows(NullPointerException.class, construction);
      assertEquals("canonicals must not be null", thrown.getMessage());
    }

    static Stream<Named<List<String>>> canonicalsContainingNull() {
      return Stream.of(
          Named.of("only", Collections.singletonList(null)),
          Named.of("first", Arrays.asList(null, "Architect")),
          Named.of("middle", Arrays.asList("Architect", null, "Accountant")),
          Named.of("last", Arrays.asList("Architect", null)));
    }

    @ParameterizedTest
    @MethodSource("canonicalsContainingNull")
    void rejectsANullCanonicalWhereverItIs(List<String> canonicals) {
      NullPointerException thrown =
          assertThrows(NullPointerException.class, () -> new Normaliser(canonicals));
      assertEquals("canonicals elements must not be null", thrown.getMessage());
    }

    @Test
    void rejectsANullWordSimilarityAlgorithm() {
      NullPointerException thrown =
          assertThrows(NullPointerException.class, () -> new Normaliser(CANONICALS, null, 0.0));
      assertEquals("wordSimilarityAlgorithm must not be null", thrown.getMessage());
    }

    @Test
    void givesTheAlgorithmOnlyLowerCaseWordsNeverNullOrWhitespace() {
      List<String> words = new ArrayList<>();
      WordSimilarityAlgorithm recording =
          (first, second) -> {
            words.add(first);
            words.add(second);
            return first.equals(second) ? 1.0 : 0.0;
          };
      Normaliser recorded = new Normaliser(CANONICALS, recording, 0.0);

      for (String term :
          List.of(
              "  Senior SOFTWARE Engineer ",
              "C# / C++ developer",
              "R&D (Architect)",
              "Chief Accountant",
              "Quantity Surveyor (Junior)")) {
        recorded.normalise(term);
      }

      assertFalse(words.isEmpty());
      assertEquals(
          List.of(),
          words.stream().filter(word -> word == null || !word.matches("[a-z0-9#+']{2,}")).toList());
    }

    @Test
    void doesNotCallTheAlgorithmForATermWithNoWords() {
      List<String> calls = new ArrayList<>();
      WordSimilarityAlgorithm recording =
          (first, second) -> {
            calls.add(first + "|" + second);
            return 1.0;
          };
      Normaliser recorded = new Normaliser(CANONICALS, recording, 0.0);

      assertNull(recorded.normalise("R&D"));
      assertEquals(List.of(), calls);
    }
  }

  @Nested
  class Validation {

    static Stream<Arguments> disallowedCharacters() {
      return Stream.of(
          Arguments.of("Chief Accountant!", "Chief Accountant!"),
          Arguments.of("C@terer", "C@terer"),
          Arguments.of("Accountant\u0000", "Accountant\\u0000"),
          // Unit separator, the last control character before printable ASCII
          Arguments.of("Architect\u001F", "Architect\\u001F"),
          // Tilde, the last printable ASCII character, so not escaped
          Arguments.of("Architect~", "Architect~"),
          // Delete, the first character after printable ASCII
          Arguments.of("Architect\u007F", "Architect\\u007F"),
          // Rocket emoji, a surrogate pair
          Arguments.of("Dev \uD83D\uDE80 engineer", "Dev \\uD83D\\uDE80 engineer"),
          // A with grave accent
          Arguments.of("\u00C0ccountant", "\\u00C0ccountant"),
          // e with acute accent
          Arguments.of("Ing\u00E9nieur", "Ing\\u00E9nieur"),
          // En dash
          Arguments.of("Engineer \u2013 Java", "Engineer \\u2013 Java"),
          // Trade mark sign
          Arguments.of("Lead Engineer\u2122", "Lead Engineer\\u2122"),
          // Cyrillic capital A, which looks the same as a Latin A
          Arguments.of("\u0410ccountant", "\\u0410ccountant"),
          Arguments.of("\"Architect\"", "\\u0022Architect\\u0022"),
          Arguments.of("Architect\\", "Architect\\u005C"));
    }

    @ParameterizedTest(name = "\"{1}\"")
    @MethodSource("disallowedCharacters")
    void rejectsATermWithADisallowedCharacter(String term, String shown) {
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> normaliser.normalise(term));
      assertEquals(disallowedCharacterMessage(shown), thrown.getMessage());
    }

    @ParameterizedTest(name = "\"{1}\"")
    @MethodSource("disallowedCharacters")
    void rejectsACanonicalWithADisallowedCharacter(String canonical, String shown) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> new Normaliser(List.of("Architect", canonical)));
      assertEquals(disallowedCharacterMessage(shown), thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"#+#", "---", "123", "( )"})
    void rejectsATermWithoutALetter(String term) {
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> normaliser.normalise(term));
      assertEquals(
          "terms must contain at least one letter but was \"" + term + "\"", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"#+#", "---", "123", "( )"})
    void rejectsACanonicalWithoutALetter(String canonical) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> new Normaliser(List.of("Architect", canonical)));
      assertEquals(
          "terms must contain at least one letter but was \"" + canonical + "\"",
          thrown.getMessage());
    }

    @Test
    void escapesControlCharactersSoAMessageCannotForgeALogLine() {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> normaliser.normalise("Architect\nERROR forged log line"));

      assertEquals(
          disallowedCharacterMessage("Architect\\u000AERROR forged log line"), thrown.getMessage());
    }

    @Test
    void acceptsATermAtTheMaximumLength() {
      String longest = "a".repeat(Normaliser.MAX_TERM_LENGTH);

      assertNull(normaliser.normalise(longest));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1_000_000})
    void rejectsATermOverTheMaximumLengthWithoutCopyingIt(int extra) {
      String tooLong = "a".repeat(Normaliser.MAX_TERM_LENGTH + extra);

      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> normaliser.normalise(tooLong));
      assertEquals(
          "terms must be at most 200 characters but was " + tooLong.length(), thrown.getMessage());
    }

    @Test
    void acceptsACanonicalAtTheMaximumLength() {
      String longest = "a".repeat(Normaliser.MAX_TERM_LENGTH);

      assertEquals(longest, new Normaliser(List.of(longest)).normalise(longest));
    }

    @Test
    void rejectsACanonicalOverTheMaximumLength() {
      String tooLong = "a".repeat(Normaliser.MAX_TERM_LENGTH + 1);

      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> new Normaliser(List.of(tooLong)));
      assertEquals("terms must be at most 200 characters but was 201", thrown.getMessage());
    }

    @Test
    void acceptsCanonicalsWithPunctuation() {
      Normaliser punctuated =
          new Normaliser(List.of("C# / C++ developer", "R&D (lead)", "Engineer: Java"));

      assertEquals("C# / C++ developer", punctuated.normalise("C# developer"));
    }
  }

  @Nested
  class WordSimilarity {

    @Test
    void usesTheSuppliedAlgorithm() {
      WordSimilarityAlgorithm sameLength =
          (first, second) -> first.length() == second.length() ? 1.0 : 0.0;
      Normaliser byLength = new Normaliser(List.of("Architect", "Accountant"), sameLength, 0.0);

      assertEquals("Accountant", byLength.normalise("Bookkeeper"));
    }

    @Test
    void passesWordsInComparisonFormInBothDirections() {
      List<String> seen = new ArrayList<>();
      WordSimilarityAlgorithm recording =
          (first, second) -> {
            seen.add(first + "|" + second);
            return 1.0;
          };

      new Normaliser(List.of("  Cafe "), recording, 0.0).normalise(" ECLAIR  ");

      assertEquals(List.of("eclair|cafe", "cafe|eclair"), seen);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAScoreOutsideZeroToOne(double score) {
      Normaliser broken = new Normaliser(List.of("Architect"), (first, second) -> score, 0.0);

      IllegalStateException thrown =
          assertThrows(IllegalStateException.class, () -> broken.normalise("Builder"));
      assertEquals(
          "wordSimilarityAlgorithm must return a score between 0.0 and 1.0 but returned "
              + score
              + " for \"builder\" and \"architect\"",
          thrown.getMessage());
    }
  }

  @Nested
  class Construction {

    @Test
    void copiesCanonicalsSoAddingToTheListLaterHasNoEffect() {
      List<String> canonicals = new ArrayList<>(List.of("Architect"));
      Normaliser copied = new Normaliser(canonicals);

      canonicals.add("Accountant");

      assertNull(copied.normalise("Chief Accountant"));
    }

    @Test
    void copiesCanonicalsSoClearingTheListLaterHasNoEffect() {
      List<String> canonicals = new ArrayList<>(List.of("Architect"));
      Normaliser copied = new Normaliser(canonicals);

      canonicals.clear();

      assertEquals("Architect", copied.normalise("Architect"));
    }

    @Test
    void rejectsEmptyCanonicals() {
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> new Normaliser(List.of()));
      assertEquals("canonicals must not be empty", thrown.getMessage());
    }

    @Test
    void acceptsTheMaximumNumberOfCanonicalsAndCanMatchTheLast() {
      Normaliser largest = new Normaliser(canonicalsNumbered(Normaliser.MAX_NUM_CANONICALS));
      String last = "Role " + (Normaliser.MAX_NUM_CANONICALS - 1);

      assertEquals(last, largest.normalise(last));
    }

    @Test
    void rejectsTooManyCanonicals() {
      List<String> tooMany = canonicalsNumbered(Normaliser.MAX_NUM_CANONICALS + 1);

      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> new Normaliser(tooMany));
      assertEquals("Number of canonicals cannot be greater than 1000", thrown.getMessage());
    }
  }

  @Nested
  class ThreadSafety {

    private static final int THREADS = 8;
    private static final int ROUNDS = 500;

    @Test
    void givesTheSameAnswersWhenSharedBetweenThreads() throws Exception {
      List<String> terms =
          List.of(
              "Java engineer",
              "C# engineer",
              "Chief Accountant",
              "Senior Quantity Surveyor",
              "Plumber");
      List<String> answers =
          Arrays.asList(
              "Software engineer", "Software engineer", "Accountant", "Quantity surveyor", null);
      List<String> expected = new ArrayList<>();
      for (int round = 0; round < ROUNDS; round++) {
        expected.addAll(answers);
      }
      Callable<List<String>> normaliseEveryTermRepeatedly =
          () -> {
            List<String> results = new ArrayList<>();
            for (int round = 0; round < ROUNDS; round++) {
              for (String term : terms) {
                results.add(normaliser.normalise(term));
              }
            }
            return results;
          };

      try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
        for (Future<List<String>> results :
            pool.invokeAll(Collections.nCopies(THREADS, normaliseEveryTermRepeatedly))) {
          assertEquals(expected, results.get());
        }
      }
    }
  }
}
