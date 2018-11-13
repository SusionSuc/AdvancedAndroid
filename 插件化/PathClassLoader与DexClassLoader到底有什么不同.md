
先说一下为什么要抛出这个问题吧？

最近在看插件化相关的技术,因此会涉及到插件中的类如何加载,根据我以前的了解，再加上在网上查了解的知识,认为他们的区别是:

- DexClassLoader : 可加载jar、apk和dex，可以SD卡中加载
- PathClassLoader : 只能加载已安裝到系統中（即/data/app目录下）的apk文件

有这两个区别是因为`DexClassLoader`在构造的时候多传了一个`optimizedDirectory`参数，因此造成了这个区别:

```
 public DexClassLoader(String dexPath, String optimizedDirectory,String librarySearchPath, ClassLoader parent) {
        super(dexPath, new File(optimizedDirectory), librarySearchPath, parent);
    }
```

但我在看源码的时候发现了一个问题 : 我发现在最新的源码中这个参数已经被`deprecated`了。而且源码好像真没有表现出他们俩有什么不同

>DexClassLoader.java
```
public class DexClassLoader extends BaseDexClassLoader {
    /**
     @param optimizedDirectory this parameter is deprecated and has no effect since API level 26.
     */
    public DexClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(dexPath, null, librarySearchPath, parent);
    }
}
```

>PathClassLoader.java
```
public class PathClassLoader extends BaseDexClassLoader {
    public PathClassLoader(String dexPath, ClassLoader parent) {
        super(dexPath, null, null, parent);
    }

    public PathClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(dexPath, null, librarySearchPath, parent);
    }
}
```

即，`DexClassLoader`传的`optimizedDirectory` 参数根本没用。 官方已经标注了`is deprecated` & `no effect since API level 26`。 那 *DexClassLoader相比于PathClassLoader可以加载SD卡上的apk* 是怎么得出的呢?

在最新源码中，这两者构造函数的能力是一样的。并且 *基类是不可能强判子类做相关处理逻辑的吧？*, 因此，再看一下官方文档对这两个类的解释:

- PathClassLoader
>提供ClassLoader在本地文件系统中的文件和目录列表上运行的简单实现，但不尝试从网络加载类。Android将此类用于其系统类加载器及其应用程序类加载器。

- DexClassLoader
>它可以加载 .jar、.apk和dex文件。这可用于执行未作为应用程序的一部分安装的代码。在API级别26之前，此类加载器需要一个应用程序专用的可写目录来缓存优化的类。使用Context.getCodeCacheDir()创建这样一个目录：
>`File dexOutputDir = context.getCodeCacheDir();`自`API 26`后不要在外部存储上缓存优化的类。 外部存储不提供保护应用程序免受代码注入攻击所必需的访问控制。

看官方文档，好像说的也不明白。但是在26以前`optimizedDirectory`参数是用来指明缓存优化后的加载的类的目录。26以后就废弃了。

我对这两个类做了一个测试发现: `PathClassLoader`也是可以加载`SD卡`上的apk的。

下面是测试代码:

```
    private void loadClassTest() {
        File apk = new File(Environment.getExternalStorageDirectory(), "Test1.apk");
        PathClassLoader pathClassLoader = new PathClassLoader(apk.getAbsolutePath(), null, this.getApplication().getClassLoader());
        DexClassLoader dexClassLoader = new DexClassLoader(apk.getAbsolutePath(), null, null, this.getApplication().getClassLoader());

        String classNameInTestApk = "com.susion.myapplication.modle2.Module2";

        try {
            Class loadByPathClassLoader = pathClassLoader.loadClass(classNameInTestApk);
            Log.e("susion", " PathClassLoader  load success : " + loadByPathClassLoader.getName());

            Class loadByDexClassLoader = dexClassLoader.loadClass(classNameInTestApk);
            Log.e("susion", " DexClassLoader load success : " + loadByDexClassLoader.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

*跑这段代码前注意申请相关存储权限。* 这个`Test1.apk`是我用另一个工程打的包，放在了sd卡的根目录。并没有安装在手机上。

首先我在 `API Platform 27`上跑了这段代码(即 compileSdkVersion = 27),打印的log如下:

```
  PathClassLoader  load success : com.susion.myapplication.modle2.Module2
  DexClassLoader load success : com.susion.myapplication.modle2.Module2
```

这段代码在 21、18上跑的效果是一样的。即，都能加载成功。那这两个ClassLoader到底有什么区别呢？



