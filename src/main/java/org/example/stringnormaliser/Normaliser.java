package org.example.stringnormaliser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * Maps each term, a free-text string such as "Java engineer", onto the closest of a fixed list of
 * canonicals, such as "Software engineer".
 *
 * <p>Every canonical and term must satisfy the same rules: at most {@link #MAX_TERM_LENGTH}
 * characters, not blank, only ASCII letters, digits, spaces and {@code # + & ' ( ) . , / : ; -},
 * and at least one letter. Each is then put into comparison form, without surrounding whitespace
 * and in lower case. Canonicals with the same comparison form are merged, keeping the first.
 *
 * <p>Every canonical is given a quality score {@code q} ({@code 0.0 <= q <= 1.0}) against the term
 * by comparing their words with a {@link WordSimilarityAlgorithm}, and the highest-scoring
 * canonical wins. On a tie, the canonical listed first wins. The winner is only returned if its
 * score is strictly greater than the score threshold.
 *
 * <p>Instances are immutable, and safe to share between threads provided the word similarity
 * algorithm is. The default, Levenshtein edit distance, holds no state. The algorithm is never
 * passed {@code null}.
 */
public final class Normaliser {

  /** The default canonicals, four job titles, used by {@link #Normaliser()}. */
  public static final List<String> DEFAULT_CANONICALS =
      List.of("Architect", "Software engineer", "Quantity surveyor", "Accountant");

  /** By default, any canonical with a non-zero score can be matched. */
  public static final double DEFAULT_SCORE_THRESHOLD = 0.0;

  /**
   * The longest canonical or term accepted. Real terms are far shorter, and the limit keeps the
   * cost of scoring bounded whatever the input.
   */
  public static final int MAX_TERM_LENGTH = 200;

  /** The most canonicals accepted. Each call scores every canonical, so this bounds its cost. */
  public static final int MAX_NUM_CANONICALS = 1000;

  private static final TermCleaner TERM_CLEANER = new TermCleaner();

  private final Matcher matcher;
  private final double scoreThreshold;

  /**
   * Creates a normaliser for the {@link #DEFAULT_CANONICALS} that compares words by Levenshtein
   * edit distance and accepts any canonical with a non-zero score.
   */
  public Normaliser() {
    this(DEFAULT_CANONICALS);
  }

  /**
   * Creates a normaliser that compares words by Levenshtein edit distance and accepts any canonical
   * with a non-zero score.
   *
   * @param canonicals the canonicals, in order of preference for ties; between 1 and {@link
   *     #MAX_NUM_CANONICALS}, each satisfying the rules in the class description
   * @throws NullPointerException if {@code canonicals} or any canonical in it is {@code null}
   * @throws IllegalArgumentException if {@code canonicals} is empty, too long, or contains an
   *     invalid canonical
   */
  public Normaliser(List<String> canonicals) {
    this(canonicals, DEFAULT_SCORE_THRESHOLD);
  }

  /**
   * Creates a normaliser that compares words by Levenshtein edit distance and accepts only
   * canonicals that score strictly above {@code scoreThreshold}.
   *
   * @param canonicals the canonicals, as for {@link #Normaliser(List)}
   * @param scoreThreshold a canonical must score strictly above this, in {@code [0.0, 1.0)}, to
   *     match
   * @throws NullPointerException if {@code canonicals} or any canonical in it is {@code null}
   * @throws IllegalArgumentException if {@code canonicals} is empty, too long, or contains an
   *     invalid canonical, or if {@code scoreThreshold} is out of range
   */
  public Normaliser(List<String> canonicals, double scoreThreshold) {
    this(canonicals, new LevenshteinSimilarity(), scoreThreshold);
  }

  /**
   * Creates a normaliser that compares words using {@code wordSimilarityAlgorithm} and accepts only
   * canonicals that score strictly above {@code scoreThreshold}.
   *
   * @param canonicals the canonicals, as for {@link #Normaliser(List)}
   * @param wordSimilarityAlgorithm scores how similar two words are
   * @param scoreThreshold a canonical must score strictly above this, in {@code [0.0, 1.0)}, to
   *     match
   * @throws NullPointerException if {@code canonicals}, any canonical in it, or {@code
   *     wordSimilarityAlgorithm} is {@code null}
   * @throws IllegalArgumentException if {@code canonicals} is empty, too long, or contains an
   *     invalid canonical, or if {@code scoreThreshold} is out of range
   */
  public Normaliser(
      List<String> canonicals,
      WordSimilarityAlgorithm wordSimilarityAlgorithm,
      double scoreThreshold) {
    Objects.requireNonNull(canonicals, "canonicals must not be null");
    if (canonicals.size() > MAX_NUM_CANONICALS) {
      throw new IllegalArgumentException(
          "Number of canonicals cannot be greater than " + MAX_NUM_CANONICALS);
    }

    List<String> copy = new ArrayList<>(canonicals); // Allows null elements
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("canonicals must not be empty");
    }
    for (String canonical : copy) {
      Objects.requireNonNull(canonical, "canonicals elements must not be null");
      checkTerm(canonical);
    }

    Objects.requireNonNull(wordSimilarityAlgorithm, "wordSimilarityAlgorithm must not be null");

    if (!(scoreThreshold >= 0.0 && scoreThreshold < 1.0)) {
      throw new IllegalArgumentException(
          "scoreThreshold must be at least 0.0 and less than 1.0 but was " + scoreThreshold);
    }

    this.matcher = new Matcher(mapCleanToOriginal(copy), wordSimilarityAlgorithm);
    this.scoreThreshold = scoreThreshold;
  }

  /**
   * Returns the canonical that best matches {@code term}.
   *
   * @param term the free-text term, which must satisfy the rules in the class description
   * @return the best canonical, without whitespace at the start or end, or {@code null} if none
   *     scores strictly above the threshold
   * @throws NullPointerException if {@code term} is {@code null}
   * @throws IllegalArgumentException if {@code term} breaks a rule
   * @throws IllegalStateException if the word similarity algorithm returns a score outside {@code
   *     [0.0, 1.0]}
   */
  public String normalise(String term) {
    return match(term).map(Match::canonical).orElse(null);
  }

  /**
   * Returns the canonical that best matches {@code term}, with its score.
   *
   * @param term the free-text term, which must satisfy the rules in the class description
   * @return the best canonical and its score, or empty if none scores strictly above the threshold
   * @throws NullPointerException if {@code term} is {@code null}
   * @throws IllegalArgumentException if {@code term} breaks a rule
   * @throws IllegalStateException if the word similarity algorithm returns a score outside {@code
   *     [0.0, 1.0]}
   */
  private Optional<Match> match(String term) {
    Objects.requireNonNull(term, "term must not be null");
    checkTerm(term);
    Match best = matcher.findBestMatch(TERM_CLEANER.clean(term));
    return Optional.of(best).filter(match -> match.score() > scoreThreshold);
  }

  /**
   * Maps each canonical's comparison (clean) form to the original form without surrounding
   * whitespace, in list order. Canonicals with the same comparison form are merged, keeping the
   * first.
   */
  private static SequencedMap<String, String> mapCleanToOriginal(List<String> canonicals) {
    assert canonicals != null;
    assert canonicals.stream().noneMatch(Objects::isNull);

    SequencedMap<String, String> canonicalsCleanToOriginal = new LinkedHashMap<>();
    for (String canonical : canonicals) {
      // Condense duplicates, prioritising input order
      canonicalsCleanToOriginal.putIfAbsent(TERM_CLEANER.clean(canonical), canonical.strip());
    }
    return canonicalsCleanToOriginal;
  }

  /**
   * Checks {@code term}, which may be a canonical, against every {@link TermRule}, in order.
   *
   * @throws IllegalArgumentException naming the first rule {@code term} breaks
   */
  private static void checkTerm(String term) {
    assert term != null;
    for (TermRule rule : TermRule.values()) {
      if (!rule.isSatisfiedBy(term)) {
        throw new IllegalArgumentException(rule.failureMessage(term));
      }
    }
  }
}
