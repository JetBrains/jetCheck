package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

class CounterExampleImpl<T> implements PropertyFailure.CounterExample<T> {
  final StructureNode data;
  private final T value;
  @Nullable private final Throwable exception;
  private final Iteration<T> iteration;

  private CounterExampleImpl(StructureNode data, T value, @Nullable Throwable exception, Iteration<T> iteration) {
    this.data = data;
    this.value = value;
    this.exception = exception;
    this.iteration = iteration;
  }

  @Override
  public T getExampleValue() {
    return value;
  }

  @Nullable
  @Override
  public Throwable getExceptionCause() {
    return exception;
  }

  @NotNull
  @Override
  public CounterExampleImpl<T> replay() {
    T value = iteration.generateValue(createReplayData());
    CounterExampleImpl<T> example = checkProperty(iteration, value, data);
    return example != null ? example : 
           new CounterExampleImpl<>(data, value, new IllegalStateException("Replaying failure is unexpectedly successful!"), iteration);
  }

  boolean tryReproducing() {
    iteration.session.notifier.beforeReproducing(data);
    try {
      return checkProperty(iteration, iteration.generateValue(createReplayData()), data) != null;
    } catch (Throwable e) {
      iteration.session.notifier.replayFailed(e);
      return false;
    }
  }

  @NotNull
  @Override
  public String getSerializedData() {
    return DataSerializer.serialize(iteration, data);
  }

  ReplayDataStructure createReplayData() {
    return new ReplayDataStructure(data, iteration.sizeHint, IntCustomizer::checkValidInt, new HashSet<>());
  }

  static <T> CounterExampleImpl<T> checkProperty(Iteration<T> iteration, T value, StructureNode node) {
    try {
      iteration.session.notifier.beforePropertyCheck(value);
      if (!iteration.session.property.test(value)) {
        iteration.session.notifier.propertyCheckFailed(null);
        return new CounterExampleImpl<>(node, value, null, iteration);
      }
    }
    catch (Throwable e) {
      iteration.session.notifier.propertyCheckFailed(e);
      return new CounterExampleImpl<>(node, value, e, iteration);
    }
    return null;
  }

}