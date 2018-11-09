
先说一下为什么要抛出这个问题吧？

最近在看插件化相关的技术,因此会涉及到插件中的类如何加载,根据我以前的了解，再加上在网上查到的一下知识,大部分都是这么解释他们的区别的:

- DexClassLoader : 可載入jar、apk和dex，可以從SD卡中載入
- PathClassLoader : 只能載入已安裝到系統中（即/data/app目錄下）的apk文件

然后呢？大部分同学说他们有这两个区别是因为`DexClassLoader`在构造的时候多传了一个`optimizedDirectory`参数，因此造成了这个区别:

```
 public DexClassLoader(String dexPath, String optimizedDirectory,String librarySearchPath, ClassLoader parent) {
        super(dexPath, new File(optimizedDirectory), librarySearchPath, parent);
    }
```

但因为在看插件化技术的时候，就希望把自己看的明明白白的。因此我下载了Android源码(最新的)。我发现在最新的源码中这个参数已经被`deprecated`了，而且，看源码，我真的没看出来这两个ClassLoader是因为这个原因导致`DexClassLoader`能从`SD卡`中加载类。 当然，我也没从源码里面看出来这两个ClassLoader有什么不同。好， 我们来看一下源码(我拉的最新的):

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

即，`DexClassLoader`传的`optimizedDirectory` 参数根本没用。 官方已经标注了`is deprecated` & `no effect since API level 26`。 这我就有疑问了？ 文章一开始 *DexClassLoader相比于PathClassLoader可以加载SD卡上的apk* 是怎么得出的呢?

于是我猜哈，`PathClassLoader`也是可以加载`SD卡`上的apk的，因此我决定验证一下:

```
    private void loadClassTest() {
        File apk = new File(Environment.getExternalStorageDirectory(), "Test.apk");

        PathClassLoader pathClassLoader = new PathClassLoader(apk.getAbsolutePath(), null, this.getClassLoader());
        DexClassLoader dexClassLoader = new DexClassLoader(apk.getAbsolutePath(), null, null, this.getClassLoader());

        String classNameInTestApk = "com.susion.myapplication.TestClass";
        try {
            Class loadByDexClassLoader = dexClassLoader.loadClass(classNameInTestApk);
            Log.e("wpc", "current API :"+applicationInfo.targetSdkVersion+" DexClassLoader load success : " + loadByDexClassLoader.getName());

            Class loadByPathClassLoader = pathClassLoader.loadClass(classNameInTestApk);
            Log.e("wpc", "current API :" +applicationInfo.targetSdkVersion + " PathClassLoader  load success : " + loadByPathClassLoader.getName());  
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

首先我在 `API Platform 27`上跑了这段代码( 即 compileSdkVersion = 27),打印的log如下:

```
 current API :27 DexClassLoader load success : com.susion.myapplication.TestClass
 current API :27 PathClassLoader  load success : com.susion.myapplication.TestClass
```

首先我在 `API Platform 21`上跑了这段代码,打印的log如下:

```
 current API :21 DexClassLoader load success : com.susion.myapplication.TestClass
 current API :21 PathClassLoader  load success : com.susion.myapplication.TestClass
```

emmmmmmm....

