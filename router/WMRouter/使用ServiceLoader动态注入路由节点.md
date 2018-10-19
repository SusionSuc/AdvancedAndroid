>文章是作者学习`WMRouter`的源码的重点纪要。 WMRouter官方文档 : https://mp.weixin.qq.com/s/pKRi5qpZmol7xFIfeBbK_A

在继续阅读源码前，我们需要先了解一下`ServiceLoader`,`WMRouter`中的`ServiceLoader`类似于`java spi`, 了解`java spi`可以看一下下面这篇文章:

>https://www.jianshu.com/p/deeb39ccdc53

## ServiceLoader

`WMRouter`中的`ServiceLoader`是自己实现的，机制大致和`java spi`相同, 但不同的是对于实现类的读取，它不是从`META-INF/services`中读取。而是自建了一个实现类的读取机制,我们来看一下它的初始化:
```
    void doInit() {
       Class.forName(“com.sankuai.waimai.router.generated.ServiceLoaderInit”).getMethod("init").invoke(null);
    }
```

即初始化的时候反射调用了`ServiceLoaderInit.init()`方法。我全局搜索了一下这个类并没有发现它的声明，最后发现这个类是使用`Gradle Transform API`动态生成的。对于`Gradle Transform API`可以大致看一下下面这个文章了解一下:

>https://www.jianshu.com/p/37df81365edf

下面来看一下`WMRouter`的transform插件。

### WMRouterPlugin

官方是这样描述它的作用的 : 将注解生成器生成的初始化类汇总到ServiceLoaderInit，运行时直接调用ServiceLoaderInit。 所以上面的`SerciceLoader`初始化反射调用的类就是由这个插件生成的。

这里我大致描述一下这个插件的工作逻辑:

1. 扫描编译生成的class文件夹，或者输入的jar包的指定目录 : com/sankuai/waimai/router/generated/service, 收集目录下的类并保存起来 (这个类其实就是`ServiceInit_xxx1`这种类)
2. 使用`asm`生成`ServiceLoaderInit`类，并调用前面扫描到的类的`init`方法。

即最终产生如下代码：

```
    public class ServiceLoaderInit {
        public static void init() {
            ServiceInit_xxx1.init();
            ServiceInit_xxx2.init();
        }
    }
```

到这里就有疑问了，从开始分析到现在我们并没有看到`ServiceInit_xxx1`这种类是如何生成的呢。那它是在哪里生成的呢？

## ServiceInit_xx

### 怎么生成的？

在上一篇文章已经了解到`UriAnnotationProcessor`在编译时会扫描`@RouterUri`,并且会生成`UriAnnotationInit_xx1`这种类，它的代码就是把根据`@RouterUri`生成的路由`UrlHandler`注册到`UriAnnotationHandler`中。但这些代码在运行时的哪个节点会被调用呢？

其实`UriAnnotationProcessor`在扫描`@RouterUri`生成相关类的同时，还会生成一个类,就是`ServiceInit_xx`:

```
  public void buildHandlerInitClass(CodeBlock code, String genClassName, String handlerClassName, String interfaceName) {
        .... // 生成 UriAnnotationInit_hasValue 代码
        String fullImplName = Const.GEN_PKG + Const.DOT + genClassName;
        String className = "ServiceInit" + Const.SPLITTER + hash(genClassName);
        new ServiceInitClassBuilder(className)
                .putDirectly(interfaceName, fullImplName, fullImplName, false)
                .build();
    }
```

我们看一下`ServiceInitClassBuilder`的`putDirectly`和`build`方法:

```
  public class ServiceInitClassBuilder {
        ...
        public ServiceInitClassBuilder putDirectly(String interfaceName, String key, String implementName, boolean singleton) {
            builder.addStatement("$T.put($T.class, $S, $L.class, $L)",
                    serviceLoaderClass, className(interfaceName), key, implementName, singleton);
            return this;
        }

        public void build() {
            MethodSpec methodSpec = MethodSpec.methodBuilder(Const.INIT_METHOD)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(TypeName.VOID)
                    .addCode(this.builder.build())
                    .build();

            TypeSpec typeSpec = TypeSpec.classBuilder(this.className)
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(methodSpec)
                    .build();

            JavaFile.builder(“com.sankuai.waimai.router.generated.service”, typeSpec)
                    .build()
                    .writeTo(filer);
        }
```

