# StringNormaliser

A Java library that finds the best match for a **term**, a free-text string such as "Java engineer", in a fixed list of **canonicals**, such as "Software engineer".

```java
Normaliser n = new Normaliser(); // the four default canonicals; or pass your own, e.g. new Normaliser(List.of("Architect", "Plumber"))
n.normalise("Java engineer");    // "Software engineer"
n.normalise("C# engineer");      // "Software engineer"
n.normalise("Accountant");       // "Accountant"
n.normalise("Chief Accountant"); // "Accountant"
n.normalise("Plumber");          // null
```

## Requirements

- JDK 21 or later
- Nothing else. Maven 3.9.16 is pinned through the Maven Wrapper (`mvnw` / `mvnw.cmd`) and downloads on first use. `.mvn/jvm.config` exports the JDK compiler packages the formatter needs, so Spotless doesn't open them with the deprecated `sun.misc.Unsafe`.
## Build, test and run

```shell
./mvnw verify                 # compile, run the unit tests, check formatting, build the jar  (Windows: .\mvnw.cmd verify)
./mvnw spotless:apply         # reformat the code to Google Java Style
java -jar target/string-normaliser-1.0-SNAPSHOT.jar                        # run the default examples
java -jar target/string-normaliser-1.0-SNAPSHOT.jar "Senior Quantity Surveyor" "Plumber"
```

```text
"Java engineer" > "Software engineer"
"C# engineer" > "Software engineer"
"Accountant" > "Accountant"
"Chief Accountant" > "Accountant"
```

In an IDE, open the folder as a Maven project. Run `Main` for the demo and the `src/test/java` folder for the tests.

## How matching works

Every canonical gets a quality score **q** (0.0 ≤ q ≤ 1.0) against the term. The highest score wins.

1. **Validate.** Canonicals and the term must pass the same rules, checked in this order: at most 200 characters; not blank; only ASCII letters, digits, spaces and `# + & ' ( ) . , / : ; -`; at least one letter. There must be between 1 and 1000 canonicals.
2. **Clean.** Surrounding whitespace is stripped and the text is lower-cased. Canonicals that are the same once cleaned are merged, and the first is kept. The cleaned form is only compared, never returned: a match returns the canonical as given, without surrounding whitespace.
3. **Tokenise.** Text is split into words at every character other than a letter, digit, `#`, `+` or `'`, so `C#`, `C++` and `Director's` stay whole. Apostrophes at either end of a word are dropped, so `'Chef'` becomes `chef`. Single characters, such as the `r` and `d` of `R&D`, and words with no letter or digit, such as `++`, are dropped too. Canonicals are tokenised once, when the `Normaliser` is built.
4. **Match words.** Each word is paired with its most similar word on the other side. Word similarity is Levenshtein edit distance scaled to 0–1, so small typos still count (`acountant` vs `accountant` = 0.9). Pairs below 0.8 count as 0.
5. **Score.** q is the sum of the best pair scores in both directions divided by the total number of words. When every word matches exactly or not at all, and none is repeated, this is the Dice coefficient `2|A ∩ B| / (|A| + |B|)`:
   - "Chief Accountant" vs "Accountant" → 2 × 1 / (2 + 1) = **0.67**
   - "Java engineer" vs "Software engineer" → 2 × 1 / (2 + 2) = **0.50**
   - "Java engineer" vs "Accountant" → **0.00**

**Ties** go to the canonical listed first: list order is the caller's stated preference. **No match:** unless the best score is strictly above the score threshold (default 0.0, so at least one word must match), `normalise` returns `null`.

## Design

Every class is in `org.example.stringnormaliser`.

