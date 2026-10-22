package org.example.stringnormaliser;

import java.util.regex.Pattern;

/**
 * The rules every canonical and term must satisfy, in the order {@link Normaliser} checks them. The
 * first rule broken gives the exception message.
 *
 * <p>The order matters. {@link #MAX_LENGTH} comes first, so no later rule ever scans an over-long
 * term or copies one into a message. {@link #ALLOWED_CHARACTERS} comes before {@link #HAS_LETTER},
 * so a term with a disallowed character is reported for that character.
 *
 * <p>A term must satisfy every rule, so each rule must make sense on its own; a whitelist split
 * across several rules would reject everything.
 */
enum TermRule {

  /** Keeps the cost of scoring bounded, whatever the input. */
  MAX_LENGTH("must be at most " + Normaliser.MAX_TERM_LENGTH + " characters") {
    @Override
    boolean isSatisfiedBy(String term) {
      assert term != null;
      return term.length() <= Normaliser.MAX_TERM_LENGTH;
    }

    /** Gives the length rather than the term, which may be very long. */
    @Override
    String describeActual(String term) {
      assert term != null;
      return String.valueOf(term.length());
    }
  },

  /** Rejects empty terms and terms of whitespace alone, which have nothing to match. */
  NOT_BLANK("must not be blank") {
    @Override
    boolean isSatisfiedBy(String term) {
      assert term != null;
      return !term.isBlank();
    }
  },

  /**
   * Printable ASCII only, so accented letters, other scripts and control characters such as tabs
   * are rejected rather than handled.
   */
  ALLOWED_CHARACTERS(
      "may contain only letters A-Z, digits 0-9, spaces and # + & ' ( ) . , / : ; -") {
    @Override
    boolean isSatisfiedBy(String term) {
      assert term != null;
      return ALLOWED_TERM.matcher(term).matches();
    }
  },

  /** Rejects punctuation or numbers alone, such as "---" or "123". */
  HAS_LETTER("must contain at least one letter") {
    @Override
    boolean isSatisfiedBy(String term) {
      assert term != null;
      return LETTER.matcher(term).find();
    }
  };

  // Character groups, written as the contents of a regex [...] class.
  private static final String LETTERS = "A-Za-z";
  private static final String DIGITS = "0-9";
  private static final String SPACES = " ";
  // The punctuation found in real terms, such as "C# / C++ developer" or "R&D (lead)". The hyphen
  // comes last, so the regex reads it as itself rather than a range. The brackets enclose other
  // characters only because IntelliJ checks this constant as a regex alone and flags "()" as an
  // empty group; inside the [...] class they are plain characters.
  private static final String PUNCTUATION = "#+&'(.,/:;)-";

  private static final Pattern ALLOWED_TERM =
      Pattern.compile("[" + LETTERS + DIGITS + SPACES + PUNCTUATION + "]+");
  private static final Pattern LETTER = Pattern.compile("[" + LETTERS + "]");

  private final String description;

  /**
   * @param description what the rule requires, phrased to follow "terms", such as "must contain at
   *     least one letter"
   */
  TermRule(String description) {
    this.description = description;
  }

  abstract boolean isSatisfiedBy(String term);

  /**
   * The exception message for a term or canonical that breaks this rule, such as {@code terms must
   * contain at least one letter but was "---"}.
   */
  final String failureMessage(String term) {
    assert term != null;
    return "terms " + description + " but was " + describeActual(term);
  }

  /** How the message shows the term that broke the rule: quoted, unless a rule says otherwise. */
  String describeActual(String term) {
    assert term != null;
    return quote(term);
  }

  /**
   * Quotes {@code term}, writing every character outside printable ASCII, and the quote and
   * backslash themselves, as a Java Unicode escape. A message can then never carry a line break or
   * control character into a log, and the quotes always mark where the term starts and ends.
   */
  private static String quote(String term) {
    assert term != null;

    StringBuilder quoted = new StringBuilder(term.length() + 2).append('"');
    for (int i = 0; i < term.length(); i++) {
      char c = term.charAt(i);
      if (c >= ' ' && c <= '~' && c != '"' && c != '\\') {
        quoted.append(c);
      } else {
        quoted.append(String.format("\\u%04X", (int) c));
      }
    }
    return quoted.append('"').toString();
  }
}