其实就是会把上面代码生成到`com/sankuai/waimai/router/generated/service`文件夹下，这样`WMRouter`的transform插件就能扫描到这些类了:

```
public class ServiceInit_36ed390bf4b81a8381d45028b37cc645 {
  public static void init() {
       ServiceLoader.put(IUriAnnotationInit.class, "com.xxx.UriAnnotationInit_72565413b8384a4bebb02d352762d60d", com.xx.UriAnnotationInit_72565413b8384a4bebb02d352762d60d.class, false);
  }
}
```

### 有何作用

我们重新看一下上面生成的代码,它调用了`SerciceLoader`的`put`方法:

```
     /**
     * @param interfaceClass 接口类
     * @param implementClass 实现类
     */
    public static void put(Class interfaceClass, String key, Class implementClass, boolean singleton) {
        ServiceLoader loader = SERVICES.get(interfaceClass);
        if (loader == null) {
            loader = new ServiceLoader(interfaceClass);
            SERVICES.put(interfaceClass, loader);
        }
        loader.putImpl(key, implementClass, singleton);
    }
```

结合我们了解的 java spi, 这里其实就是把`IUriAnnotationInit`接口的实现类`UriAnnotationInit_xx1`放入了`SerciceLoader`中。而`SerciceLoader`在初始化的时候就会调用`ServiceInit_xx`的`init`方法,`SerciceLoader`中保存了`IUriAnnotationInit`接口的实现类`UriAnnotationInit_xx1`。

经过上面的分析下面我们就再来看一下`@RouterUri`标记的page，是如何在我们app运行时注册到`UriAnnotationHandler`?

我们继续来看一下`UriAnnotationHandler`的初始化方法:

```
   protected void initAnnotationConfig() {
        RouterComponents.loadAnnotation(this, IUriAnnotationInit.class);
    }
```

上面的代码最终会调用到这里:

```
    List<? extends AnnotationInit<T>> services = ServiceLoader.load(clazz).getAll();
    for (AnnotationInit<T> service : services) {
        service.init(handler);
    }
```

即`SerciceLoader`会加载`IUriAnnotationInit`的所有实现类，并传入`UriAnnotationHandler`，调用`init`方法。从而实现了在运行时动态注册了`@RouterUri`标记生成的page的`UriHandler`。

我们用下面这张图总结一下上面的过程:

![SerciceLoader的工作原理](picture/ServiceLoader.png)

## ServiceLoader中更强大的功能

其实上面只是使用了`SerciceLoader`的一部分功能，`WMRouter`实现的`SerciceLoader`还是比较强大的，很有利于代码的解耦。

### @RouterService

java 中的spi机制需要我们在 `META-INF/services`规范好接口与实现类的关系，`WMRouter`中提供`@RouterService`,来简化了这个操作。我们来看一下这个注解是如何使用的:

比如在一个项目中有3个库: interface、lib1、lib2

```
//定义在interface库中
public abstract class LibraryModule {
    public abstract String getModuleName();
}

//定义在lib1中
@RouterService(interfaces = LibraryModule.class)
public class LibraryModule1 extends LibraryModule {
}

//定义在lib2
@RouterService(interfaces = LibraryModule.class)
public class LibraryModule2 extends LibraryModule {
}
```

`WMRouter`中有一个`ServiceAnnotationProcessor`负责处理`RouterService`注解，它会把标记了这个注解的类，生成`ServiceInit_xx`, 即

```
public class ServiceInit_f3649d9f5ff15a62b844e64ca8434259 {
  public static void init() {
    ServiceLoader.put(IUriAnnotationInit.class, "xxx",xxx.class, false);
  }
}
```

这样再由`WMRouter`的插件转换生成 `ServiceLoaderInit.init()`中的调用代码。就达到在运行时把`LibraryModule`的实现注入到`SerciceLoader`中，从而我们可以获得不同库的实现。

*这个特性非常有用，比如一个业务库的代码需要被另一个业务库复用，这时候，我们就可以使用这个机制，从而使两个业务库不耦合的情况下，调用对方功能* 赞！

关于`SerciceLoader`的更多特性，大家可以自行了解，这里就不介绍了。






