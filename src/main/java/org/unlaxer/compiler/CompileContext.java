package org.unlaxer.compiler;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.unlaxer.compiler.internal.Try;

public class CompileContext implements Closeable{

  public final MemoryClassLoader memoryClassLoader;
  public final ClassLoader classLoader;
  public final StandardJavaFileManager fileManager;

  public final CustomClassloaderJavaFileManager customClassloaderJavaFileManager;
  public final MemoryJavaFileManager memoryFileManager;
  public final JavaFileManagerContext javaFileManagerContext;

  final Path outputPath;

  final Set<JavaFileObject> compiledClassJavaFileObjects;

  public CompileContext(ClassLoader classLoader, JavaFileManagerContext javaFileManagerContext) {
    this(classLoader, null, javaFileManagerContext);
  }

  public CompileContext(ClassLoader classLoader, Path outputPath, JavaFileManagerContext javaFileManagerContext) {
    super();
    this.memoryClassLoader = new MemoryClassLoader(classLoader);
    this.classLoader = classLoader;
    this.outputPath = outputPath;
    this.javaFileManagerContext = javaFileManagerContext;

    this.fileManager = getCompiler().getStandardFileManager(null, Locale.getDefault(),
        StandardCharsets.UTF_8);

    customClassloaderJavaFileManager = new CustomClassloaderJavaFileManager(
        memoryClassLoader, fileManager, javaFileManagerContext);
    memoryFileManager = new MemoryJavaFileManager(customClassloaderJavaFileManager, javaFileManagerContext);
    compiledClassJavaFileObjects = new HashSet<>();
  }

  public void putAll(Map<? extends String, ? extends byte[]> m) {
    memoryClassLoader.putAll(m);
  }

  // Static JavaCompiler instance with lazy initialization
  private static volatile JavaCompiler compiler;
  private static final Object COMPILER_LOCK = new Object();

  /**
   * Get or initialize the JavaCompiler instance using double-checked locking.
   */
  public static JavaCompiler getCompiler() {
    if (compiler == null) {
      synchronized (COMPILER_LOCK) {
        if (compiler == null) {
          compiler = initializeCompiler();
        }
      }
    }
    return compiler;
  }

  /**
   * Initialize the JavaCompiler from ToolProvider or fallback to JavacTool.
   */
  private static JavaCompiler initializeCompiler() {
    JavaCompiler c = ToolProvider.getSystemJavaCompiler();
    if (c == null) {
      try {
        Class<?> javacTool = Class.forName("com.sun.tools.javac.api.JavacTool");
        Method create = javacTool.getMethod("create");
        c = (JavaCompiler) create.invoke(null);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }
    return c;
  }

  public Try<ClassAndByteCode> compile(ClassName className, String javaSourceCode) {

    JavaFileObject javaFileObjectForJava = new MemoryJavaFileObjectForJava(className, javaSourceCode);

    StringWriter output = new StringWriter();

    try {

      JavaCompiler.CompilationTask task = getCompiler().getTask(
          new PrintWriter(output), memoryFileManager, null,
          null, null, Arrays.asList(javaFileObjectForJava));

      boolean success = task.call();

      if (success) {

        putAll(memoryFileManager.getBytesByName());

        Class<?> clazz = memoryClassLoader.loadClass(className.fullName());
        byte[] bytes = memoryClassLoader.getBytes(className.fullName());
        ClassAndByteCode classAndByteCode = new ClassAndByteCode(clazz, bytes);

        memoryFileManager.setJavaFileOBjectForClass(className.fullName, new MemoryJavaFileObjectForClass(className, bytes));

        return Try.success(classAndByteCode);

      } else {
        return Try.failure(new CompileError(className.fullName(), output.toString()));
      }
    } catch (Exception e) {
      return Try.failure(new CompileError(className.fullName(), output.toString(), e));
    }
  }

  @Override
  public void close() throws IOException {
    List<IOException> exceptions = new ArrayList<>();

    tryClose(fileManager, exceptions);
    tryClose(customClassloaderJavaFileManager, exceptions);
    tryClose(memoryFileManager, exceptions);

    if (!exceptions.isEmpty()) {
      IOException first = exceptions.get(0);
      for (int i = 1; i < exceptions.size(); i++) {
        first.addSuppressed(exceptions.get(i));
      }
      throw first;
    }
  }

  /**
   * Attempt to close a resource, adding any exceptions to the provided list.
   */
  private static void tryClose(AutoCloseable resource, List<IOException> exceptions) {
    if (resource == null) return;

    try {
      resource.close();
    } catch (IOException e) {
      exceptions.add(e);
    } catch (Exception e) {
      exceptions.add(new IOException(e));
    }
  }

  public Optional<Path> outputPath(){
    return Optional.ofNullable(outputPath);
  }
}
