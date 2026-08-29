package org.unlaxer.compiler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DefaultJarURIResolverTest {

  @Test
  public void wsjar_isNormalizedToJar() {
    assertEquals("jar:file:///opt/lib/app.jar",
        DefaultJarURIResolver.INSTANCE.resolve("wsjar:file:///opt/lib/app.jar!/"));
  }

  @Test
  public void vfsjar_isNormalizedToJar() {
    assertEquals("jar:file:///wildfly/lib/module.jar",
        DefaultJarURIResolver.INSTANCE.resolve("vfsjar:file:///wildfly/lib/module.jar!/"));
  }

  @Test
  public void jar_isPassedThrough() {
    assertEquals("jar:file:///app/lib.jar",
        DefaultJarURIResolver.INSTANCE.resolve("jar:file:///app/lib.jar!/"));
  }

  @Test
  public void noBang_noScheme_isReturnedAsIs() {
    assertEquals("file:///some/dir/",
        DefaultJarURIResolver.INSTANCE.resolve("file:///some/dir/"));
  }

  @Test
  public void unknownSchemeWithBang_isPrefixedWithJar() {
    // Jakarta EE fallback: 未知スキームかつ '!' を含む場合は jar: を前置する現仕様。
    assertEquals("jar:custom:/path/to/lib.jar",
        DefaultJarURIResolver.INSTANCE.resolve("custom:/path/to/lib.jar!/entry"));
  }

  @Test
  public void multipleBangs_usesLastBangAsBoundary() {
    // lastIndexOf('!') で切る現仕様を固定化。
    assertEquals("jar:file:/a!b/lib.jar",
        DefaultJarURIResolver.INSTANCE.resolve("wsjar:file:/a!b/lib.jar!/pkg/"));
  }
}
