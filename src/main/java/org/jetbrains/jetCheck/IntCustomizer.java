// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author peter
 */

interface IntCustomizer {
  int suggestInt(IntData data, IntDistribution currentDistribution);

  static int checkValidInt(IntData data, IntDistribution currentDistribution) {
    int value = data.value;
    if (!currentDistribution.isValidValue(value)) throw new CannotRestoreValue();
    return value;
  }

}

class CombinatorialIntCustomizer implements IntCustomizer {
  private final DecisionNode root;
  private DecisionNode currentNode;

  CombinatorialIntCustomizer() {
    this(new DecisionNode(null, null));
  }

  private CombinatorialIntCustomizer(DecisionNode root) {
    this.root = currentNode = root;
  }

  public int suggestInt(IntData data, IntDistribution currentDistribution) {
    if (data.distribution instanceof BoundedIntDistribution && currentDistribution instanceof BoundedIntDistribution) {
      Integer value = suggestCombinatorialVariant(data,
              (BoundedIntDistribution)currentDistribution,
              (BoundedIntDistribution)data.distribution);
      if (value != null) {
        return value;
      }
    }
    return IntCustomizer.checkValidInt(data, currentDistribution);
  }

  private Integer suggestCombinatorialVariant(IntData data, BoundedIntDistribution current, BoundedIntDistribution original) {
    if (original.getMax() != current.getMax() || original.getMin() != current.getMin()) {
      LinkedHashSet<Integer> possibleValues = getPossibleValues(data, current, original);
      if (!possibleValues.isEmpty()) {
        if (possibleValues.size() == 1) return possibleValues.iterator().next();

        if (currentNode.branches == null) {
          currentNode.branches = new LinkedHashMap<>();
          possibleValues.forEach(i -> currentNode.branches.put(i, new DecisionNode(currentNode, new IntData(data.id, i, current))));
        }
        Integer next = currentNode.branches.keySet().iterator().next();
        currentNode = currentNode.branches.get(next);
        assert currentNode != null;
        return next;
      }
    }
    return null;
  }

  private LinkedHashSet<Integer> getPossibleValues(IntData data, BoundedIntDistribution current, BoundedIntDistribution original) {
    List<Integer> possibleValues = new ArrayList<>();
    int fromStart = data.value - original.getMin();
    int fromEnd = original.getMax() - data.value;

    int sameDistanceFromStart = current.getMin() + fromStart;
    int sameDistanceFromEnd = current.getMax() - fromEnd;

    if (!tooManyCombinations()) {
      if (fromStart < fromEnd) {
        possibleValues.add(sameDistanceFromStart);
        possibleValues.add(sameDistanceFromEnd);
      } else {
        possibleValues.add(sameDistanceFromEnd);
        possibleValues.add(sameDistanceFromStart);
      }
    }
    possibleValues.add(data.value);

    return possibleValues.stream()
            .map(value -> Math.min(Math.max(value, current.getMin()), current.getMax()))
            .filter(current::isValidValue)
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private boolean tooManyCombinations() {
    return currentNode.depth > 3;
  }

  @Nullable
  CombinatorialIntCustomizer nextAttempt() {
    currentNode.delete();
    return root.branches == null || root.branches.isEmpty() ? null : new CombinatorialIntCustomizer(root);
  }

  StructureNode writeChanges(StructureNode node) {
    StructureNode result = node;
    DecisionNode decision = currentNode;
    while (decision != null) {
      IntData choice = decision.lastChoice;
      if (choice != null) {
        result = result.replace(choice.id, choice);
      }
      decision = decision.parent;
    }
    return result;
  }

  int countVariants() {
    int result = 1;
    DecisionNode decision = currentNode;
    while (decision != null) {
      result *= Math.max(1, decision.branches == null ? 1 : decision.branches.size());
      decision = decision.parent;
    }
    return result;
  }

  private static class DecisionNode {
    @Nullable final DecisionNode parent;
    final int depth;
    final IntData lastChoice;
    Map<Integer, DecisionNode> branches;

    DecisionNode(@Nullable DecisionNode parent, IntData choice) {
      this.parent = parent;
      this.lastChoice = choice;
      depth = parent == null ? 1 : parent.depth + 1;
    }

    void delete() {
      if (parent == null) return;
      parent.branches.values().remove(this);
      if (parent.branches.isEmpty()) {
        parent.delete();
      }
    }
  }
}