package org.unlaxer.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class ClassNameTest {

  @Test
  public void fullyQualifiedClass_hasPackageAndSimpleName() {
    ClassName cn = new ClassName("demo.Hello");

    assertEquals("demo.Hello", cn.fullName());
    assertEquals("Hello", cn.name());
    assertEquals("demo", cn.packageName());
  }

  @Test
  public void defaultPackage_hasEmptyPackageAndFullNameAsName() {
    ClassName cn = new ClassName("NoPkg");

    assertEquals("NoPkg", cn.fullName());
    assertEquals("NoPkg", cn.name());
    assertEquals("", cn.packageName());
  }

  @Test
  public void nestedClass_usesLastDotAsNameBoundary() {
    // lastIndexOf で分割する現仕様を固定化。
    // ネストした型(Outer$Inner 形式ではなくドット区切り)の FQCN を与えた場合の振る舞い。
    ClassName cn = new ClassName("com.example.Outer.Inner");

    assertEquals("com.example.Outer.Inner", cn.fullName());
    assertEquals("Inner", cn.name());
    assertEquals("com.example.Outer", cn.packageName());
  }

  @Test
  public void singleSegmentNoDot_treatsWholeAsName() {
    ClassName cn = new ClassName("Solo");

    assertEquals("Solo", cn.fullName());
    assertEquals("Solo", cn.name());
    assertFalse("default-package class must have empty packageName",
        cn.packageName() != null && !cn.packageName().isEmpty());
    assertEquals("", cn.packageName());
  }

  @Test
  public void trailingDot_yieldsEmptyName() {
    // 境界値: 末尾にドットがあると name は空文字になる(現仕様)。
    ClassName cn = new ClassName("pkg.");

    assertEquals("pkg.", cn.fullName());
    assertEquals("", cn.name());
    assertEquals("pkg", cn.packageName());
  }
}
