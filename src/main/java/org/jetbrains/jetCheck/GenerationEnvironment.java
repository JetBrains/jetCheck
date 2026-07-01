package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A context for {@link Generator}s. Primitive generators (e.g. {@link Generator#integers}) know how to obtain
 * random data from it, other generators build more complex values on top of that, by running {@link #generate(Generator)} recursively.
 */
public interface GenerationEnvironment {

  /**
   * @return a non-negative number used by various generators to guide the sizes of structures (e.g. collections) they create.
   * The sizes need not be exactly equal to this hint, but on average, bigger hints should correspond to bigger structures.
   * When generators invoke other generators, the size hint of the structure used by called generators is
   * generally less than the original one's.
   */
  int getSizeHint();

  /** Runs the given generator on a data sub-structure of this structure and returns the result */
  <T> T generate(@NotNull Generator<T> generator);

  /**
   * Creates a fresh environment that draws every int from the given source, for producing a value outside
   * {@link PropertyChecker}. Pass the result to {@link #generate(Generator)} to obtain one value:
   * <pre>{@code
   * T value = GenerationEnvironment.generative(source, sizeHint).generate(generator);
   * }</pre>
   *
   * <p>This is an advanced integration point. Ordinary property tests use {@link PropertyChecker#forAll}, which
   * owns both the source of randomness and the generation loop. Use this factory only to drive a generator from
   * an external stream of ints — for example a coverage-guided fuzzer that supplies the bytes and wants each of
   * them to map onto a local change in the generated value. The environment performs generation only; it does not
   * shrink, replay, or re-run the property.
   *
   * <p>Generation draws from {@code source} on demand and reports failures through the same exceptions as a normal
   * run: an over-constrained generator (such as {@link Generator#suchThat} or {@link Generator#nonEmptyLists} over
   * a source that cannot satisfy the constraint) throws {@link CannotSatisfyCondition}, which the caller is
   * expected to handle.
   *
   * @param source   the source of the ints every primitive generator draws
   * @param sizeHint the value returned by {@link #getSizeHint()}, biasing generated collection and string sizes;
   *                 must be non-negative
   * @return a fresh environment ready for a single {@link #generate(Generator)} call
   * @see IntSource
   */
  static GenerationEnvironment generative(@NotNull IntSource source, int sizeHint) {
    Objects.requireNonNull(source, "source");
    if (sizeHint < 0) throw new IllegalArgumentException("sizeHint must be non-negative: " + sizeHint);
    GenerativeDataStructure root =
      new GenerativeDataStructure(source, new StructureNode(new NodeId()), sizeHint, PropertyChecker.DEFAULT_MAX_GENERATION_DEPTH);
    return new GenerationEnvironment() {
      @Override
      public int getSizeHint() {
        return root.getSizeHint();
      }

      @Override
      public <T> T generate(@NotNull Generator<T> generator) {
        // Run the generator on the root structure, the way a PropertyChecker iteration does, so the top-level
        // generator sees the full size hint. Calling root.generate(generator) instead would descend into a
        // sub-structure and hand the generator a hint reduced by one.
        return generator.getGeneratorFunction().apply(root);
      }
    };
  }

}
