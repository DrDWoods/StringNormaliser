package org.example.stringnormaliser;

import java.util.Locale;

/**
 * Puts text into comparison form: no whitespace at the start or end, and lower case, so that "
 * Software ENGINEER\t" becomes "software engineer".
 *
 * <p>{@link Normaliser} applies it to the term and to every canonical, so both sides are always
 * compared in the same form, and canonicals with the same form are merged. The form is only ever
 * compared, never returned to a caller.
 *
 * <p>Lower-casing uses {@link Locale#ENGLISH}, so the result does not depend on the default locale.
 * With a Turkish default locale, for example, a plain {@code toLowerCase()} turns the "I" of
 * "ARCHITECT" into a dotless i.
 *
 * <p>Everything else, including punctuation and whitespace between words, is left in place for
 * {@link Tokeniser} to deal with.
 */
final class TermCleaner {

  String clean(String text) {
    assert text != null;
    return text.strip().toLowerCase(Locale.ENGLISH);
  }
}
