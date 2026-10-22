package org.example.stringnormaliser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TokeniserTest {

  @Test
  void splitsOnWhitespaceAndLowerCases() {
    assertEquals(List.of("chief", "accountant"), Tokeniser.tokenise("  Chief \t ACCOUNTANT "));
  }

  @Test
  void treatsPunctuationAsSeparator() {
    assertEquals(
        List.of("software", "engineer", "junior"),
        Tokeniser.tokenise("Software-Engineer (Junior)"));
  }

  @Test
  void keepsProgrammingLanguageSymbols() {
    assertEquals(List.of("c#", "c++", "engineer"), Tokeniser.tokenise("C#/C++ engineer"));
  }

  @Test
  void keepsApostrophesInsideWords() {
    assertEquals(List.of("director's", "assistant"), Tokeniser.tokenise("Director's assistant"));
    assertEquals(List.of("o'brien"), Tokeniser.tokenise("O'Brien"));
  }

  @Test
  void dropsApostrophesAroundWords() {
    assertEquals(List.of("chef"), Tokeniser.tokenise("'Chef'"));
    assertEquals(List.of("architect"), Tokeniser.tokenise("''Architect''"));
    assertEquals(List.of("accountants", "office"), Tokeniser.tokenise("Accountants' office"));
  }

  @Test
  void dropsSingleCharacterTokens() {
    assertEquals(List.of("engineer", "manager"), Tokeniser.tokenise("Engineer + Manager"));
    assertEquals(List.of("lead"), Tokeniser.tokenise("R&D lead"));
    assertEquals(List.of(), Tokeniser.tokenise("A B C"));
  }

  @Test
  void dropsWordsWithNoLetterOrDigit() {
    assertEquals(List.of("architect"), Tokeniser.tokenise("Architect ++ '' ##"));
    assertEquals(List.of("c#", "c++", "3d"), Tokeniser.tokenise("C# C++ 3D +#"));
  }

  @Test
  void keepsDigits() {
    assertEquals(List.of("engineer", "3d"), Tokeniser.tokenise("Engineer 3D"));
  }

  @Test
  void returnsEmptyListWhenThereAreNoWords() {
    assertEquals(List.of(), Tokeniser.tokenise(""));
    assertEquals(List.of(), Tokeniser.tokenise(" - / . "));
  }
}
