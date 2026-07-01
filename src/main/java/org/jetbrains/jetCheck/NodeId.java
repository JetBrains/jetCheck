// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
class NodeId {
  private final AtomicInteger counter;
  final int number;
  @Nullable final Integer generatorHash;

  NodeId(@NotNull Generator<?> generator) {
    this(new AtomicInteger(), generator);
  }

  /** A root id not tied to any generator, for a data structure built outside {@link PropertyChecker}. */
  NodeId() {
    this(new AtomicInteger(), null);
  }

  private NodeId(AtomicInteger counter, @Nullable Generator<?> generator) {
    this.counter = counter;
    this.generatorHash = generator == null ? null : generator.getGeneratorFunction().hashCode();
    number = counter.getAndIncrement();
  }

  NodeId childId(@Nullable Generator<?> generator) {
    return new NodeId(counter, generator);
  }

  @Override
  public String toString() {
    return String.valueOf(number);
  }
}
