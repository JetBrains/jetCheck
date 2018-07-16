# jetCheck
 
jetCheck is a property-based testing library for Java 8+, inspired by [QuickCheck](https://en.wikipedia.org/wiki/QuickCheck) and [Hypothesis](https://hypothesis.works/). Its distinguishing features are:
* Automatic example minimization: you don't need to manually write shrinkers for your custom data types 
* Re-running of minimized test example: once you've got a failing test, you can re-run (and debug) it without all the intermediate shrinking steps
* Stateful system testing (single-threaded): you can generate a sequence of commands, and each command generation may depend on the state that previous commands have brought the system into.

The library is by no means supposed to be feature-complete, and might lack even very basic data generators, if no clients have needed them yet. Improvement suggestions are welcome. 

# What's property-based testing

"Property-based testing" means that the library checks very general properties that a program should have, as opposed to specific scenarios in unit testing. In *jetCheck*, a number of random test cases are generated, the property is verified on those test cases, and, if it fails, the test case is automatically minimized and printed.

Some examples of properties:
* For `sort` function, for every input, the output should contain the same elements, but in the ascending order
* For any kind of data normalization, executing that normalization again on normalized data should leave the result unchanged
* If you have a serializer for your data, then for any input, `deserialize(serialize(input))` should be equivalent to `input`
* For any random input, your program shouldn't crash
* Your new shiny super-optimized data structure should behave the same way as the old time-proven one
* Incrementally updating a data structure on changes in the input should be equivalent to building it anew from the input

In [IntelliJ IDEA](https://github.com/JetBrains/intellij-community) and some other [JetBrains](https://www.jetbrains.com/) IDEs, *jetCheck* is used for checking that:
* lexer/parser never fail on any text, however broken
* incremental lexer and parser results are consistent with full lexing/parsing 
* code completion suggests whatever you want to write, doesn't contain duplicates and doesn't fail
* automatic code transformations don't fail, don't break compilation, don't change in presence of extra parentheses, and don't remove valuable comments
* all internal representations (of which there's many) built for code are always synchronized
* sorting algorithms used for completion and Project View obey transitivity contracts
* and other things

# How to use jetCheck

You can add *jetCheck* dependency to your project by using [jitpack](https://jitpack.io/#jetbrains/jetCheck). Then, in your normal test method, whatever test framework you're using, call [PropertyChecker](src/main/java/org/jetbrains/jetCheck/PropertyChecker.java). Here's a simple example of a failing property:

`PropertyChecker.forAll(Generator.integers(), i -> i == 42);`

If executed, it fails like this:

    org.jetbrains.jetCheck.PropertyFalsified: Falsified on 0
    Shrunk in 1 stage, by trying 1 example
    
    To re-run the minimal failing case, run
      PropertyChecker.customized().rechecking("+/uO5x/L6LKECgEA")
        .forAll(...)
    To re-run the test with all intermediate shrinking steps, use `recheckingIteration(-112063344742606325L, 1)` instead for last iteration, or `withSeed(-112063344742606325L)` for all iterations

This means that for the property `i == 42`, *jetCheck* has found and printed a counter-example: it's `0`. Of course, you'll have more complex properties working with more complex data types and generators for them. When a property-based test fails, the usual strategy is to take the printed value and create a regression unit test based on it, which you can then debug and fix.

You can also debug the property-based test itself by using `rechecking` line from the failure message. If it doesn't fail, then your test might have some unnoticed side effects, and you can debug it using more advanced `recheckingIteration` or `withSeed`, using seeds mentioned in the same message. 

For writing generators and properties, see the documentation of [PropertyChecker](src/main/java/org/jetbrains/jetCheck/PropertyChecker.java) and [Generator](src/main/java/org/jetbrains/jetCheck/Generator.java) methods. For stateful system testing, see [ImperativeCommand](src/main/java/org/jetbrains/jetCheck/ImperativeCommand.java).