package org.unlaxer.compiler;

/**
 * Resolves a JAR URL external form string to a normalized "jar:" scheme URI string.
 * Implementations handle container-specific URL schemes (wsjar, vfsjar, etc.).
 */
@FunctionalInterface
public interface JarURIResolver {
  /**
   * Resolves an external form URL string to a normalized jar: scheme URI.
   *
   * @param externalForm the result of URL.toExternalForm(), including the '!' and path after it
   * @return normalized URI string starting with "jar:"
   */
  String resolve(String externalForm);
}
