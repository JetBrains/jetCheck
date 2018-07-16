package org.jetbrains.jetCheck;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class PropertyFalsified extends RuntimeException {
  static final String FAILURE_REASON_HAS_CHANGED_DURING_SHRINKING = "Failure reason has changed during shrinking, see initial failing example below";
  static final String NOT_REPRODUCIBLE = "The failure is not reproducible on re-run!!! Possible cause: side effects in the test.";
  private static final String SEPARATOR = "\n==========================\n";
  private final PropertyFailureImpl<?> failure;
  private final String message;

  PropertyFalsified(PropertyFailureImpl<?> failure) {
    super(failure.getMinimalCounterexample().getExceptionCause());
    this.failure = failure;
    this.message = calcMessage();
  }

  @Override
  public String getMessage() {
    return message;
  }

  private String calcMessage() {
    StringBuilder traceBuilder = new StringBuilder();

    String exampleString = valueToString(failure.getMinimalCounterexample(), traceBuilder);

    Throwable failureReason = failure.getMinimalCounterexample().getExceptionCause();
    Throwable rootCause = failureReason == null ? null : getRootCause(failureReason);
    String msg = rootCause != null && !rootCause.toString().contains("ComparisonFailure") // otherwise IDEA replaces the whole message (including example and rechecking information) with a diff
                 ? "Failed with " + rootCause + "\nOn " + exampleString 
                 : "Falsified on " + exampleString;

    if (!failure.reproducible) {
      msg += "\n\n" + NOT_REPRODUCIBLE;
    }

    msg += "\n" +
           getShrinkingStats() +
           "\n" + failure.iteration.printToReproduce(failureReason, failure.getMinimalCounterexample()) + "\n";

    if (failureReason != null) {
      appendTrace(traceBuilder, 
                  rootCause == failureReason ? "Property failure reason: " : "Property failure reason, innermost exception (see full trace below): ", 
                  rootCause);
    }

    if (failure.getStoppingReason() != null) {
      msg += "\n Shrinking stopped prematurely, see the reason below.";
      appendTrace(traceBuilder, "An unexpected exception happened during shrinking: ", failure.getStoppingReason());
    }
    
    Throwable first = failure.getFirstCounterExample().getExceptionCause();
    if (exceptionsDiffer(first, failure.getMinimalCounterexample().getExceptionCause())) {
      msg += "\n " + FAILURE_REASON_HAS_CHANGED_DURING_SHRINKING;
      StringBuilder secondaryTrace = new StringBuilder();
      traceBuilder.append("\n Initial value: ").append(valueToString(failure.getFirstCounterExample(), secondaryTrace));
      if (first == null) {
        traceBuilder.append("\n Initially property was falsified without exceptions");
        traceBuilder.append(secondaryTrace);
      } else {
        traceBuilder.append(secondaryTrace);
        appendTrace(traceBuilder, "Initially failed because of ", first);
      }
    }
    return msg + traceBuilder;
  }

  private static Throwable getRootCause(Throwable t) {
    while (t.getCause() != null) {
      t = t.getCause();
    }
    return t;
  }

  private static void appendTrace(StringBuilder traceBuilder, String prefix, Throwable e) {
    traceBuilder.append("\n ").append(prefix).append(StatusNotifier.printStackTrace(e)).append(SEPARATOR);
  }

  private static String valueToString(CounterExampleImpl<?> example, StringBuilder traceBuilder) {
    try {
      return String.valueOf(example.getExampleValue());
    }
    catch (Throwable e) {
      appendTrace(traceBuilder, "Exception during toString evaluation: ", e);
      return "<can't evaluate toString(), see exception below>";
    }
  }
  
  private String getShrinkingStats() {
    int exampleCount = failure.getTotalShrinkingExampleCount();
    if (exampleCount == 0) return "";
    String examples = exampleCount == 1 ? "example" : "examples";

    int stageCount = failure.getShrinkingStageCount();
    if (stageCount == 0) return "Couldn't shrink, tried " + exampleCount + " " + examples + "\n";

    String stages = stageCount == 1 ? "stage" : "stages";
    return "Shrunk in " + stageCount + " " + stages + ", by trying " + exampleCount + " " + examples + "\n";
  }

  private static boolean exceptionsDiffer(Throwable e1, Throwable e2) {
    if (e1 == null && e2 == null) return false;
    if ((e1 == null) != (e2 == null)) return true;
    if (!e1.getClass().equals(e2.getClass())) return true;
    if (e1 instanceof StackOverflowError) return false;

    return !getUserTrace(e1).equals(getUserTrace(e2));
  }

  private static List<String> getUserTrace(Throwable e) {
    List<String> result = new ArrayList<>();
    for (StackTraceElement element : e.getStackTrace()) {
      String s = element.toString();
      if (s.startsWith("org.jetbrains.jetCheck.") && !s.contains("Test.")) {
        break;
      }
      result.add(s);
    }
    return result;
  }

  public PropertyFailure<?> getFailure() {
    return failure;
  }

  @SuppressWarnings("WeakerAccess")
  public Object getBreakingValue() {
    return failure.getMinimalCounterexample().getExampleValue();
  }

}
