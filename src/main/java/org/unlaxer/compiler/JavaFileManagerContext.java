package org.unlaxer.compiler;

import java.net.URL;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.tools.StandardLocation;
import javax.tools.JavaFileManager.Location;

public class JavaFileManagerContext {
  public final Predicate<Location> matchForStandardFileManager;
  public final Predicate<Location> matchForOtherFileManager;
  public final JarURIResolver jarURIResolver;

  public JavaFileManagerContext(
      Predicate<Location> matchForStandardFileManager,
      Predicate<Location> matchForOtherFileManager,
      JarURIResolver jarURIResolver) {
    super();
    this.matchForStandardFileManager = matchForStandardFileManager;
    this.matchForOtherFileManager = matchForOtherFileManager;
    this.jarURIResolver = jarURIResolver;
  }

  public JavaFileManagerContext(Predicate<Location> matchForStandardFileManager,Predicate<Location> matchForOtherFileManager) {
    super();
    this.matchForStandardFileManager = matchForStandardFileManager;
    this.matchForOtherFileManager = matchForOtherFileManager;
    this.jarURIResolver = DefaultJarURIResolver.INSTANCE;
  }

  public JavaFileManagerContext(JarURIResolver jarURIResolver) {
    super();
    this.matchForStandardFileManager = defaultMatchForStandardFileManager;
    this.matchForOtherFileManager = defaultMatchForOtherFileManager;
    this.jarURIResolver = jarURIResolver;
  }

  /**
   * @deprecated Use {@link JavaFileManagerContext(JarURIResolver)} instead.
   * This constructor maintains backwards compatibility.
   */
  @Deprecated(forRemoval = false)
  public JavaFileManagerContext(Function<URL, String> jarURLStringFromURL) {
    super();
    this.matchForStandardFileManager = defaultMatchForStandardFileManager;
    this.matchForOtherFileManager = defaultMatchForOtherFileManager;
    // Convert Function<URL, String> to JarURIResolver
    this.jarURIResolver = externalForm -> {
      try {
        return jarURLStringFromURL.apply(new URL(externalForm));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  public JavaFileManagerContext() {
    super();
    this.matchForStandardFileManager = defaultMatchForStandardFileManager;
    this.matchForOtherFileManager = defaultMatchForOtherFileManager;
    this.jarURIResolver = DefaultJarURIResolver.INSTANCE;
  }

  private static final Predicate<Location> defaultMatchForOtherFileManager = //
      location -> {
        String name = location.getName();
        return false == name.startsWith("SYSTEM_MODULES[") ||
            name.equals("SYSTEM_MODULES[java.base]");
      };

  private static final Predicate<Location> defaultMatchForStandardFileManager = //
      location -> //
      location == StandardLocation.PLATFORM_CLASS_PATH || //
      location.getName().startsWith("SYSTEM_MODULES[");

}