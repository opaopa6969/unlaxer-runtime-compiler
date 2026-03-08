# unlaxer-runtime-compiler

**言語:** 日本語 | [English](./README.md)

`javax.tools.JavaCompiler` を基盤とした、メモリ上での Java ランタイムコンパイラです：

- メモリ内ソースコンパイル
- クラスローダー隔離 (`MemoryClassLoader`)
- バイトコード抽出（保存・リロード用）
- マルチフレームワーク対応（Helidon、Open Liberty、WildFly、Quarkus、標準）

⚠️ **セキュリティに関する注意**: ユーザーが提供した Java コードのコンパイルと実行は任意コードの実行と等価です。
厳密なホワイトリスト化と/または別プロセス/コンテナでの実行隔離を使用してください。

## 必要な環境

- Java **17+**
- **JDK**（JRE ではなく）。`ToolProvider.getSystemJavaCompiler()` を使用するため

## Maven

```xml
<dependency>
  <groupId>org.unlaxer</groupId>
  <artifactId>unlaxer-runtime-compiler</artifactId>
  <version>1.0.0</version>
</dependency>
```

## 使い方

### 基本：文字列からクラスをコンパイル

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
      // 必要に応じてバイトコードを保存...
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
```

### バイトコードからクラスを定義

バイトコードを保存すれば、後で再ロードできます：

```java
import org.unlaxer.compiler.ClassAndByteCode;
import org.unlaxer.compiler.CompileContext;
import org.unlaxer.compiler.JavaFileManagerContext;

