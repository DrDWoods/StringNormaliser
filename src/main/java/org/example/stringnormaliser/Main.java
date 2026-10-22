package org.example.stringnormaliser;

import java.util.List;

/**
 * Command-line demo. Normalises each argument, or four example job titles if none are given,
 * against the default canonicals.
 */
public final class Main {

  private static final List<String> EXAMPLES =
      List.of("Java engineer", "C# engineer", "Accountant", "Chief Accountant");

  private Main() {}

  public static void main(String[] args) {
    Normaliser normaliser = new Normaliser();
    List<String> inputs = args.length > 0 ? List.of(args) : EXAMPLES;

    for (String input : inputs) {
      try {
        String canonical = normaliser.normalise(input);
        String result = canonical == null ? "no match" : "\"" + canonical + "\"";
        System.out.printf("\"%s\" > %s%n", input, result);
      } catch (IllegalArgumentException exception) {
        System.out.printf("\"%s\" skipped: %s%n", input, exception.getMessage());
      }
    }
  }
}
