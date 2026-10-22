package org.example.stringnormaliser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TermCleanerTest {

  private final TermCleaner cleaner = new TermCleaner();

  @Test
  void stripsLeadingAndTrailingWhitespace() {
    assertEquals("software engineer", cleaner.clean(" \t\nsoftware engineer \r\n"));
  }

  @ParameterizedTest(name = "strips U+{0} from the ends but not the middle")
  @ValueSource(
      strings = {
        "0020", // space
        "0009", // tab
        "000A", // line feed
        "000D", // carriage return
        "2003", // em space
        "2028", // line separator
        "3000", // ideographic space
      })
  void stripsWhitespace(String codePoint) {
    String c = Character.toString(Integer.parseInt(codePoint, 16));

    assertEquals(
        "software" + c + "engineer", cleaner.clean(c + c + "software" + c + "engineer" + c));
  }

  @Test
  void lowerCases() {
    assertEquals("chief accountant", cleaner.clean("Chief ACCOUNTANT"));
  }

  @Test
  void lowerCasesTheSameWayWhateverTheDefaultLocale() {
    Locale original = Locale.getDefault();
    try {
      // Turkish lower-cases a plain capital I to a dotless i.
      Locale.setDefault(Locale.forLanguageTag("tr"));

      assertEquals("architect", cleaner.clean("ARCHITECT"));
    } finally {
      Locale.setDefault(original);
    }
  }

  @ParameterizedTest(name = "\"{0}\" is unchanged")
  @CsvSource({
    "chief accountant",
    "r&d engineer",
    "c# / c++",
    "'software   engineer'",
    "x",
  })
  void leavesCleanTextUnchanged(String input) {
    assertEquals(input, cleaner.clean(input));
  }

  @ParameterizedTest(name = "\"{0}\" becomes empty")
  @ValueSource(strings = {"", " ", " \t\r\n"})
  void returnsEmptyForWhitespaceOnly(String input) {
    assertEquals("", cleaner.clean(input));
  }
}
