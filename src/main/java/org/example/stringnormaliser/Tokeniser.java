package org.example.stringnormaliser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Splits text into lower-case words.
 *
 * <p>Every character other than an ASCII letter, digit, {@code #}, {@code +} or apostrophe
 * separates words, so "C#", "C++" and "Director's" each stay one word. Apostrophes at the start or
 * end of a word are dropped, so "'Chef'" becomes "chef". Single characters, and words with no
 * letter or digit such as "++", are dropped too, so text such as {@code R&D} or {@code C ++} has no
 * words at all.
 *
 * <p>{@link Normaliser} has already lower-cased the text, so lower-casing again here changes
 * nothing.
 */
final class Tokeniser {

  private static final Pattern SEPARATORS = Pattern.compile("[^A-Za-z0-9#+']+");
  private static final Pattern OUTER_APOSTROPHES = Pattern.compile("^'+|'+$");
  private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[A-Za-z0-9]");

  private Tokeniser() {}

  static List<String> tokenise(String text) {
    assert text != null;

    List<String> tokens = new ArrayList<>();
    for (String token : SEPARATORS.split(text.toLowerCase(Locale.ENGLISH))) {
      // An apostrophe inside a word, as in "director's", is part of it; around a word, as in
      // "'chef'", it is a quote.
      String word = OUTER_APOSTROPHES.matcher(token).replaceAll("");
      // Single characters, such as the "r" and "d" of "R&D", and symbols alone, such as "++",
      // carry no meaning and only dilute the score. This also drops the empty first token split
      // gives when the text starts with a separator.
      if (word.length() > 1 && LETTER_OR_DIGIT.matcher(word).find()) {
        tokens.add(word);
      }
    }
    return List.copyOf(tokens);
  }
}
