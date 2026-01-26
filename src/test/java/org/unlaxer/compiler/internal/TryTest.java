package org.unlaxer.compiler.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.compiler.ClassAndByteCode;
import org.unlaxer.compiler.ClassName;
import org.unlaxer.compiler.CompileContext;
import org.unlaxer.compiler.CompileError;
import org.unlaxer.compiler.JavaFileManagerContext;

public class TryTest {

  @Test
  public void compileError_isLeft() throws Exception {
    try (CompileContext cc = new CompileContext(getClass().getClassLoader(), new JavaFileManagerContext())) {
      Try<ClassAndByteCode> r = cc.compile(new ClassName("demo.Broken"),
          "package demo; public class Broken { public void x( }");

      assertTrue(r.left.isPresent());
      assertFalse(r.right().isPresent());
      assertTrue(r.left.get() instanceof CompileError);
    }
  }

}