| Type | Visibility | Responsibility |
|---|---|---|
| `Normaliser` | public | Entry point. Validates the canonicals and the term against `TermRule`, cleans them, and applies the score threshold to the best match. |
| `Match` | public | Record of the chosen canonical and its score. Validates both. |
| `WordSimilarityAlgorithm` | public | Interface: scores one word against another, in [0, 1]. The only extension point. |
| `Main` | public | Command-line demo. |
| `Matcher` | package-private | Holds each canonical with its words, scores every canonical against the term and picks the best. Checks every word score. |
| `TermRule` | package-private | Enum of the validation rules, declared in the order they are checked. Builds the exception messages. |
| `TermCleaner` | package-private | Produces the comparison form: stripped and lower-cased. |
| `Tokeniser` | package-private | Splits text into words. |
| `LevenshteinSimilarity` | package-private | The default `WordSimilarityAlgorithm`: edit distance scaled to [0, 1]. |

- **A simple API.** `new Normaliser()` uses four default canonicals (`Normaliser.DEFAULT_CANONICALS`), and `normalise` returns a `String`. Other constructors take your own canonicals, word measure and threshold. When nothing matches, `normalise` returns `null` rather than throwing: free-text titles often match nothing, so that is an expected outcome, not an exceptional one (*Effective Java* Item 69). Returning the term itself or the first canonical would give a wrong answer silently.
- **One extension point.** A client can replace the word measure, e.g. `new Normaliser(canonicals, myAlgorithm, 0.2)`. The word threshold (0.8) and the way word scores combine into q are fixed.
- **Canonicals are returned as given.** `TermCleaner` produces the comparison form, which decides which canonicals are duplicates and is then split into words by `Tokeniser`. As in OpenRefine's clustering, that form is only compared, never returned: `Matcher` keeps each canonical alongside its words and always returns the canonical.
- **Work done once.** Canonicals are validated, cleaned and tokenised in the constructor. Each call to `normalise` only processes the term.
- **Immutability.** All classes are immutable and safe to share between threads, provided a client's `WordSimilarityAlgorithm` is. The list of canonicals is copied before its elements are checked, so the caller cannot change them afterwards.
- **Defensive coding** (following *Effective Java* Items 49, 50 and 72). The package is the trust boundary. Public constructors and methods check their arguments and document them with `@throws`. Package-private classes check their callers' assumptions with `assert`.
  - A `null` argument throws `NullPointerException`, e.g. `term must not be null`.
  - A canonical or term that breaks a `TermRule` throws `IllegalArgumentException` describing the rule, e.g. `terms must contain at least one letter but was "---"`. The length rule is checked first, so no other rule ever scans an over-long string, and its message gives the length rather than the string.
  - Messages escape every character outside printable ASCII (`Café` becomes `"Caf\u00E9"`), so a message can never carry a line break or control character into a log.
  - No canonicals, more than 1000 canonicals, or a score threshold outside [0.0, 1.0) throws `IllegalArgumentException`.
  - Scores from a client's `WordSimilarityAlgorithm` are outside data, so each one is checked. A score outside [0, 1], or `NaN`, would silently pick the wrong canonical, so it throws `IllegalStateException` instead. The caller's arguments were valid, so the exception does not blame them.
  - `Match` validates its own fields, because anyone can construct one.
  - `TermCleaner` and `Tokeniser` lower-case with `Locale.ENGLISH`, so matching does not depend on the default locale.

## Testing

`./mvnw test` runs 325 JUnit tests. `NormaliserTest` covers these edge cases through the public API. Every exception test checks the whole message, so a case can't pass for the wrong reason.

