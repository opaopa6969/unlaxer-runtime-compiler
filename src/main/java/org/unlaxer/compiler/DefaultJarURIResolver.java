package org.unlaxer.compiler;

/**
 * Default implementation of JarURIResolver that supports multiple Java framework URL schemes.
 * Handles: wsjar (Open Liberty), vfsjar (WildFly), jar (Quarkus/Standard), and Jakarta EE.
 */
public class DefaultJarURIResolver implements JarURIResolver {
  public static final DefaultJarURIResolver INSTANCE = new DefaultJarURIResolver();

  @Override
  public String resolve(String externalForm) {
    // Extract the part before '!' (the JAR path)
    int bangIndex = externalForm.lastIndexOf('!');
    String jarUri;
    if (bangIndex >= 0) {
      jarUri = externalForm.substring(0, bangIndex);
    } else {
      jarUri = externalForm;
    }

    // Open Liberty / WebSphere: wsjar: → jar:
    if (jarUri.startsWith("wsjar:")) {
      return "jar:" + jarUri.substring("wsjar:".length());
    }

    // WildFly / JBoss: vfsjar: → jar:
    if (jarUri.startsWith("vfsjar:")) {
      return "jar:" + jarUri.substring("vfsjar:".length());
    }

    // Quarkus / Standard: jar: as-is
    if (jarUri.startsWith("jar:")) {
      return jarUri;
    }

    // Jakarta EE fallback: prepend jar: to unknown schemes containing '!'
    if (externalForm.contains("!")) {
      return "jar:" + jarUri;
    }

    return jarUri;
  }
}
