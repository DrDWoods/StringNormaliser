# StringNormaliser

A Java library that maps a **term** (free text such as "Java engineer") onto the best match from a fixed list of **canonicals** (such as "Software engineer"). Package `org.example.stringnormaliser`, JDK 21+ (`maven.compiler.release` 21), JUnit 6, no runtime dependencies. The GitHub repo is public.

## Commands

- `./mvnw verify` compiles, runs the tests, checks formatting (Spotless, Google Java Style) and builds the jar. On Windows: `.\mvnw.cmd verify`.
- `./mvnw spotless:apply` reformats. Run it after editing Java, or `verify` fails.
- `.mvn/jvm.config` exports the six `jdk.compiler` packages google-java-format needs, one `--add-exports` per line. Without them, Spotless opens the packages with `sun.misc.Unsafe`, which JDK 24+ warns about and a later JDK will remove. Keep them, and add no comments: Maven's launch scripts would pass them to the JVM.
- `pom.xml` overrides plexus-utils in the clean and resources plugins for CVE-2025-67030. After bumping either plugin, drop its override if the new release already has the fix.- The working tree uses CRLF (`core.autocrlf=true`) and `mvnw` is LF (`.gitattributes`). Preserve line endings when editing with scripts.

## Nomenclature

- **canonical**: an entry in the fixed list, and the value returned.
- **term**: the free-text input to `normalise`.
- **comparison form** (key): the stripped, lower-cased text from `TermCleaner`. It decides which canonicals are duplicates and is never returned.
- **word** (token): what `Tokeniser` produces. At least two characters from `[a-z0-9#+']`, with at least one letter or digit and no apostrophe at either end.
- **score** (q, in [0, 1]): a term against a canonical. **Word score**: two words, from a `WordSimilarityAlgorithm`.

## Design

| Type | Visibility | Role |
|---|---|---|
| `Normaliser` | public final | Entry point. Validates canonicals and terms, merges duplicate canonicals (first kept), applies the score threshold. `normalise` returns the canonical or `null`. |
| `WordSimilarityAlgorithm` | public interface | The only extension point: scores two words in [0, 1], called with each pair in both orders. |
| `Match` | public record | Canonical and score; validates both. Nothing public returns it (`match` is private), by the user's choice. |
| `Matcher` | package-private | Tokenises canonicals once, scores every canonical, strict `>` so the first canonical wins a tie, checks every word score. |
| `TermRule` | package-private enum | `MAX_LENGTH`, `NOT_BLANK`, `ALLOWED_CHARACTERS`, `HAS_LETTER`. Declaration order is check order. Builds the messages. |
| `TermCleaner`, `Tokeniser` | package-private | Comparison form, and splitting into words. Both lower-case with `Locale.ENGLISH`. |
| `LevenshteinSimilarity` | package-private | Default algorithm: `1 - distance / max length`. |
| `Main` | public | Demo. The user plans to delete it; then also remove the pom's `mainClass` block and the README's `java -jar` lines, `Main` row and "Run `Main`". |

Score = the sum of each word's best word score (counted only if at least `Matcher.WORD_SIMILARITY_THRESHOLD`, 0.8), in both directions, divided by the total number of words. A canonical matches only if its score is strictly above the threshold (default 0.0). The 0.8 is also written in `WordSimilarityAlgorithm`'s Javadoc and the README: keep them in step.

## Defensive programming

Strong defensive programming, as Joshua Bloch defines it in *Effective Java* (3rd edition), is a core requirement. Apply it to every change, and cite the item when explaining a choice.

- **Check parameters for validity (Item 49).** The package is the trust boundary. Every public constructor and method checks all its arguments on entry: `Objects.requireNonNull(x, "x must not be null")` for nulls, plus explicit range and content checks. Package-private code checks its callers' assumptions with `assert`, which Item 49 allows because the package controls every call. Surefire runs with `-ea`; never write tests expecting `AssertionError`.
- **Failure atomicity (Item 76).** Validate everything before assigning any field.
- **Make defensive copies (Item 50).** Copy collections before validating their elements (`new ArrayList<>(list)`, which allows nulls), so the caller can't change them after the check. `Collections.unmodifiable*` is a view, not a copy. `Map.copyOf` and `Set.copyOf` lose the order the tie-break needs.
- **Minimise accessibility and mutability (Items 15, 17, 19).** Classes are `final` and immutable, with `private final` fields. Only `Normaliser`, `Match`, `WordSimilarityAlgorithm` and the `Main` demo are public.
- **Use standard exceptions and document them (Items 72, 74).**
  - `NullPointerException` for a null.
  - `IllegalArgumentException` for a bad argument.
  - `IllegalStateException` when a client's word score is outside [0, 1] or `NaN`, because the caller's arguments were valid.
  - Document every exception with `@throws`.
- **Exceptions only for exceptional conditions (Item 69).** No match is an expected outcome, so `normalise` returns `null` rather than throwing.
- **Failure-capture messages (Item 75).** Messages give the offending value, quoted, with everything outside printable ASCII, and `"` and `\`, escaped as `\uXXXX`. This stops a message carrying a line break into a log. `MAX_LENGTH` gives the length, never the value.
- **Distrust client code.** Every score from a client's `WordSimilarityAlgorithm` is range-checked, because a bad one would silently pick the wrong canonical.
- Every exception test asserts the whole message, so a case can't pass for the wrong reason.

## Decisions (don't revisit unless asked)

- `new Normaliser()` (the four `DEFAULT_CANONICALS`) and `String normalise(String)` returning `null` on no match are required. `NormaliserTest.DefaultCanonicals.supportsTheSampleUsage` mirrors the required usage exactly, variable names included: keep it unchanged and passing.
- Printable ASCII only. No Unicode or accent support.
- Canonicals with no words (such as "R&D") are accepted but never match, as a test pins. Don't add a check rejecting them.
- The word threshold is fixed, not configurable. Duplicate canonicals are merged, not rejected.
- No Spring, no `Term` value type, no term-level scorer interface.
- No coverage or mutation tooling in the build. The README's edge-case table is the testing evidence.
- Keep the repo self-contained: never say where the project came from or who it is for, anywhere in the repo, including commit messages.

## Conventions

- `NormaliserTest` uses the public API only, with a `@Nested` class per kind of case. The package-private classes have their own test classes. Prefer parameterised tests (`@CsvSource`, `@MethodSource`, `Named`).
- When tests change, update the README's test count and edge-case table.
- Write non-ASCII test inputs as `\uXXXX` escapes, with a comment naming the character. When writing files through a tool, build the backslash with `chr(92)` in a script: a backslash, `u` and four hex digits typed into tool input are decoded into the raw character.
- British spelling (normalise, normaliser). Javadoc on the public API; comments say why.

## Working with the user

- For a review, report findings with `file:line` and wait. Implement only when asked.
- During restructuring, give structural feedback only: don't implement, and don't nag about tests or builds.
- Commit only when asked. The user commits ("wip") and pushes.
- Keep the README and other docs concise.
