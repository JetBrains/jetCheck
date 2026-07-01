// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jetCheck;

/**
 * The raw source of the ints that primitive generators draw. Every random decision a {@link Generator} makes
 * ultimately becomes a {@link #drawInt} call, so an {@code IntSource} is the single point through which all
 * randomness enters generation.
 *
 * <p>This is an advanced integration point. Most tests never touch it: {@link PropertyChecker} supplies its own
 * source and owns the generation loop. Implement it only to drive a {@link Generator} from an external stream of
 * ints — for example a coverage-guided fuzzer that mutates a byte stream and wants each mutation to map onto a
 * local change in the generated value. Pair it with {@link GenerationEnvironment#generative(IntSource, int)}.
 *
 * @see GenerationEnvironment#generative(IntSource, int)
 */
public interface IntSource {
  /**
   * Returns the next int, which must satisfy the given distribution (see {@link IntDistribution#isValidValue}).
   * A bounded distribution should consume only as much of the underlying source as it needs, so that byte-level
   * changes in the source stay local to the value they affect.
   *
   * @param distribution the distribution the returned value must belong to
   * @return the drawn int
   */
  int drawInt(IntDistribution distribution);
}