| Case | Inputs | Expected |
|---|---|---|
| Default canonicals | `Java engineer`, `C# engineer`, `Accountant`, `Chief Accountant` | `Software engineer`, `Software engineer`, `Accountant`, `Accountant` |
| Mixed case | `ACCOUNTANT`, `aCcOuNtAnT`, a canonical in capitals, a Turkish default locale | Matched; the canonical keeps its own case |
| Spaces | Around and between words, and padding up to the 200-character limit | Ignored; the canonical comes back without surrounding spaces |
| Other whitespace | Tab, line break, non-breaking, em and zero-width spaces, byte order mark | Rejected |
| Blank | Empty, up to 200 spaces, Unicode spaces only | Rejected |
| Ties | `Quantity engineer`: 0.5 against both `Software engineer` and `Quantity surveyor` | The canonical listed first |
| Duplicate canonicals | `Architect` and `ARCHITECT` in one list | Merged, keeping the first |
| Close matches | A typo (`Acountant`), word order (`Surveyor quantity`), a repeated word (`Senior Senior Architect`), punctuation (`software-engineer`, `Accountant's`), apostrophes around a word (`'Chef'`), symbols alone (`Architect ++`), a word exactly at the 0.8 word threshold | Matched |
| No match | `Plumber`; no words (`R&D` as a term or a canonical, `C ++`); closest word below 0.8 (`Engineering`); swapped letters (`Enigneer`) | `null` |
| Score threshold | A score equal to the threshold; thresholds at the edges of [0.0, 1.0), including `-0.0`, and outside it (`-0.1`, `1.0`, `NaN`, infinities) | Only a higher score matches; thresholds outside the range rejected |
| Null | Null term; null canonicals in every constructor; a null first, middle or last in the list; null algorithm | `NullPointerException` |
| Invalid characters | `!`, `@`, `~`, accented letters, emoji, a Cyrillic look-alike, quotes, backslash, control characters, no letters (`---`) | Rejected, with the term escaped in the message |
| Limits | Terms and canonicals of 200 and 201 characters, terms of over a million; 0, 1000 and 1001 canonicals | Accepted within the limits, rejected outside them |
| Custom algorithm | Word scores of `-0.1`, `1.1`, `NaN` and infinity | `IllegalStateException` |
| Sharing | The caller's list changed after construction; 8 threads sharing one `Normaliser` | No effect; the same answers |

The package-private classes have their own tests: `MatcherTest` checks exact scores, best-word pairing and the word threshold; `LevenshteinSimilarityTest` checks known edit distances and properties such as symmetry and the triangle inequality on random strings; `TermRuleTest` checks the rule order and message escaping.

## Limitations and possible extensions

- Matching is lexical. "Bookkeeper" will not match "Accountant" and "Developer" will not match "Software engineer". A curated alias table, checked in `Normaliser` before fuzzy matching (as Apple's `NLGazetteer` takes precedence over its tagger), or an embedding-based scorer would handle these. A scorer like that compares whole terms, so it would need a term-level extension point; `WordSimilarityAlgorithm` only compares single words.
- Generic words count as much as specific ones. "Quantity engineer" scores 0.50 against both "Software engineer" and "Quantity surveyor", so the tie-break decides. Weighting words by how distinctive they are (e.g. TF-IDF) would help.
- A canonical or term made only of single characters and symbols, such as "R&D" or "C ++", has no words left after tokenising, so it scores 0 against everything and never matches.
- Levenshtein distance counts two swapped letters as two edits, so "Enigneer" scores 0.75 against "engineer", below the word threshold, and does not match. Damerau-Levenshtein distance, which counts a swap as one edit, would score it 0.875.
- Only the best canonical is returned. Returning the top *k* matches with their scores, as fastText's `predict` does, would let a caller show near-ties as "did you mean…?" suggestions.
- Each call scores every canonical, which is fine for up to 1000. For larger lists, `Matcher` could first narrow the candidates with an index of canonicals by word or n-gram, as OpenRefine's nearest-neighbour clustering does.
- Nothing is cached, so normalising the same term again repeats the whole calculation. If heavy use with repeated terms is expected, an LRU cache keyed by comparison form would avoid that, as the JDK's `LinkedHashMap` supports with access order and `removeEldestEntry`. `Normaliser` is immutable and so thread-safe; the cache would need synchronising to keep it that way.
- Validation failures are reported by message only. A caller that needs to branch on the reason, such as a web form, would need an exception exposing the failed `TermRule`, as libphonenumber's `NumberParseException` exposes its `ErrorType`.
