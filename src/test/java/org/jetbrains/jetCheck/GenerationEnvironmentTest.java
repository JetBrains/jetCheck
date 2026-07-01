/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import junit.framework.TestCase;

import java.util.List;
import java.util.Random;

import static java.util.Arrays.asList;
import static org.jetbrains.jetCheck.Generator.*;

/**
 * Tests for {@link GenerationEnvironment#generative}, the entry point that drives a {@link Generator} from an
 * external {@link IntSource} outside {@link PropertyChecker}.
 */
public class GenerationEnvironmentTest extends TestCase {

  /** An {@link IntSource} that replays a fixed script and counts how many ints have been drawn. */
  private static final class ScriptedIntSource implements IntSource {
    private final int[] values;
    private int drawn;

    ScriptedIntSource(int... values) {
      this.values = values;
    }

    @Override
    public int drawInt(IntDistribution distribution) {
      if (drawn >= values.length) {
        throw new AssertionError("source exhausted after " + values.length + " draws");
      }
      return values[drawn++];
    }

    int drawn() {
      return drawn;
    }
  }

  /** A source that returns 0 for every draw, the counterpart of a fuzzer's all-zero byte seed. */
  private static IntSource zeros() {
    return distribution -> 0;
  }

  /** Adapts a {@link Random} to an {@link IntSource}, the way a caller wires an existing random stream in. */
  private static IntSource fromRandom(Random random) {
    return distribution -> distribution.generateInt(random);
  }

  public void testScriptedSourceDrivesGenerationAndDrawsOneIntPerCall() {
    // listsOf first draws the size (bounded by the size hint), then one int per element.
    ScriptedIntSource source = new ScriptedIntSource(3, 10, 20, 30);
    List<Integer> list = GenerationEnvironment.generative(source, 10).generate(listsOf(integers(0, 100)));

    assertEquals(asList(10, 20, 30), list);
    // Exactly one draw per int: the size draw plus three element draws, nothing more.
    assertEquals(4, source.drawn());
  }

  public void testDifferentIntStreamsProduceDifferentValues() {
    Generator<List<Integer>> gen = listsOf(integers(0, 100));
    List<Integer> one = GenerationEnvironment.generative(new ScriptedIntSource(1, 42), 10).generate(gen);
    List<Integer> two = GenerationEnvironment.generative(new ScriptedIntSource(1, 7), 10).generate(gen);

    assertEquals(asList(42), one);
    assertEquals(asList(7), two);
  }

  public void testSameIntSequenceProducesIdenticalValue() {
    Generator<List<Integer>> gen = listsOf(integers(0, 1000));
    int[] script = {4, 111, 222, 333, 444};
    List<Integer> first = GenerationEnvironment.generative(new ScriptedIntSource(script), 20).generate(gen);
    List<Integer> second = GenerationEnvironment.generative(new ScriptedIntSource(script), 20).generate(gen);

    assertEquals(first, second);
    assertEquals(asList(111, 222, 333, 444), first);
  }

  public void testRandomBackedSourceIsDeterministic() {
    // Two sources over identically seeded Randoms draw the same ints, so they yield the same value.
    Generator<List<Integer>> gen = listsOf(integers());
    List<Integer> first = GenerationEnvironment.generative(fromRandom(new Random(123456789L)), 20).generate(gen);
    List<Integer> second = GenerationEnvironment.generative(fromRandom(new Random(123456789L)), 20).generate(gen);

    assertEquals(first, second);
  }

  public void testSizeHintReachesTopLevelGenerator() {
    // The top-level generator observes exactly the requested size hint, as it does under PropertyChecker.
    Generator<Integer> hint = from(GenerationEnvironment::getSizeHint);
    assertEquals(7, (int)GenerationEnvironment.generative(zeros(), 7).generate(hint));
    assertEquals(0, (int)GenerationEnvironment.generative(zeros(), 0).generate(hint));
  }

  public void testSizeHintBiasesCollectionSizes() {
    Generator<List<Integer>> gen = listsOf(integers(0, 5));
    double small = averageSize(gen, 1, 300);
    double large = averageSize(gen, 40, 300);

    assertTrue("larger size hint should yield larger collections: small=" + small + ", large=" + large,
               large > small + 3.0);
  }

  private static double averageSize(Generator<List<Integer>> gen, int sizeHint, int samples) {
    IntSource source = fromRandom(new Random(20240517L + sizeHint));
    long total = 0;
    for (int i = 0; i < samples; i++) {
      total += GenerationEnvironment.generative(source, sizeHint).generate(gen).size();
    }
    return (double)total / samples;
  }

  public void testCannotSatisfyConditionPropagatesForNonEmptyLists() {
    // The all-zero source draws size 0 every time, so a non-empty list can never be satisfied.
    Generator<List<Integer>> gen = nonEmptyLists(integers());
    try {
      GenerationEnvironment.generative(zeros(), 16).generate(gen);
      fail("expected CannotSatisfyCondition");
    }
    catch (CannotSatisfyCondition expected) {
      assertNotNull(expected.getCondition());
    }
  }

  public void testCannotSatisfyConditionPropagatesForSuchThat() {
    Generator<Integer> nonZero = integers(0, 5).suchThat(i -> i != 0);
    try {
      GenerationEnvironment.generative(zeros(), 16).generate(nonZero);
      fail("expected CannotSatisfyCondition");
    }
    catch (CannotSatisfyCondition expected) {
      assertNotNull(expected.getCondition());
    }
  }

  public void testNegativeSizeHintRejected() {
    try {
      GenerationEnvironment.generative(zeros(), -1);
      fail("expected IllegalArgumentException");
    }
    catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("-1"));
    }
  }

  public void testNullSourceRejected() {
    try {
      GenerationEnvironment.generative(null, 16);
      fail("expected NullPointerException");
    }
    catch (NullPointerException expected) {
      // expected
    }
  }
}
