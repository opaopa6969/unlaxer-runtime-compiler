package org.unlaxer.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;
import org.unlaxer.compiler.internal.Try;

public class CompileContextTest {

  @Test
  public void compile_nullSource_isFailureWithCompileError() throws Exception {
    try (CompileContext cc = new CompileContext(getClass().getClassLoader(), new JavaFileManagerContext())) {
      Try<ClassAndByteCode> r = cc.compile(new ClassName("demo.Null"), null);

      assertTrue("null source must produce a failure", r.left.isPresent());
      assertFalse("failure must not carry a right value", r.right().isPresent());
      assertTrue("failure cause must be CompileError", r.left.get() instanceof CompileError);
    }
  }

  @Test
  public void compile_emptySource_isFailureWithCompileError() throws Exception {
    try (CompileContext cc = new CompileContext(getClass().getClassLoader(), new JavaFileManagerContext())) {
      Try<ClassAndByteCode> r = cc.compile(new ClassName("demo.Empty"), "");

      assertTrue("empty source must produce a failure", r.left.isPresent());
      assertTrue("failure cause must be CompileError", r.left.get() instanceof CompileError);
    }
  }

  @Test
  public void compile_classNameMismatch_isFailureWithCompileError() throws Exception {
    // ClassName で指定した名前と、ソース中のクラス名が不一致の場合、
    // コンパイルは失敗し CompileError になる現仕様を固定化。
    try (CompileContext cc = new CompileContext(getClass().getClassLoader(), new JavaFileManagerContext())) {
      String src = "package demo; public class Goodbye { public static int v(){return 1;} }";
      Try<ClassAndByteCode> r = cc.compile(new ClassName("demo.Hello"), src);

      assertTrue("name mismatch must produce a failure", r.left.isPresent());
      assertTrue("failure cause must be CompileError", r.left.get() instanceof CompileError);
    }
  }

  @Test
  public void compile_sameFqcnInSameContext_returnsSameClass_secondSourceIgnored() throws Exception {
    // 重要な回帰ポイント:
    // 同一 CompileContext 内で同一 FQCN を再コンパイルすると、MemoryClassLoader に
    // 既に同名クラスが define 済みのため、2 回目のソース変更は反映されず
    // 同じ Class オブジェクト(== )が返る。
    // 既存の sameFqcn_canHaveTwoGenerations(別コンテキスト → 別クラス) と対になる
    // 仕様の固定化。
    try (CompileContext cc = new CompileContext(getClass().getClassLoader(), new JavaFileManagerContext())) {
      ClassName cn = new ClassName("v1.Recompile");
      String s1 = "package v1; public class Recompile { public static int v(){return 1;} }";
      String s2 = "package v1; public class Recompile { public static int v(){return 2;} }";

      ClassAndByteCode cab1 = cc.compile(cn, s1).get();
      ClassAndByteCode cab2 = cc.compile(cn, s2).get();

      assertEquals("same context + same FQCN must return the identical Class instance",
          cab1.clazz, cab2.clazz);
      assertEquals(1, cab1.clazz.getMethod("v").invoke(null));
      assertEquals("second source must be ignored when class already defined",
          1, cab2.clazz.getMethod("v").invoke(null));
    }
  }

  @Test
  public void getCompiler_concurrentAccess_yieldsSingleSharedInstance() throws Exception {
    // README で強調されている double-checked locking による単一インスタンス保証を検証。
    int threads = 64;
    ExecutorService ex = Executors.newFixedThreadPool(16);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch fire = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ConcurrentHashMap<javax.tools.JavaCompiler, Boolean> seen = new ConcurrentHashMap<>();

    for (int i = 0; i < threads; i++) {
      ex.submit(() -> {
        ready.countDown();
        try {
          fire.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        seen.put(CompileContext.getCompiler(), Boolean.TRUE);
        done.countDown();
      });
    }

    ready.await(5, TimeUnit.SECONDS);
    fire.countDown();
    assertTrue("all threads must complete within timeout", done.await(10, TimeUnit.SECONDS));
    ex.shutdown();

    assertEquals("compiler must be a single shared instance across threads", 1, seen.size());
    assertNotNull(CompileContext.getCompiler());
  }
}
