/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.jetbrains.jetCheck.Generator.*;

/**
 * @author peter
 */
public class ShrinkTest extends PropertyCheckerTestCase {
  public void testShrinkingComplexString() {
    checkFalsified(listsOf(stringsOf(asciiPrintableChars())),
                   l -> {
                     String s = l.toString();
                     return !"abcdefghijklmnopqrstuvwxyz()[]#!".chars().allMatch(c -> s.indexOf((char)c) >= 0);
                   },
                   308);
  }

  public void testShrinkingNonEmptyList() {
    List<Integer> list = checkGeneratesExample(nonEmptyLists(integers(0, 100)),
                                               l -> l.contains(42),
                                               6);
    assertEquals(1, list.size());
  }

  public void testWhenEarlyObjectsCannotBeShrunkBeforeLater() {
    Generator<String> gen = listsOf(IntDistribution.uniform(0, 2), listsOf(IntDistribution.uniform(0, 2), sampledFrom('a', 'b'))).map(List::toString);
    Set<String> failing = new HashSet<>(Arrays.asList("[[a, b], [a, b]]", "[[a, b], [a]]", "[[a], [a]]", "[[a]]", "[]"));
    Predicate<String> property = s -> !failing.contains(s);
    checkFalsified(gen, property, 0); // prove that it sometimes fails
    for (int i = 0; i < 1000; i++) {
      try {
        PropertyChecker.customized().silent().forAll(gen, property);
      }
      catch (PropertyFalsified e) {
        assertEquals("[]", e.getBreakingValue());
      }
    }
  }

  public void testNotAllDataIsConsumedAfterShrinking() {
    AtomicBoolean failed = new AtomicBoolean();
    Generator<Integer> gen = from(data -> {
      int i = data.generate(from(data1 -> {
        int j = data1.generate(integers(0, 10));
        if (!failed.get()) {
          data1.generate(integers(5, 10));
        }
        return j;
      }));
      data.generate(integers(-10, -5));
      return i;
    });
    checkFalsified(gen, i -> {
      if (i == 4) {
        failed.set(true);
        return false;
      }
      return true;
    }, 3);
  }

  public void testNotAllDataIsConsumedAfterShrinking_2() {
    Generator<List<Integer>> gen = listsOf(from(data -> {
      int i = data.generate(integers(0, 100));
      if (i != 0) {
        data.generate(integers(-10, -5));
      }
      return i;
    }));
    assertEquals(Arrays.asList(0, 0, 0, 0, 1), checkGeneratesExample(gen, ints -> {
      int zeroIndex = ints.lastIndexOf(0);
      return ints.size() >= 5 && zeroIndex >= 0 && zeroIndex != ints.size() - 1;
    }, 21));
  }


  public void testNegativeIntsShrinkInDirectionOfZero() {
    for (int i = 0; i < 100; i++) {
      //noinspection deprecation
      PropertyFalsified fails = checkFails(PropertyChecker.customized().withSeed(i), integers(-1000, 10), j -> j >= 0);
      assertEquals(-1, fails.getBreakingValue());
    }
  }
}
