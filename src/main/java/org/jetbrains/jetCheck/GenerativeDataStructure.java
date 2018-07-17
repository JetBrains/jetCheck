package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;


/**
 * @author peter
 */
class GenerativeDataStructure extends AbstractDataStructure {
  private final CurrentData dataTracker;
  private final IntSource random;

  GenerativeDataStructure(IntSource random, StructureNode node, int sizeHint) {
    this(null, random, node, sizeHint);
  }

  private GenerativeDataStructure(@Nullable CurrentData dataTracker, IntSource random, StructureNode node, int sizeHint) {
    super(node, sizeHint);
    this.random = random;
    this.dataTracker = dataTracker != null ? dataTracker : new CurrentData();
  }

  @Override
  int drawInt(@NotNull IntDistribution distribution) {
    ensureActiveStructure();
    int i = random.drawInt(distribution);
    node.addChild(new IntData(node.id.childId(null), i, distribution));
    return i;
  }

  void ensureActiveStructure() {
    dataTracker.checkContext(this);
  }

  @Override
  public <T> T generate(@NotNull Generator<T> generator) {
    return dataTracker.generateOn(generator, subStructure(generator, childSizeHint()), this);
  }

  @NotNull
  private GenerativeDataStructure subStructure(@NotNull Generator<?> generator, int childSizeHint) {
    return new GenerativeDataStructure(dataTracker, random, node.subStructure(generator), childSizeHint);
  }

  @Override
  <T> T generateNonShrinkable(@NotNull Generator<T> generator) {
    GenerativeDataStructure data = subStructure(generator, sizeHint);
    data.node.shrinkProhibited = true;
    return dataTracker.generateOn(generator, data, this);
  }

  @Override
  <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<? super T> condition) {
    for (int i = 0; i < 100; i++) {
      GenerativeDataStructure structure = subStructure(generator, childSizeHint());
      T value = dataTracker.generateOn(generator, structure, this);
      if (condition.test(value)) return value;

      if (random instanceof DataSerializer.SerializedIntSource) {
        throw DataSerializer.errorRestoringSerialized();
      }

      node.removeLastChild(structure.node);
    }
    throw new CannotSatisfyCondition(condition);
  }

  @Override
  void changeKind(StructureKind kind) {
    if (node.kind != StructureKind.GENERIC) {
      throw new IllegalStateException("Attempt to use incompatible generator on a same data structure which is already " + node.kind);
    }
    node.kind = kind;
  }

  private class CurrentData {
    GenerationEnvironment current = GenerativeDataStructure.this;

    <T> T generateOn(Generator<T> gen, GenerativeDataStructure data, GenerativeDataStructure parent) {
      checkContext(parent);
      current = data;
      try {
        return gen.getGeneratorFunction().apply(data);
      }
      finally {
        current = parent;
      }
    }

    void checkContext(GenerativeDataStructure data) {
      if (current != data) throw new WrongDataStructure();
    }
  }
}

class WrongDataStructure extends IllegalStateException {
  WrongDataStructure() {
    super("You're calling methods on wrong environment, confusing nested lambda arguments?");
  }
}