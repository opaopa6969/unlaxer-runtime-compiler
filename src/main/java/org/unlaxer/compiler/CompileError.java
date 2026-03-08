package org.unlaxer.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CompileError extends RuntimeException {

  public enum ComileErrorContextKey{
    calculatorList,
  }

  public final Map<String,Object> objectByKey = new HashMap<>();

  // New fields for enhanced diagnostics
  private final String className;
  private final String diagnosticOutput;

  public CompileError() {
    super();
    this.className = null;
    this.diagnosticOutput = null;
  }

  public CompileError(Consumer<Map<String,Object>> mapConsumer) {
    super();
    this.className = null;
    this.diagnosticOutput = null;
    mapConsumer.accept(objectByKey);
  }

  public CompileError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    this.className = null;
    this.diagnosticOutput = null;
  }

  public CompileError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace , Consumer<Map<String,Object>> mapConsumer) {
    super(message, cause, enableSuppression, writableStackTrace);
    this.className = null;
    this.diagnosticOutput = null;
    mapConsumer.accept(objectByKey);
  }

  public CompileError(String message, Throwable cause) {
    super(message, cause);
    this.className = null;
    this.diagnosticOutput = null;
  }

  public CompileError(String message) {
    super(message);
    this.className = null;
    this.diagnosticOutput = null;
  }

  public CompileError(Throwable cause) {
    super(cause);
    this.className = null;
    this.diagnosticOutput = null;
  }

  public CompileError(String message, Throwable cause , Consumer<Map<String,Object>> mapConsumer) {
    super(message, cause);
    this.className = null;
    this.diagnosticOutput = null;
    mapConsumer.accept(objectByKey);
  }

  public CompileError(String message, Consumer<Map<String,Object>> mapConsumer) {
    super(message);
    this.className = null;
    this.diagnosticOutput = null;
    mapConsumer.accept(objectByKey);
  }

  public CompileError(Throwable cause, Consumer<Map<String,Object>> mapConsumer) {
    super(cause);
    this.className = null;
    this.diagnosticOutput = null;
    mapConsumer.accept(objectByKey);
  }

  /**
   * Constructor with className and diagnosticOutput for enhanced error reporting.
   * @param className the fully qualified name of the class that failed to compile
   * @param diagnosticOutput the compiler diagnostic output
   */
  public CompileError(String className, String diagnosticOutput) {
    super(formatMessage(className, diagnosticOutput));
    this.className = className;
    this.diagnosticOutput = diagnosticOutput;
  }

  /**
   * Constructor with className, diagnosticOutput, and underlying cause.
   * @param className the fully qualified name of the class that failed to compile
   * @param diagnosticOutput the compiler diagnostic output
   * @param cause the underlying exception
   */
  public CompileError(String className, String diagnosticOutput, Throwable cause) {
    super(formatMessage(className, diagnosticOutput), cause);
    this.className = className;
    this.diagnosticOutput = diagnosticOutput;
  }

  /**
   * Format a message from className and diagnosticOutput.
   */
  private static String formatMessage(String className, String diagnosticOutput) {
    return String.format("Compilation failed for class %s:%n%s", className, diagnosticOutput);
  }

  /**
   * Get the class name that failed to compile.
   * @return the fully qualified class name, or null if not available
   */
  public String getClassName() {
    return className;
  }

  /**
   * Get the diagnostic output from the compiler.
   * @return the diagnostic output, or null if not available
   */
  public String getDiagnosticOutput() {
    return diagnosticOutput;
  }
}
