package org.jetbrains.jetCheck;

/**
 * Thrown when a generator recurses deeper than {@link PropertyChecker.Parameters#withMaxGenerationDepth the configured limit}
 * while producing a single value.<p></p>
 *
 * This almost always means a recursive generator (see {@link Generator#recursive}) has no effective base case:
 * the recursive alternatives are chosen so often that, on average, every generated node spawns more than one child,
 * so generation descends without bound and would otherwise overflow the JVM stack with a {@link StackOverflowError}.<p></p>
 *
 * To fix the generator, make sure recursion terminates: make the process "subcritical" (the recursive branches must,
 * on average, produce fewer than one recursive child), e.g. by lowering their {@link Generator#frequency} weights, or by
 * deriving collection sizes from {@link GenerationEnvironment#getSizeHint()} (which decreases with nesting) instead of a
 * fixed distribution.
 */
@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class GeneratorRecursedTooDeeply extends RuntimeException {
  private final int depth;

  GeneratorRecursedTooDeeply(int depth) {
    super("Generator recursed deeper than " + depth + " levels while generating a single value. " +
          "This usually means a recursive generator has no effective base case and would overflow the stack. " +
          "Consider lowering the weight of recursive alternatives or deriving sizes from " +
          "GenerationEnvironment.getSizeHint() so that recursion terminates. ");
    this.depth = depth;
  }

  /** @return the recursion depth limit that was exceeded */
  public int getDepth() {
    return depth;
  }
}