public class ReloadExample {
  public static void main(String[] args) {
    byte[] stored = /* DB/blob から読み込む */ null;

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

## クラスローダーのライフサイクル

各 `CompileContext` は独自の `MemoryClassLoader` を持っています。コンパイル済みのクラスが不要になったらコンテキストを閉じてください：

```java
try (CompileContext ctx = new CompileContext(parent, new JavaFileManagerContext())) {
  // コンパイル / 定義
}
// ctx.close() で内部リソースが解放されます
// ガベージコレクタは、クラスローダーが到達不可能になると、クラスを回収できます
```

## 診断 / エラー

コンパイル失敗は `Try` 失敗として返され、詳細な `CompileError` が含まれます：

```java
var result = ctx.compile(className, source);
if (result.isFailure()) {
  CompileError error = result.getCause();
  System.err.println("クラス: " + error.getClassName());
  System.err.println("診断: " + error.getDiagnosticOutput());
  System.err.println("原因: " + error.getCause());
}
```

---

## 応用：マルチフレームワーク対応

このコンパイラは、異なるアプリケーションサーバーで使用される、カスタム Java URL スキームに対応しています。

### サポートされているフレームワーク

| フレームワーク | URL スキーム | 例 |
|---|---|---|
| **Helidon / Open Liberty** | `wsjar:` | `wsjar:file:///opt/lib/app.jar!/` |
| **WildFly / JBoss AS** | `vfsjar:` | `vfsjar:file:///wildfly/lib/module.jar!/` |
| **Quarkus / 標準** | `jar:` | `jar:file:///app/lib.jar!/` |

### カスタム JarURIResolver を使う

`JarURIResolver` インターフェースを実装することで、カスタム URL スキーム変換ができます：

```java
import org.unlaxer.compiler.JarURIResolver;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.compiler.CompileContext;

public class CustomFrameworkExample {
  public static void main(String[] args) {
    // 新しいフレームワーク用のカスタムリゾルバーを定義
    JarURIResolver customResolver = externalForm -> {
      // 例：custom-jar: -> jar:
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
      // カスタムフレームワーク環境内でコンパイル
    }
  }
}
```

### デフォルト動作

カスタムリゾルバーを指定しない場合、`DefaultJarURIResolver` が使用され、自動的に以下に対応します：
- `wsjar:` (Open Liberty/WebSphere) → `jar:`
- `vfsjar:` (WildFly) → `jar:`
- `jar:` (Quarkus/標準) → 変更なし

```java
// 内部で DefaultJarURIResolver を使用
new JavaFileManagerContext()
```

---

## デザインパターン & アーキテクチャ

### 1. ストラテジーパターン：JarURIResolver

`JarURIResolver` インターフェースにより、プラグイン可能な URL スキーム処理が実現され、
コアコードを修正せずに新しいフレームワークのサポートを追加できます：

```
JavaFileManagerContext
    |
    +-- JarURIResolver (インターフェース)
            |
            +-- DefaultJarURIResolver (wsjar, vfsjar, jar)
            |
            +-- CustomResolver (カスタム実装)
```

**拡張ポイント**: 標準的でない URL スキームを持つフレームワークの場合、`JarURIResolver` を実装してください。

### 2. ダブルチェックロッキング：コンパイラー初期化

static `JavaCompiler` インスタンスはダブルチェックロッキングを使用して、スレッドセーフな遅延初期化を確保します：

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

**メリット**：
- 遅延初期化（コンパイラーは初回使用時にのみ作成）
- マルチスレッド環境でのスレッドセーフ
- 初期化後のパフォーマンス低下なし

### 3. Try/Either パターン：エラーハンドリング

コンパイル結果は `Try<T>` で囲まれ、関数型エラーハンドリングが可能です：

```java
Try<ClassAndByteCode> result = ctx.compile(className, source);
result
  .map(cab -> cab.clazz.getName())
  .ifSuccess(System.out::println)
  .ifFailure(err -> System.err.println("失敗: " + err.getMessage()));
```

---

## パフォーマンス＆最適化のヒント

### バイトコードのキャッシング

コンパイル済みバイトコードをキャッシュして、再コンパイルを回避できます：

```java
// L1：ソース → ClassAndByteCode（メモリ内キャッシュ）
Map<String, ClassAndByteCode> sourceCache = new HashMap<>();

// L2：バイトコード → シリアル化ストレージ（DB/ファイル）
Map<String, byte[]> bytecodeStorage = new HashMap<>();

// ハイブリッドアプローチ
ClassAndByteCode getOrCompile(String source) {
  ClassAndByteCode cached = sourceCache.get(source);
  if (cached != null) return cached;

  // バイトコードストレージから再ロードを試みる
  byte[] bytecode = bytecodeStorage.get(hash(source));
  if (bytecode != null) {
    cached = ctx.defineClass(bytecode).getOrThrow();
    sourceCache.put(source, cached);
    return cached;
  }

  // 新規コンパイル
  cached = ctx.compile(className, source).getOrThrow();
  bytecodeStorage.put(hash(source), cached.bytes);
  sourceCache.put(source, cached);
  return cached;
}
```

### リソース管理

`CompileContext` が確実にクローズされるよう、try-with-resources を常に使用してください：

```java
try (CompileContext ctx = new CompileContext(parent, config)) {
  // 1つのコンテキストで複数クラスをコンパイル
  for (String source : sources) {
    ctx.compile(new ClassName(fqcn), source);
  }
} // fileManager, memoryFileManager などが自動的にクリーンアップされる
```

### マルチスレッド対応

`CompileContext` はダブルチェックロッキングを使用した共有 static `JavaCompiler` インスタンスを使用しており、
スレッドセーフです。ただし、各スレッドは独自の `CompileContext` インスタンスを使用する必要があります：

```java
// ✅ 良い：各スレッドが独自のコンテキストを持つ
ExecutorService executor = Executors.newFixedThreadPool(4);
for (String source : sources) {
  executor.submit(() -> {
    try (CompileContext ctx = new CompileContext(parent, config)) {
      ctx.compile(className, source);
    }
  });
}

// ❌ 避けるべき：スレッド間で CompileContext を共有
CompileContext shared = new CompileContext(parent, config);
executor.submit(() -> shared.compile(...)); // ✗ スレッドセーフではない
```

---

## トラブルシューティング

### 「Compiler is null」

**原因**: JRE ではなく JDK で実行されていません。

**解決策**: `tools.jar` を含む、または `ToolProvider.getSystemJavaCompiler()` を持つ JDK を使用してください。

### 「SYSTEM_MODULES[...] not found」

**原因**: サポートされていないモジュール参照を持つカスタムフレームワーク。

**解決策**: カスタム `JarURIResolver` を実装して、フレームワークのモジュール スキームを処理してください。

### コンパイル中の ClassNotFoundException

**原因**: コンパイルされたクラスが、クラスパスに含まれていない外部ライブラリを参照している。

**解決策**: すべての依存ファイルが親 ClassLoader のクラスパスで利用可能であることを確認してください。

---

## 関連プロジェクト

- 📌 **TinyExpression**: このコンパイラーを使用する式評価エンジン

---

ライセンス

Apache-2.0 (LICENSE を参照)。
