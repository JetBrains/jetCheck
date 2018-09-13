/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.jetbrains.jetCheck.Generator.*;

/**
 * @author peter
 */
public class ExceptionTest extends PropertyCheckerTestCase {

  public void testFailureReasonUnchanged() {
    PropertyFalsified e = checkFails(STABLE, integers(), i -> {
      throw new AssertionError("fail");
    });

    assertFalse(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_SHRINKING));
  }

  public void testFailureReasonChangedExceptionClass() {
    PropertyFalsified e = checkFails(STABLE, integers(), i -> {
      throw (i == 0 ? new RuntimeException("fail") : new IllegalArgumentException("fail"));
    });
    assertTrue(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_SHRINKING));
  }

  public void testFailureReasonChangedExceptionTrace() {
    PropertyFalsified e = checkFails(STABLE, integers(), i -> {
      if (i == 0) {
        throw new AssertionError("fail");
      }
      else {
        throw new AssertionError("fail2");
      }
    });
    assertTrue(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_SHRINKING));
  }

  public void testExceptionWhileGeneratingValue() {
    try {
      STABLE.forAll(from(data -> {
        throw new AssertionError("fail");
      }), i -> true);
      fail();
    }
    catch (GeneratorException ignore) {
    }
  }

  public void testExceptionWhileShrinkingValue() {
    PropertyFalsified e = checkFails(PropertyChecker.customized(), listsOf(integers()).suchThat(l -> {
      if (l.size() == 1 && l.get(0) == 0) throw new RuntimeException("my exception");
      return true;
    }), l -> l.stream().allMatch(i -> i > 0));

    assertEquals("my exception", Objects.requireNonNull(e.getFailure().getStoppingReason()).getMessage());
    assertTrue(StatusNotifier.printStackTrace(e).contains("my exception"));
  }

  public void testUnsatisfiableSuchThat() {
    try {
      PropertyChecker.forAll(integers(-1, 1).suchThat(i -> i > 2), i -> i == 0);
      fail();
    }
    catch (GeneratorException e) {
      assertTrue(e.getCause() instanceof CannotSatisfyCondition);
    }
  }

  public void testUsingWrongDataStructureForGeneration() {
    Generator<Integer> gen = from(data1 -> {
      int i1 = data1.generate(naturals());
      int i2 = data1.generate(from(data2 -> data1.generate(integers())));
      return i1 + i2;
    });
    try {
      PropertyChecker.forAll(gen, i -> true);
      fail();
    }
    catch (WrongDataStructure expected) {
    }
  }

  public void testUsingWrongDataStructureForLogging() {
    try {
      PropertyChecker.checkScenarios(() -> env ->
              env.executeCommands(constant(env1 -> env.logMessage("Message"))));
      fail();
    }
    catch (WrongDataStructure expected) {
    }
  }

  public void testNonReproducibleFailure() {
    AtomicBoolean failed = new AtomicBoolean();
    AtomicInteger runCount = new AtomicInteger();

    Generator<Integer> gen = integers();
    Predicate<Integer> prop = i -> {
      runCount.incrementAndGet();
      return !failed.compareAndSet(false, true);
    };

    PropertyFalsified e = checkFails(STABLE, gen, prop);
    assertTrue(e.getMessage(), e.getMessage().contains(PropertyFalsified.NOT_REPRODUCIBLE));
    assertEquals(0, e.getFailure().getTotalShrinkingExampleCount());

    failed.set(false);
    runCount.set(0);
    //noinspection deprecation
    checkFails(PropertyChecker.customized().rechecking(e.getFailure().getMinimalCounterexample().getSerializedData()), gen, prop);
    assertEquals(1, runCount.get());
  }

  public void testNonReproducibleFailureByCannotRestore() {
    AtomicBoolean failed = new AtomicBoolean();
    try {
      PropertyChecker.customized().silent().checkScenarios(() -> env -> {
        if (failed.get()) {
          env.generateValue(integers(0, 10), "%s");
        }
        failed.set(true);
        throw new AssertionError("failed");
      });
      fail();
    } catch (PropertyFalsified e) {
      assertTrue(e.getMessage(), e.getMessage().contains(PropertyFalsified.NOT_REPRODUCIBLE));
      assertEquals(0, e.getFailure().getTotalShrinkingExampleCount());
    }
  }

  public void testNonReproducibleFailureBecauseOfGeneratorException() {
    AtomicBoolean failed = new AtomicBoolean();
    try {
      PropertyChecker.customized().silent().forAll(Generator.from(e -> {
        if (failed.get()) {
          throw new AssertionError("some failure");
        }
        return 0;
      }), i -> {
        failed.set(true);
        return false;
      });
      fail();
    } catch (PropertyFalsified e) {
      assertTrue(e.getMessage(), e.getMessage().contains(PropertyFalsified.NOT_REPRODUCIBLE));
      assertEquals(0, e.getFailure().getTotalShrinkingExampleCount());
    }
  }
}
