package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** A generator created by {@link Generator#recursive(Function)}. */
public final class RecursiveGenerator<T> extends Generator<T> {
  private final Generator<T> myDelegate;
  private final ThreadLocal<AtomicInteger> myBoundedRecursion = new ThreadLocal<>();
  private final @NotNull Function<? super Generator<T>, ? extends Generator<T>> myCreateGenerator;
  private final @Nullable Generator<? extends T> myBase;

  RecursiveGenerator(@NotNull Function<? super Generator<T>, ? extends Generator<T>> createGenerator, @Nullable Generator<? extends T> base) {
    super(new RecursiveGeneratorFunction<>());
    this.myCreateGenerator = createGenerator;
    myBase = base;
    myDelegate = createGenerator.apply(this);
    ((RecursiveGeneratorFunction<T>) getGeneratorFunction()).myGenerator = this;
  }

  /**
   * Sets a base generator to be used when the recursion depth exceeds the maximum allowed.
   * @return a modified copy of this recursive generator
   */
  @NotNull
  public Generator<T> withBase(@NotNull Generator<? extends T> base) {
    Objects.requireNonNull(base, "base");
    return new RecursiveGenerator<>(myCreateGenerator, base);
  }

  private T generateRecursive(@NotNull GenerationEnvironment data) {
    if (myBase == null) {
      return myDelegate.getGeneratorFunction().apply(data);
    }

    AtomicInteger bounded = myBoundedRecursion.get();
    boolean first = bounded == null;
    if (first) {
      myBoundedRecursion.set(bounded = new AtomicInteger(maxRecursiveDepth(data.getSizeHint())));
    }
    if (bounded.get() <= 0) {
      return myBase.getGeneratorFunction().apply(data);
    }

    bounded.decrementAndGet();
    try {
      return myDelegate.getGeneratorFunction().apply(data);
    } finally {
      bounded.incrementAndGet();
      if (first) myBoundedRecursion.remove();
    }
  }

  // a fancy way to compute the binary logarithm of the size hint
  private static int maxRecursiveDepth(int sizeHint) {
    return sizeHint <= 1 ? 0 : 31 - Integer.numberOfLeadingZeros(sizeHint);
  }

  private static final class RecursiveGeneratorFunction<T> implements Function<GenerationEnvironment, T> {
    RecursiveGenerator<T> myGenerator;

    @Override
    public T apply(GenerationEnvironment data) {
      return myGenerator.generateRecursive(data);
    }
  }
}
