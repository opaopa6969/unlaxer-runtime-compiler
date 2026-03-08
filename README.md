# unlaxer-runtime-compiler

In-memory Java runtime compiler built on `javax.tools.JavaCompiler` with:

- in-memory source compilation
- classloader isolation (`MemoryClassLoader`)
- bytecode extraction (store/reload use-cases)
- multi-framework support (Helidon, Open Liberty, WildFly, Quarkus, standard)

⚠️ **Security note**: compiling and executing user-provided Java code is equivalent to running arbitrary code.
Use strict whitelisting and/or isolate execution in a separate process/container.

## Requirements

- Java **17+**
- A **JDK** (not a JRE), because it uses `ToolProvider.getSystemJavaCompiler()`.

## Maven

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>unlaxer-runtime-compiler</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Usage

### Basic: Compile a class from a String

```java
import org.unlaxer.compiler.ClassName;
import org.unlaxer.compiler.ClassAndByteCode;
import org.unlaxer.compiler.CompileContext;
import org.unlaxer.compiler.JavaFileManagerContext;

public class Example {
  public static void main(String[] args) {
    String fqcn = "demo.Hello";
    String src =
        "package demo;\n" +
        "public class Hello {\n" +
        "  public String greet(String name) { return \"Hello, \" + name; }\n" +
        "}\n";

    try (CompileContext ctx = new CompileContext(
        Example.class.getClassLoader(),
        new JavaFileManagerContext()
    )) {
      ClassAndByteCode cab = ctx.compile(new ClassName(fqcn), src)
          .getOrThrow(); // Try<T> API

      Object instance = cab.clazz.getDeclaredConstructor().newInstance();
      var m = cab.clazz.getMethod("greet", String.class);
      System.out.println(m.invoke(instance, "world")); // -> Hello, world

      byte[] bytecode = cab.bytes;
      // Store bytecode if needed...
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
```

### Define a class from bytecode

If you store bytecode, you can reload it later:

```java
import org.unlaxer.compiler.ClassAndByteCode;
import org.unlaxer.compiler.CompileContext;
import org.unlaxer.compiler.JavaFileManagerContext;

public class ReloadExample {
  public static void main(String[] args) {
    byte[] stored = /* load from DB/blob */ null;

    try (CompileContext ctx = new CompileContext(
        ReloadExample.class.getClassLoader(),
        new JavaFileManagerContext()
    )) {
      ClassAndByteCode cab = ctx.defineClass(stored).getOrThrow();
      System.out.println(cab.clazz.getName());
    }
  }
}
```

## ClassLoader lifecycle

Each CompileContext owns a MemoryClassLoader. Close the context when you no longer need the compiled classes:

```java
try (CompileContext ctx = new CompileContext(parent, new JavaFileManagerContext())) {
  // compile / define
}
// ctx.close() releases internal resources.
// GC can reclaim classes once the classloader becomes unreachable.
```

## Diagnostics / errors

Compilation failures are returned as Try failures with detailed `CompileError`:

```java
var result = ctx.compile(className, source);
if (result.isFailure()) {
  CompileError error = result.getCause();
  System.err.println("Class: " + error.getClassName());
  System.err.println("Diagnostics: " + error.getDiagnosticOutput());
  System.err.println("Root cause: " + error.getCause());
}
```

---

## Advanced: Multi-Framework Support

This compiler supports custom Java URL schemes used by different application servers.

### Supported Frameworks

| Framework | URL Scheme | Example |
|-----------|-----------|---------|
| **Helidon / Open Liberty** | `wsjar:` | `wsjar:file:///opt/lib/app.jar!/` |
| **WildFly / JBoss AS** | `vfsjar:` | `vfsjar:file:///wildfly/lib/module.jar!/` |
| **Quarkus / Standard** | `jar:` | `jar:file:///app/lib.jar!/` |

### Using a Custom JarURIResolver

The `JarURIResolver` interface allows you to implement custom URL scheme transformations:

```java
import org.unlaxer.compiler.JarURIResolver;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.compiler.CompileContext;

public class CustomFrameworkExample {
  public static void main(String[] args) {
    // Define a custom resolver for a new framework
    JarURIResolver customResolver = externalForm -> {
      // Example: custom-jar: -> jar:
      if (externalForm.startsWith("custom-jar:")) {
        return "jar:" + externalForm.substring("custom-jar:".length());
      }
      return externalForm;
    };

    JavaFileManagerContext ctx = new JavaFileManagerContext(customResolver);
    try (CompileContext compileCtx = new CompileContext(
        MyClass.class.getClassLoader(),
        ctx
    )) {
      // Compile within custom framework environment
    }
  }
}
```

