package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An entry point to property-based testing. The main usage pattern: {@code PropertyChecker.forAll(generator, property)}.
 */
@SuppressWarnings("WeakerAccess")
public class PropertyChecker {
  static final int DEFAULT_MAX_SIZE_HINT = 100;

  /**
   * Checks that the given property returns {@code true} and doesn't throw exceptions by running the generator and the property
   * on random data repeatedly for some number of times. To customize the settings, invoke {@link #customized()} first.
   */
  public static <T> void forAll(Generator<T> generator, @NotNull Predicate<T> property) {
    customized().forAll(generator, property);
  }

  /**
   * Performs a check that the scenarios generated by the given command are successful. Default {@link PropertyChecker} settings are used. To customize the settings, invoke {@link #customized()} first.
   * @param command a supplier for a top-level command. This supplier should not have any side effects. 
   */
  public static void checkScenarios(@NotNull Supplier<? extends ImperativeCommand> command) {
    customized().checkScenarios(command);
  }

  /**
   * @return a "parameters" object that where some checker settings can be changed 
   */
  public static Parameters customized() {
    return new Parameters();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static class Parameters {
    long globalSeed = new Random().nextLong();
    @Nullable IntSource serializedData;
    IntUnaryOperator sizeHintFun = iteration -> (iteration - 1) % DEFAULT_MAX_SIZE_HINT + 1;
    int iterationCount = 100;
    boolean silent;
    boolean printValues;
    boolean printData;

    private Parameters() {
    }

    /**
     * This function allows to start the test with a fixed random seed. It's useful to reproduce some previous test run and debug it.
     * @param seed A random seed to use for the first iteration.
     *             The following iterations will use other, pseudo-random seeds, but still derived from this one.
     * @return this
     * @deprecated To catch your attention. It's fine to call this method during test debugging, but it should not be committed to version control
     * and used in regression tests, because any changes in the test itself or the framework can render the passed argument obsolete.
     * For regression testing, it's recommended to code the failing scenario explicitly.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public Parameters withSeed(long seed) {
      if (serializedData != null) {
        System.err.println("withSeed ignored, because 'rechecking' is used");
        return this;
      }

      globalSeed = seed;
      return this;
    }

    /**
     * @param iterationCount the number of iterations to try. By default it's 100.
     * @return this
     */
    public Parameters withIterationCount(int iterationCount) {
      if (serializedData != null) {
        System.err.println("withIterationCount ignored, because 'rechecking' is used");
        return this;
      }
      this.iterationCount = iterationCount;
      return this;
    }

    /**
     * @param sizeHintFun a function determining how size hint should be distributed depending on the iteration number.
     *                    By default the size hint will be 1 in the first iteration, 2 in the second one, and so on until 100,
     *                    then again 1,...,100,1,...,100, etc.
     * @return this
     * @see DataStructure#getSizeHint()
     */
    public Parameters withSizeHint(@NotNull IntUnaryOperator sizeHintFun) {
      if (serializedData != null) {
        System.err.println("withSizeHint ignored, because 'rechecking' is used");
        return this;
      }

      this.sizeHintFun = sizeHintFun;
      return this;
    }

    /**
     * Suppresses all output from the testing infrastructure during property check and test minimization
     * @return this
     */
    public Parameters silent() {
      if (printValues) throw new IllegalStateException("'silent' is incompatible with 'printGeneratedValues'");
      if (printData) throw new IllegalStateException("'silent' is incompatible with 'printRawData'");
      this.silent = true;
      return this;
    }

    /**
     * Enables verbose mode, when for every execution of property check all the generated values are printed to the stdout.
     * If a check fails, this is also printed. Can be useful to get an impression of how good the generators are, and for debugging purposes.
     * @return this
     */
    public Parameters printGeneratedValues() {
      if (silent) throw new IllegalStateException("'printGeneratedValues' is incompatible with 'silent'");
      printValues = true;
      return this;
    }

    /**
     * During minimization, prints the raw underlying data used to feed generators.
     * Rarely needed, requires some understanding of the checker internals.
     * @return th
     */
    public Parameters printRawData() {
      if (silent) throw new IllegalStateException("'printRawData' is incompatible with 'silent'");
      printData = true;
      return this;
    }

    /**
     * Checks the property within a single iteration by using specified seed and size hint. Useful to debug the test after it's failed, if {@link #rechecking} isn't enough (e.g. due to unforeseen side effects).
     * @deprecated To catch your attention. It's fine to call this method during test debugging, but it should not be committed to version control
     * and used in regression tests, because any changes in the test itself or the framework can render the passed arguments obsolete.
     * For regression testing, it's recommended to code the failing scenario explicitly.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public Parameters recheckingIteration(long seed, int sizeHint) {
      return withSeed(seed).withSizeHint(whatever -> sizeHint).withIterationCount(1);
    }

    /**
     * Checks the property within a single iteration by using specified underlying data. Useful to debug the test after it's failed.
     * @param serializedData the data used for running generators in serialized form, as printed by {@link PropertyFailure} exception.
     * @deprecated To catch your attention. It's fine to call this method during test debugging, but it should not be committed to version control
     * and used in regression tests, because any changes in the test itself or the framework can render the passed argument obsolete.
     * For regression testing, it's recommended to code the failing scenario explicitly.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public Parameters rechecking(@NotNull String serializedData) {
      this.iterationCount = 1;
      DataSerializer.deserializeInto(serializedData, this);
      return this;
    }

    /**
     * Checks that the given property returns {@code true} and doesn't throw exceptions by running the given generator and the property
     * on random data repeatedly for some number of times (see {@link #withIterationCount(int)}).
     */
    public <T> void forAll(Generator<T> generator, Predicate<T> property) {
      Iteration<T> iteration = new CheckSession<>(serializedData == null ? generator : generator.noShrink(),
                                                  property, this).firstIteration();
      while (iteration != null) {
        iteration = iteration.performIteration();
      }
    }

    /**
     * Performs a check that the scenarios generated by the given command are successful. Default {@link PropertyChecker} settings are used.
     * @param command a supplier for a top-level command. This supplier should not have any side effects. 
     */
    public void checkScenarios(@NotNull Supplier<? extends ImperativeCommand> command) {
      forAll(Scenario.scenarios(command), Scenario::ensureSuccessful);
    }

  }

}

