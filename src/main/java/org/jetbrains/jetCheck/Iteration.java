package org.jetbrains.jetCheck;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

class Iteration<T> {

  private static final Predicate<Object> DATA_IS_DIFFERENT = new Predicate<Object>() {
    @Override
    public boolean test(Object o) {
      return false;
    }

    @Override
    public String toString() {
      return ": cannot generate enough sufficiently different values";
    }
  };

  final CheckSession<T> session;
  long iterationSeed;
  final int sizeHint;
  final int iterationNumber;
  private Random random;

  Iteration(CheckSession<T> session, long iterationSeed, int iterationNumber) {
    this.session = session;
    this.sizeHint = session.parameters.sizeHintFun.applyAsInt(iterationNumber);
    this.iterationNumber = iterationNumber;
    if (sizeHint < 0) {
      throw new IllegalArgumentException("Size hint should be non-negative, found " + sizeHint);
    }
    initSeed(iterationSeed);
  }

  private void initSeed(long seed) {
    iterationSeed = seed;
    random = new Random(seed);
  }

  @Nullable
  private CounterExampleImpl<T> findCounterExample() {
    for (int i = 0; i < 100; i++) {
      if (i > 0) {
        initSeed(random.nextLong());
      }

      ScheduledFuture<?> printSeeds = session.executor.schedule(
              () -> System.out.println("An iteration is running for too long, " + printSeeds()),
              1, TimeUnit.MINUTES);
      try {
        StructureNode node = new StructureNode(new NodeId(session.generator));
        T value;
        try {
          IntSource source = session.parameters.serializedData != null ? session.parameters.serializedData : d -> d.generateInt(random);
          value = session.generator.getGeneratorFunction().apply(new GenerativeDataStructure(source, node, sizeHint));
        }
        catch (CannotSatisfyCondition e) {
          continue;
        }
        catch (DataSerializer.EOFException e) {
          session.notifier.eofException();
          return null;
        }
        catch (WrongDataStructure e) {
          throw e;
        }
        catch (Throwable e) {
          //noinspection InstanceofCatchParameter
          if (e instanceof CannotRestoreValue && session.parameters.serializedData != null) {
            throw e;
          }
          throw new GeneratorException(this, e);
        }
        if (!session.addGeneratedNode(node)) continue;

        return CounterExampleImpl.checkProperty(this, value, node);
      } finally {
        printSeeds.cancel(false);
      }
    }
    throw new GeneratorException(this, new CannotSatisfyCondition(DATA_IS_DIFFERENT));
  }

  String printToReproduce(@Nullable Throwable failureReason, CounterExampleImpl<?> minimalCounterExample) {
    String data = minimalCounterExample.getSerializedData();
    boolean scenarios =
      failureReason != null && StatusNotifier.printStackTrace(failureReason).contains("PropertyChecker$Parameters.checkScenarios");
    String rechecking = "PropertyChecker.customized().rechecking(\"" + data + "\")\n    ." + 
                        (scenarios ? "checkScenarios" : "forAll") + "(...)\n";
    return "To re-run the minimal failing case, run\n  " + rechecking +
            "To re-run the test with all intermediate shrinking steps, " +
            "use " + suggestRecheckingIteration() + " instead for last iteration, " +
            "or " + suggestWithSeed() + " for all iterations";
  }

  private String suggestWithSeed() {
    return "`withSeed(" + session.parameters.globalSeed + "L)`";
  }

  private String suggestRecheckingIteration() {
    return "`recheckingIteration(" + iterationSeed + "L, " + sizeHint + ")`";
  }

  String printSeeds() {
    return "use " + suggestRecheckingIteration() + " or " + suggestWithSeed() + " to reproduce";
  }

  @Nullable
  Iteration<T> performIteration() {
    session.notifier.iterationStarted(iterationNumber);

    CounterExampleImpl<T> example = findCounterExample();
    if (example != null) {
      session.notifier.counterExampleFound(this);
      throw new PropertyFalsified(new PropertyFailureImpl<>(example, this));
    }

    if (iterationNumber >= session.parameters.getIterationCount()) {
      return null;
    }
    
    return new Iteration<>(session, random.nextLong(), iterationNumber + 1);
  }

  T generateValue(ReplayDataStructure data) {
    return session.generator.getGeneratorFunction().apply(data);
  }
}

class CheckSession<T> {
  private static final String EXECUTOR_NAME = "jetCheck internal executor";
  final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(0,
          r -> new Thread(r, EXECUTOR_NAME));
  final Generator<T> generator;
  final Predicate<T> property;
  final PropertyChecker.Parameters parameters;
  final StatusNotifier notifier;
  private final Set<StructureNode> generatedNodes = Collections.newSetFromMap(new LinkedHashMap<StructureNode, Boolean>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<StructureNode, Boolean> eldest) {
      return size() > 1_000;
    }
  });

  CheckSession(Generator<T> generator, Predicate<T> property, PropertyChecker.Parameters parameters) {
    this.generator = generator;
    this.property = property;
    this.parameters = parameters;
    notifier = new StatusNotifier(parameters);
  }

  boolean addGeneratedNode(StructureNode node) {
    return generatedNodes.add(node);
  }

  void run() {
    try {
      Iteration<T> iteration = new Iteration<>(this, parameters.globalSeed, 1);
      while (iteration != null) {
        iteration = iteration.performIteration();
      }
    } finally {
      shutdownExecutor();
    }
  }

  private void shutdownExecutor() {
    executor.shutdownNow();
    try {
      if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
        throw new IllegalStateException("Cannot shutdown " + EXECUTOR_NAME);
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Cannot shutdown " + EXECUTOR_NAME, e);
    }
  }
}
