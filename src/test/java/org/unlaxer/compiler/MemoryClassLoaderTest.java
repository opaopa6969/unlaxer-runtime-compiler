package org.unlaxer.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;
import org.unlaxer.compiler.internal.Try;

public class MemoryClassLoaderTest {
  
  
  static String code1 ="""
//package sample.v1;//version1. if logic updates then update package.
package v1;
public class CheckDigits{
  public boolean check(String target){
    return target.matches("\\\\d+");
  }
}
""";
  
  static String code2 ="""
package v1;

public class ClassUser{
  public static void main(String[] args){
      System.out.println(new v1.CheckDigits().check("0123"));
  }
  public static boolean check(String arg){
      return new v1.CheckDigits().check(arg);
  }
}
""";  
  


  @Test
  public void test() throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    
    JavaFileManagerContext javaFileManagerContext = new JavaFileManagerContext();
    
    MemoryClassLoader memoryClassLoader = new MemoryClassLoader(contextClassLoader);
    
    
    try (CompileContext compileContext = new CompileContext(memoryClassLoader,javaFileManagerContext)) {
      ClassName className1 = new ClassName("v1.CheckDigits");
      
      Try<ClassAndByteCode> compile1 = compileContext.compile(className1,code1);
      
      ClassAndByteCode classAndByteCode = compile1.get();
      
      ClassName className2 = new ClassName("v1.ClassUser");
      
      Try<ClassAndByteCode> compile2 = compileContext.compile(className2,code2);
      
      ClassAndByteCode classAndByteCode2 = compile2.get();
      
      Method method = classAndByteCode2.clazz.getMethod("main", String[].class);
      String[] params = null; 
      method.invoke(null, (Object)params);
      
      Method checkMethod = classAndByteCode2.clazz.getMethod("check", String.class);
      boolean isNumber = (boolean) checkMethod.invoke(null, "0123");
      assertTrue(isNumber);
      
      boolean isNumber2 = (boolean) checkMethod.invoke(null, "abcd");
      assertFalse(isNumber2);
      
    } catch (IOException e) {
      e.printStackTrace();
      throw new UncheckedIOException(e);
    }
    
  }
  
  
  public static class ClassUser1{
    public static void main(String[] args){
        System.out.println(new CheckDigits1().check("0123"));
    }
  }
  
  public static class CheckDigits1{
    public boolean check(String target){
      return target.matches("\\d+");
    }
  }


  
  @Test
  public void sameFqcn_canHaveTwoGenerations() throws Exception {
    JavaFileManagerContext jctx = new JavaFileManagerContext();

    ClassName cn = new ClassName("v1.Gen");
    String src1 = "package v1; public class Gen { public static int v(){return 1;} }";
    String src2 = "package v1; public class Gen { public static int v(){return 2;} }";

    Class<?> c1;
    try (CompileContext cc1 = new CompileContext(getClass().getClassLoader(), jctx)) {
      c1 = cc1.compile(cn, src1).get().clazz;
      assertEquals(1, c1.getMethod("v").invoke(null));
    }

    Class<?> c2;
    try (CompileContext cc2 = new CompileContext(getClass().getClassLoader(), jctx)) {
      c2 = cc2.compile(cn, src2).get().clazz;
      assertEquals(2, c2.getMethod("v").invoke(null));
    }

    assertNotEquals("different classloaders => different Class objects", c1, c2);
  }

  
  @Test
  public void compile_returnsBytecode() throws Exception {
    JavaFileManagerContext ctx = new JavaFileManagerContext();
    try (CompileContext cc = new CompileContext(getClass().getClassLoader(), ctx)) {
      ClassName cn = new ClassName("demo.Hello");
      String src = "package demo; public class Hello { public static int x(){return 7;} }";

      ClassAndByteCode cab = cc.compile(cn, src).get();

      assertNotNull(cab.bytes);
      assertTrue(cab.bytes.length > 100); // ざっくり
    }
  }

}
