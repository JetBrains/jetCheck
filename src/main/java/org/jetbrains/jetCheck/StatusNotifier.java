package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * @author peter
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
class StatusNotifier {
  private final PropertyChecker.Parameters parameters;
  private int currentIteration;
  private long lastPrinted = System.currentTimeMillis();

  StatusNotifier(PropertyChecker.Parameters parameters) {
    this.parameters = parameters;
  }

  void iterationStarted(int iteration) {
    currentIteration = iteration;
    if (shouldPrint()) {
      System.out.println(formatCurrentTime() + ": iteration " + currentIteration + " of " + parameters.getIterationCount() + "...");
    }
  }

  void counterExampleFound(Iteration<?> iteration) {
    if (parameters.silent) return;

    lastPrinted = System.currentTimeMillis();
    System.err.println(formatCurrentTime() + ": failed on iteration " + currentIteration + " (" + iteration.printSeeds() + "), shrinking...");
  }

  private boolean shouldPrint() {
    if (parameters.silent) return false;

    if (System.currentTimeMillis() - lastPrinted > 5_000) {
      lastPrinted = System.currentTimeMillis();
      return true;
    }
    return false;
  }

  private int lastReportedStage = -1;
  private String lastReportedTrace = null;

  <T> void shrinkAttempt(PropertyFailure<T> failure, Iteration<T> iteration, StructureNode data) {
    if (shouldPrint()) {
      int stage = failure.getShrinkingStageCount();
      System.out.println(formatCurrentTime() + ": still shrinking (" + iteration.printSeeds() + "). " +
                         "Examples tried: " + failure.getTotalShrinkingExampleCount() +
                         ", successful minimizations: " + stage);
      if (lastReportedStage != stage) {
        lastReportedStage = stage;

        System.err.println(" Current minimal example: " + failure.getMinimalCounterexample().getExampleValue());

        Throwable exceptionCause = failure.getMinimalCounterexample().getExceptionCause();
        if (exceptionCause != null) {
          String trace = shortenStackTrace(exceptionCause);
          if (!trace.equals(lastReportedTrace)) {
            lastReportedTrace = trace;
            System.err.println(" Reason: " + trace);
          }
        }
        System.err.println();
      }
    }
    if (parameters.printData) {
      System.out.println("Generating from shrunk raw data: " + data);
    }
  }

  void eofException() {
    if (parameters.silent) return;

    System.out.println("Generator tried to read past the end of serialized data, so it seems the failure isn't reproducible anymore");
  }

  void logEntryReceived(String entry) {
    if (parameters.printValues) {
      System.out.println(entry);
    }
  }

  <T> void beforePropertyCheck(T value) {
    if (parameters.printValues && !isAlreadyPrinted(value)) {
      System.out.println("Checking " + value);
    }
  }

  private boolean isAlreadyPrinted(Object value) {
    return value instanceof Scenario && !((Scenario) value).hasEmptyLog();
  }

  void propertyCheckFailed(@Nullable Throwable exception) {
    if (parameters.printValues) {
      System.out.println("  failure" + (exception == null ? "" : ": " + exception.getClass().getName()));
    }
  }

  private static String shortenStackTrace(Throwable e) {
    String trace = printStackTrace(e);
    return trace.length() > 1000 ? trace.substring(0, 1000) + "..." : trace;
  }

  @NotNull
  private static String formatCurrentTime() {
    return LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()));
  }

  static String printStackTrace(Throwable e) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    e.printStackTrace(writer);
    return stringWriter.getBuffer().toString();
  }

  void beforeReproducing(StructureNode data) {
    if (parameters.printData) {
      System.out.println("Reproducing from raw data " + data);
    }
  }

  void replayFailed(@NotNull Throwable e) {
    if (parameters.printData) {
      System.out.println("  failed: " + e.getClass().getName());
    }
  }
}
