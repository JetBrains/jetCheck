package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

class PropertyFailureImpl<T> implements PropertyFailure<T> {
  private final CounterExampleImpl<T> initial;
  private CounterExampleImpl<T> shrunk;
  private int totalSteps;
  private int successfulSteps;
  final Iteration<T> iteration;
  private Throwable stoppingReason;
  final boolean reproducible;

  PropertyFailureImpl(@NotNull CounterExampleImpl<T> initial, Iteration<T> iteration) {
    this.initial = initial;
    this.shrunk = initial;
    this.iteration = iteration;
    this.reproducible = iteration.session.parameters.serializedData != null || initial.tryReproducing();
    try {
      if (reproducible) {
        shrink();
      }
    }
    catch (Throwable e) {
      stoppingReason = e;
    }
  }

  @NotNull
  @Override
  public CounterExampleImpl<T> getFirstCounterExample() {
    return initial;
  }

  @NotNull
  @Override
  public CounterExampleImpl<T> getMinimalCounterexample() {
    return shrunk;
  }

  @Nullable
  @Override
  public Throwable getStoppingReason() {
    return stoppingReason;
  }

  @Override
  public int getTotalShrinkingExampleCount() {
    return totalSteps;
  }

  @Override
  public int getShrinkingStageCount() {
    return successfulSteps;
  }

  @Override
  public int getIterationNumber() {
    return iteration.iterationNumber;
  }

  @Override
  public long getIterationSeed() {
    return iteration.iterationSeed;
  }

  @Override
  public long getGlobalSeed() {
    return iteration.session.parameters.globalSeed;
  }

  @Override
  public int getSizeHint() {
    return iteration.sizeHint;
  }

  private void shrink() {
    ShrinkStep lastSuccessfulShrink = null;
    do {
      lastSuccessfulShrink = shrinkIteration(lastSuccessfulShrink);
    }
    while (lastSuccessfulShrink != null);
  }

  private ShrinkStep shrinkIteration(ShrinkStep limit) {
    ShrinkStep lastSuccessfulShrink = null;
    ShrinkStep step = shrunk.data.shrink();
    while (step != null) {
      step = findSuccessfulShrink(step, limit);
      if (step != null) {
        lastSuccessfulShrink = step;
        step = step.onSuccess(shrunk.data);
      }
    }
    return lastSuccessfulShrink;
  }

  @Nullable
  private ShrinkStep findSuccessfulShrink(ShrinkStep step, @Nullable ShrinkStep limit) {
    List<CustomizedNode> combinatorial = new ArrayList<>();

    while (step != null && !step.equals(limit)) {
      StructureNode node = step.apply(shrunk.data);
      if (node != null && iteration.session.addGeneratedNode(node)) {
        CombinatorialIntCustomizer customizer = new CombinatorialIntCustomizer();
        if (tryStep(node, customizer)) {
          return step;
        }
        CombinatorialIntCustomizer next = customizer.nextAttempt();
        if (next != null) {
          combinatorial.add(new CustomizedNode(next, step));
        }
      }

      step = step.onFailure();
    }
    return processDelayedCombinations(combinatorial);
  }

  @Nullable
  private ShrinkStep processDelayedCombinations(List<CustomizedNode> delayed) {
    Collections.sort(delayed);

    for (CustomizedNode customizedNode : delayed) {
      CombinatorialIntCustomizer customizer = customizedNode.customizer;
      while (customizer != null) {
        if (tryStep(customizedNode.step.apply(shrunk.data), customizer)) {
          return customizedNode.step;
        }
        customizer = customizer.nextAttempt();
      }
    }
    return null;
  }

  private boolean tryStep(StructureNode node, CombinatorialIntCustomizer customizer) {
    try {
      iteration.session.notifier.shrinkAttempt(this, iteration, node);
      totalSteps++;

      HashSet<NodeId> unneeded = new HashSet<>();
      T value;
      try {
        value = iteration.generateValue(new ReplayDataStructure(node, iteration.sizeHint, customizer, unneeded));
      } catch (Throwable e) {
        iteration.session.notifier.replayFailed(e);
        throw e;
      }

      CounterExampleImpl<T> example = CounterExampleImpl.checkProperty(iteration, value, customizer.writeChanges(node.removeUnneeded(unneeded)));
      if (example != null) {
        shrunk = example;
        successfulSteps++;
        return true;
      }
    }
    catch (CannotRestoreValue ignored) {
    }
    return false;
  }

  private static class CustomizedNode implements Comparable<CustomizedNode> {
    final CombinatorialIntCustomizer customizer;
    final ShrinkStep step;

    CustomizedNode(CombinatorialIntCustomizer customizer, ShrinkStep step) {
      this.customizer = customizer;
      this.step = step;
    }

    @Override
    public int compareTo(@NotNull CustomizedNode o) {
      return Integer.compare(customizer.countVariants(), o.customizer.countVariants());
    }
  }
}
