package org.unlaxer.compiler.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class TryAdditionalTest {

  @Test
  public void resultOf_nullValue_isFailureWithIllegalArgumentException() {
    // ofNullable(null) は Either の不変条件(left^right) により両方 absent → 例外。
    // resultOf は内部で ofNullable を呼ぶため、null を返す Supplier は
    // IllegalArgumentException で失敗する現仕様を固定化。
    Try<String> r = Try.resultOf(() -> null);

    assertFalse("null result must not be a success", r.right().isPresent());
    assertTrue("null result must be a failure", r.left.isPresent());
    assertTrue("failure cause must be IllegalArgumentException",
        r.left.get() instanceof IllegalArgumentException);
  }

  @Test
  public void resultOf_throwingSupplier_isFailureWithOriginalCause() {
    Try<String> r = Try.resultOf(() -> { throw new IllegalStateException("boom"); });

    assertTrue("throwing supplier must be a failure", r.left.isPresent());
    assertTrue("failure cause must be the original exception",
        r.left.get() instanceof IllegalStateException);
    assertEquals("boom", r.left.get().getMessage());
  }

  @Test
  public void zip_bothSuccess_yieldsTupleOfValues() {
    Try<Integer> a = Try.success(1);
    Try<Integer> b = Try.success(2);

    Try<Tuple2<Integer, Integer>> z = Try.zip(a, b);

    assertTrue("zip of two successes must be a success", z.right().isPresent());
    assertEquals(Integer.valueOf(1), z.get()._1());
    assertEquals(Integer.valueOf(2), z.get()._2());
  }

  @Test
  public void zip_firstFailure_propagatesFirstCause() {
    Try<Integer> a = Try.failure(new RuntimeException("first"));
    Try<Integer> b = Try.success(2);

    Try<Tuple2<Integer, Integer>> z = Try.zip(a, b);

    assertTrue("zip with failed first must be a failure", z.left.isPresent());
    assertEquals("first", z.left.get().getMessage());
  }

  @Test
  public void zip_secondFailure_propagatesSecondCause() {
    // 現仕様: first が成功なら second の throwable を使う。
    Try<Integer> a = Try.success(1);
    Try<Integer> b = Try.failure(new RuntimeException("second"));

    Try<Tuple2<Integer, Integer>> z = Try.zip(a, b);

    assertTrue("zip with failed second must be a failure", z.left.isPresent());
    assertEquals("second", z.left.get().getMessage());
  }

  @Test
  public void fallback_failureSwitchesToSupplier_successStays() {
    AtomicInteger calls = new AtomicInteger();

    Try<Integer> failed = Try.failure(new RuntimeException("orig"));
    Try<Integer> recovered = failed.fallback(() -> { calls.incrementAndGet(); return 42; });

    assertTrue("fallback on failure must recover", recovered.right().isPresent());
    assertEquals(Integer.valueOf(42), recovered.get());
    assertEquals("fallback supplier must be invoked exactly once", 1, calls.get());

    Try<Integer> ok = Try.success(7);
    Try<Integer> notRecovered = ok.fallback(() -> { calls.incrementAndGet(); return 99; });

    assertTrue("fallback on success must keep original value", notRecovered.right().isPresent());
    assertEquals(Integer.valueOf(7), notRecovered.get());
    assertEquals("fallback supplier must NOT be invoked on success", 1, calls.get());
  }

  @Test
  public void map_onSuccess_transformsValue_onFailurePropagatesLeft() {
    Try<Integer> ok = Try.success(3);
    Try<Integer> mapped = ok.map(x -> x * 10);

    assertTrue(mapped.right().isPresent());
    assertEquals(Integer.valueOf(30), mapped.get());

    Throwable cause = new IllegalStateException("fail");
    Try<Integer> failed = Try.failure(cause);
    Try<Integer> mappedFailed = failed.map(x -> x * 10);

    assertFalse(mappedFailed.right().isPresent());
    assertNotNull(mappedFailed.left);
    assertEquals(cause, mappedFailed.left.get());
  }

  @Test
  public void get_onFailure_wrapsThrowableInRuntimeException() {
    Throwable cause = new IllegalStateException("nope");
    Try<Integer> r = Try.failure(cause);

    RuntimeException thrown = null;
    try {
      r.get();
    } catch (RuntimeException e) {
      thrown = e;
    }
    assertNotNull("get() on failure must throw", thrown);
    assertEquals(cause, thrown.getCause());
  }
}
