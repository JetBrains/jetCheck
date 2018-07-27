/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class Scenario {
  private static final String COMMANDS = "commands:";
  private final StringBuilder log = new StringBuilder();
  private Throwable failure;
  private final Consumer<String> logConsumer;

  private Scenario(@NotNull ImperativeCommand cmd, @NotNull GenerationEnvironment data, Consumer<String> logConsumer) {
    this.logConsumer = logConsumer;
    try {
      performCommand(cmd, data, "");
    }
    catch (DataSerializer.EOFException e) {
      throw e;
    }
    catch (Throwable e) {
      addFailure(e);
    }
    if (failure instanceof CannotRestoreValue || failure instanceof WrongDataStructure) {
      throw (RuntimeException) failure;
    }
  }

  private void addFailure(Throwable e) {
    if (failure == null) {
      failure = e;
    }
  }

  private void performCommand(ImperativeCommand command, GenerationEnvironment data, String indent) {
    command.performCommand(new ImperativeCommand.Environment() {
      @Override
      public void logMessage(@NotNull String message) {
        if (data instanceof GenerativeDataStructure) {
          ((GenerativeDataStructure) data).ensureActiveStructure();
        }

        if (hasEmptyLog()) {
          log.append(COMMANDS);
          logConsumer.accept(COMMANDS);
        }
        String logEntry = indent + message;
        log.append("\n").append(logEntry);
        logConsumer.accept(logEntry);
      }

      @Override
      public <T> T generateValue(@NotNull Generator<T> generator, @Nullable String logMessage) {
        T value = safeGenerate(data, generator);
        if (logMessage != null) {
          logMessage(String.format(logMessage, value));
        }
        return value;
      }

      @Override
      public void executeCommands(IntDistribution count, Generator<? extends ImperativeCommand> cmdGen) {
        innerCommandLists(Generator.listsOf(count, innerCommands(cmdGen)));
      }

      @Override
      public void executeCommands(Generator<? extends ImperativeCommand> cmdGen) {
        innerCommandLists(Generator.nonEmptyLists(innerCommands(cmdGen)));
      }

      private void innerCommandLists(final Generator<List<Object>> listGen) {
        data.generate(Generator.from(new EquivalentGenerator<List<Object>>() {
          @Override
          public List<Object> apply(GenerationEnvironment data) {
            return listGen.getGeneratorFunction().apply(data);
          }
        }));
      }

      @NotNull
      private Generator<Object> innerCommands(Generator<? extends ImperativeCommand> cmdGen) {
        return Generator.from(new EquivalentGenerator<Object>() {
          @Override
          public Object apply(GenerationEnvironment cmdData) {
            performCommand(safeGenerate(cmdData, cmdGen), cmdData, indent + "  ");
            return null;
          }
        });
      }
    });
  }

  private <T> T safeGenerate(GenerationEnvironment data, Generator<T> generator) {
    try {
      return data.generate(generator);
    }
    catch (CannotRestoreValue e) { //todo test for evil intermediate code hiding this exception, also CannotSatisfyCondition
      addFailure(e);
      throw e;
    }
  }


  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof Scenario && toString().equals(o.toString());
  }

  @Override
  public int hashCode() {
    return log.hashCode();
  }

  @Override
  public String toString() {
    return hasEmptyLog() ? COMMANDS + "<none>" : log.toString();
  }

  boolean hasEmptyLog() {
    return log.length() == 0;
  }

  boolean ensureSuccessful() {
    if (failure instanceof Error) throw (Error)failure;
    if (failure instanceof RuntimeException) throw (RuntimeException)failure;
    if (failure != null) throw new RuntimeException(failure);
    return true;
  }

  static Generator<Scenario> scenarios(@NotNull Supplier<? extends ImperativeCommand> command, Consumer<String> logConsumer) {
    return Generator.from(data -> new Scenario(command.get(), data, logConsumer));
  }

  private static abstract class EquivalentGenerator<T> implements Function<GenerationEnvironment, T> {
    @Override
    public boolean equals(Object obj) {
      return getClass() == obj.getClass(); // for recursive shrinking to work
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
    
  }

}
