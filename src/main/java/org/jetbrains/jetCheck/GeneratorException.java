package org.jetbrains.jetCheck;

/**
 * @author peter
 */
class GeneratorException extends RuntimeException {

  GeneratorException(Iteration<?> iteration, Throwable cause) {
    super("Exception while generating data, " + iteration.printSeeds(), cause);
  }
}
