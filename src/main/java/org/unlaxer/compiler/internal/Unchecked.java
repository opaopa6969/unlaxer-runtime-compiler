package org.unlaxer.compiler.internal;

import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Unchecked {
  
  interface Logger{
    default void error(String message) {
      
    }

    default void  error(String string, Throwable e) {
      
    }
  }
  
  public static void setLogger(Logger logger) {
    Unchecked.logger = logger;
  }
  
  static Logger logger = new Logger() {};

  public static <T> Consumer<T> of(ThrowingConsumer<T> target) throws LambdaException {
    return consumer(target);
  }

  public static <T> Consumer<T> consumer(ThrowingConsumer<T> target) throws LambdaException {
    return (parameter -> {
      try {
        target.accept(parameter);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new LambdaException(e);
      }
    });
  }

  public static <T> Supplier<T> of(ThrowingSupplier<T> target) throws LambdaException {
    return supplier(target);
  }

  public static <T> Supplier<T> supplier(ThrowingSupplier<T> target) throws LambdaException {
    return (() -> {
      try {
        return target.get();
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new LambdaException(e);
      }
    });
  }

  public static <T> Supplier<Optional<T>> optionalOf(ThrowingSupplier<T> target) throws LambdaException {
    return optionalSupplier(target);
  }

  public static <T> Supplier<Optional<T>> optionalSupplier(ThrowingSupplier<T> target) throws LambdaException {
    return (() -> {
      try {
        return Optional.of(target.get());
      } catch (Throwable e) {
        return Optional.empty();
      }
    });
  }

  public static <T, R> Function<T, R> of(ThrowingFunction<T, R> target) throws LambdaException {
    return function(target);
  }

  public static <T, R> Function<T, R> function(ThrowingFunction<T, R> target) throws LambdaException {
    return (parameter -> {
      try {
        return target.apply(parameter);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new LambdaException(e);
      }
    });
  }

  public static <T, R> Function<T, Try<R>> tryFunction(ThrowingFunction<T, R> target) throws LambdaException {
    return (parameter -> {
      try {
        return Try.success(target.apply(parameter));
      } catch (Exception e) {
        return Try.failure(e);
      }
    });
  }

  public static <T> T ofFuture(Future<T> future) throws LambdaException {
    try {
      return future.get();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new LambdaException(e);
    }
  }

  public static <T, R> Function<T, Optional<R>> optionalOf(ThrowingFunction<T, R> target) {
    return optionalFunction(target);
  }

  public static <T, R> Function<T, Optional<R>> optionalFunction(ThrowingFunction<T, R> target) {
    return (parameter -> {
      try {
        return Optional.of(target.apply(parameter));
      } catch (Throwable e) {
        return Optional.empty();
      }
    });
  }

  public static <T, R> Function<T, Optional<R>> optionalFunction(ThrowingFunction<T, R> target,
      BiConsumer<T, Throwable> errorConsumer) {
    return (parameter -> {
      try {
        return Optional.of(target.apply(parameter));
      } catch (Throwable e) {
        errorConsumer.accept(parameter, e);
        return Optional.empty();
      }
    });
  }

  public static <T> Predicate<T> of(ThrowingPredicate<T> target) throws LambdaException {
    return predicate(target);
  }

  public static <T> Predicate<T> predicate(ThrowingPredicate<T> target) throws LambdaException {
    return (parameter -> {
      try {
        return target.test(parameter);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new LambdaException(e);
      }
    });
  }

  public static <T> Consumer<T> consumerWithSuppress(ThrowingConsumer<T> target) {
    return (parameter -> {
      try {
        target.accept(parameter);
      } catch (Throwable e) {
            	logger.error("raised exception",e);
      }
    });
  }

  public static Runnable of(ThrowingRunnable runnable) {
    return () -> {
      try {
        runnable.run();
      } catch (Throwable e) {
    			logger.error("raised exception",e);
      }
    };

  }

  public static void run(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
      logger.error("raised exception", e);
    }
  }

  public static void runWithThrows(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public static void run(ThrowingRunnable runnable, Consumer<Throwable> throwableConsumer) {
    try {
      runnable.run();
    } catch (Throwable e) {
      throwableConsumer.accept(e);
    }
  }

  public static class SupplierAndTry<T> {

    public final ThrowingSupplier<T> supplier;
    public Try<T> result;

    public SupplierAndTry(ThrowingSupplier<T> supplier, Try<T> result) {
      super();
      this.supplier = supplier;
      this.result = result;
    }

    public SupplierAndTry(ThrowingSupplier<T> supplier) {
      this.supplier = supplier;
      this.result = resultOf(supplier);
      result.onFailure(e -> logger.error("raised exception", e));
    }
  }

  public static <T> SupplierAndTry<T> execute(ThrowingSupplier<T> supplier) {

    return new SupplierAndTry<>(supplier);
  }

  public static <T> Try<T> run(ThrowingSupplier<T> supplier) {
    Try<T> result = resultOf(supplier);
    result.onFailure(e -> logger.error("raised exception", e));
    return result;
  }

  public static <R> Try<R> resultOf(ThrowingSupplier<R> supplier) {
    try {
      R r = supplier.get();
      return Try.success(r);
    } catch (Throwable e) {
      return Try.failure(e);
    }
  }

//    @FunctionalInterface
  public static interface ThrowingRunnable {
    public void run() throws Exception;
  }

//    @FunctionalInterface
  public static interface ThrowingConsumer<T> {
    public void accept(T t) throws Exception;
  }

//    @FunctionalInterface
  public static interface ThrowingSupplier<T> {
    public T get() throws Exception;
  }

//    @FunctionalInterface
  public static interface ThrowingFunction<T, R> {
    public R apply(T t) throws Exception;
  }

//    @FunctionalInterface
  public static interface ThrowingPredicate<T> {
    public boolean test(T t) throws Exception;
  }

  public static class LambdaException extends RuntimeException {

    private static final long serialVersionUID = 798257349281977890L;

    private LambdaException(Throwable cause) {
      super(cause);
    }
  }
}