### Default Behavior

If you don't specify a custom resolver, `DefaultJarURIResolver` is used, which automatically handles:
- `wsjar:` (Open Liberty/WebSphere) → `jar:`
- `vfsjar:` (WildFly) → `jar:`
- `jar:` (Quarkus/standard) → unchanged

```java
// Uses DefaultJarURIResolver internally
new JavaFileManagerContext()
```

---

## Design Patterns & Architecture

### 1. Strategy Pattern: JarURIResolver

The `JarURIResolver` interface enables pluggable URL scheme handling, making it easy to support new frameworks without modifying core code:

```
JavaFileManagerContext
    |
    +-- JarURIResolver (interface)
            |
            +-- DefaultJarURIResolver (wsjar, vfsjar, jar)
            |
            +-- CustomResolver (your implementation)
```

**Extension point**: Implement `JarURIResolver` for frameworks with non-standard URL schemes.

### 2. Double-Checked Locking: Compiler Initialization

The static `JavaCompiler` instance uses double-checked locking to ensure thread-safe lazy initialization:

```java
private static volatile JavaCompiler compiler;
private static final Object COMPILER_LOCK = new Object();

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
```

**Benefits**:
- Lazy initialization (compiler created only when first needed)
- Thread-safe in multi-threaded environments
- No performance penalty after initialization

### 3. Try/Either Pattern: Error Handling

Compilation results are wrapped in `Try<T>` for functional error handling:

```java
Try<ClassAndByteCode> result = ctx.compile(className, source);
result
  .map(cab -> cab.clazz.getName())
  .ifSuccess(System.out::println)
  .ifFailure(err -> System.err.println("Failed: " + err.getMessage()));
```

---

## Performance & Optimization Tips

### Caching Bytecode

Compiled bytecode can be cached to avoid recompilation:

```java
// L1: Source → ClassAndByteCode (in-memory cache)
Map<String, ClassAndByteCode> sourceCache = new HashMap<>();

// L2: Bytecode → serialized storage (database/file)
Map<String, byte[]> bytecodeStorage = new HashMap<>();

// Hybrid approach
ClassAndByteCode getOrCompile(String source) {
  ClassAndByteCode cached = sourceCache.get(source);
  if (cached != null) return cached;

  // Try reload from bytecode storage
  byte[] bytecode = bytecodeStorage.get(hash(source));
  if (bytecode != null) {
    cached = ctx.defineClass(bytecode).getOrThrow();
    sourceCache.put(source, cached);
    return cached;
  }

  // Compile fresh
  cached = ctx.compile(className, source).getOrThrow();
  bytecodeStorage.put(hash(source), cached.bytes);
  sourceCache.put(source, cached);
  return cached;
}
```

### Resource Management

Always use try-with-resources to ensure `CompileContext` is closed:

```java
try (CompileContext ctx = new CompileContext(parent, config)) {
  // Compile multiple classes in one context
  for (String source : sources) {
    ctx.compile(new ClassName(fqcn), source);
  }
} // Automatic cleanup of fileManager, memoryFileManager, etc.
```

### Multi-Threading

`CompileContext` uses a shared `static` `JavaCompiler` instance that is thread-safe via double-checked locking. However, each thread should use its own `CompileContext` instance:

```java
// ✅ Good: Each thread has its own context
ExecutorService executor = Executors.newFixedThreadPool(4);
for (String source : sources) {
  executor.submit(() -> {
    try (CompileContext ctx = new CompileContext(parent, config)) {
      ctx.compile(className, source);
    }
  });
}

// ❌ Avoid: Sharing CompileContext across threads
CompileContext shared = new CompileContext(parent, config);
executor.submit(() -> shared.compile(...)); // ✗ Not thread-safe
```

---

## Troubleshooting

### "Compiler is null"

**Cause**: Running with a JRE instead of a JDK.

**Solution**: Use a JDK that includes `tools.jar` or has `ToolProvider.getSystemJavaCompiler()`.

### "SYSTEM_MODULES[...] not found"

**Cause**: Custom framework with unsupported module reference.

**Solution**: Implement a custom `JarURIResolver` to handle the framework's module scheme.

### ClassNotFoundException during compilation

**Cause**: Compiled class references an external library not in the classpath.

**Solution**: Ensure all dependencies are available in the parent ClassLoader.

---

## Related Projects

- 📖 **[README (Japanese)](./README_ja.md)** - 日本語版ドキュメント
- 📌 **TinyExpression**: Expression evaluation engine using this compiler
- 🔧 **[v2-memo/](./v2-memo/)**: Detailed design review and improvement roadmap

---

License

Apache-2.0 (see LICENSE).
