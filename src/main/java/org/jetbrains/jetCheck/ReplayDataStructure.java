package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

class ReplayDataStructure extends AbstractDataStructure {
  private final Iterator<StructureElement> iterator;
  private final IntCustomizer customizer;
  private final Set<NodeId> unneeded;

  ReplayDataStructure(StructureNode node, int sizeHint, IntCustomizer customizer, Set<NodeId> unneeded) {
    super(node, sizeHint);
    this.iterator = node.childrenIterator();
    this.customizer = customizer;
    this.unneeded = unneeded;
  }

  @Override
  int drawInt(@NotNull IntDistribution distribution) {
    return customizer.suggestInt(nextChild(IntData.class), distribution);
  }

  @NotNull
  private <E extends StructureElement> E nextChild(Class<E> required) {
    if (!iterator.hasNext()) throw new CannotRestoreValue();
    Object next = iterator.next();
    if (!required.isInstance(next)) throw new CannotRestoreValue();
    //noinspection unchecked
    return (E)next;
  }

  @Override
  public <T> T generate(@NotNull Generator<T> generator) {
    return generate(generator, childSizeHint());
  }

  private <T> T generate(@NotNull Generator<T> generator, int childSizeHint) {
    ReplayDataStructure child = new ReplayDataStructure(nextChild(StructureNode.class), childSizeHint, customizer, unneeded);
    T value = generator.getGeneratorFunction().apply(child);
    if (child.iterator.hasNext()) {
      unneeded.add(child.iterator.next().id);
    }
    return value;
  }

  @Override
  <T> T generateNonShrinkable(@NotNull Generator<T> generator) {
    return generate(generator, sizeHint);
  }

  @Override
  <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<? super T> condition) {
    T value = generate(generator);
    if (!condition.test(value)) throw new CannotRestoreValue();
    return value;
  }

  @Override
  void changeKind(StructureKind kind) {
    if (node.kind != kind) {
      throw new CannotRestoreValue();
    }
  }

  @Override
  public String toString() {
    return node.toString();
  }
}