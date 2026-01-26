# unlaxer-runtime-compiler

In-memory Java runtime compiler built on `javax.tools.JavaCompiler` with:

- in-memory source compilation
- classloader isolation (`MemoryClassLoader`)
- bytecode extraction (store/reload use-cases)
:
> ⚠️ Security note: compiling and executing user-provided Java code is equivalent to running arbitrary code.
> Use strict whitelisting and/or isolate execution in a separate process/container.

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
### Compile a class from a String
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

    // Use the current ClassLoader as a parent.
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

## Define a class from bytecode

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

Compilation failures are returned as Try failures (and/or CompileError where applicable).
Use your Try utilities to surface compiler messages to operators.

License

Apache-2.0 (see LICENSE).
