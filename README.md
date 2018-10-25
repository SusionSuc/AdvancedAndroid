
这是一份Android进阶计划。主要是学习和总结一些Android项目中会用到的一些关键技术。下面是文章列表:

>文章大部分是我自己写的，也有的是贴的一些比较好的文章链接(如有侵权，请联系我)。

# Router

对于一个功能越来越复杂的APP来说，路由对于代码的解耦、页面灵活跳转配置、页面拦截功能提供了很好的支持。一个好的路由框架应支持: Uri的动态注册(不需要再Manifest中配)、 支持跨模块获取接口的实现等。

我仔细阅读了两个业界比较出名的方案`WMRouter`和`ARouter`的源码。用以加深我对目前业界路由技术的了解。

WMRouter:  (https://github.com/meituan/WMRouter)

ARouter :   (https://github.com/alibaba/ARouter

### WMRouter

- 整个框架的路由体系是如何设计的 : <a href="router/WMRouter/基本路由架构梳理.md">基本路由架构梳理</a>

- 每一个路由节点是如何根据注解接编译期动态生成的 : <a href="router/WMRouter/路由节点的动态生成.md">路由节点(UrlHander)的动态生成</a>

- WMRouter是如何提供跨模块加载实现类的 : <a href="router/WMRouter/利用ServiceLoader运行时加载UriHandler.md">ServiceLoader动态加载路由节点</a>

- <a href="router/WMRouter/页面跳转的梳理与拦截器的使用.md">页面路由实例分析</a>


### ARouter

- 分析整个路由流程，以及相关类 : <a href="router/ARouter/基本路由过程.md">基本路由架构梳理</a>

- 路由表示如何根据注解生成并在框架运行时加载到内存 : <a href="router/ARouter/动态生成路由表.md">路由表的生成</a>

- <a href="router/ARouter/跨模块加载实现类与参数的自动注入.md">跨模块加载实现类与参数的自动注入</a>

### 方案对比

在阅读完`ARouter`和`WMRouter`的源码后，我对这两个框架的路由功能做了一个对比:

- <a href="router/Android路由框架:WMRouter与ARouter的对比.md"> WMRouter与ARouter的对比 </a>

# Gradle插件、代码注入

随着项目的成长，我们项目中的`build.gradle`文件不断变大，可复用代码越来越多，我们可能需要把这些代码抽取出去，比如上传aar到公司的maven仓库这个功能。又或者我们需要在`gradle`编译时做一些自定义的处理，比如动态添加一些类文件。对于这些功能一个自定义的gradle插件完全可以完成。并且复用性很好。所以gradle插件的学习是进阶之路上的必修课。

### Gradle插件

- <a href="gradle插件与字节码注入/Gradle构建生命周期.md">Gradle构建生命周期</a>

- <a href="gradle插件与字节码注入/Gradle插件编写概述.md">Gradle插件编写概述</a>

- <a href="gradle插件与字节码注入/GradleTransformAPI的基本使用.md">GradleTransformAPI的基本使用</a>

### 代码注入

前面在了解`ARouter`和`WMRouter`时，发现这两个框架都用到了`javapoet`和`asm库`。这两个一个是可以产生java源文件的库，一个是可以修改class文件或者产生class文件的库。接下来我们就大概了解一下这两个库的使用。

#### javapoet

由`square`开源的一个可以生成java源代码的开源工具。API设计的简单易理解。如果我们需要在处理注解时要产生一些java源文件或者我们需要生成一些协议好格式的java源码我们都可以使用这个库。当然生成的都是一些比较简单的java源代码。

我们在使用这个库是尽量不要使用`kotlin`来编写 : 首先使用kotlin来编写产生java代码的源文件,同时思考两种代码写法可能有点绕。再者在使用`addStatement("$T.out.print", System::class.java)`。这种API时`$`会合kotlin产生冲突。

对于这个库API的使用可以参考下面这些文章:

> GitHub : https://github.com/square/javapoet  

> javapoet——让你从重复无聊的代码中解放出来 : https://www.jianshu.com/p/95f12f72f69a 

> 可以从这里拷贝一些模板 : https://juejin.im/entry/58fefebf8d6d810058a610de

#### asm库

> AOP 的利器：ASM 3.0 介绍: https://www.ibm.com/developerworks/cn/java/j-lo-asm30/#N101F3


