
这是一份Android进阶计划。主要是学习和总结一些Android项目中会用到的一些关键技术,分析一些著名开源框架的源码。

目前文章还是比较少的，先不做索引，先简单列一下目前所涉及的相关技术点, 相关文章都有可能会随时添加、修改:

|技术点|
|:----|
|Router|
|Gradle插件、代码注入|
|屏幕适配|
|插件化|

>文章有我自己写的，也有的是贴的一些比较好的文章链接(如有侵权，请联系我)。

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


----

# Gradle插件、代码注入

随着项目的成长，我们项目中的`build.gradle`文件不断变大，可复用代码越来越多，我们可能需要把这些代码抽取出去，比如上传aar到公司的maven仓库这个功能。又或者我们需要在`gradle`编译时做一些自定义的处理，比如动态添加一些类文件。对于这些功能一个自定义的gradle插件完全可以完成。并且复用性很好。所以gradle插件的学习是进阶之路上的必修课。

## Gradle插件

- <a href="gradle插件与字节码注入/Gradle构建生命周期.md">Gradle构建生命周期</a>

- <a href="gradle插件与字节码注入/Gradle插件编写概述.md">Gradle插件编写概述</a>

- <a href="gradle插件与字节码注入/GradleTransformAPI的基本使用.md">GradleTransformAPI的基本使用</a>

## 代码注入

### java 注解

很多框架在设计时都使用了自定义注解。自定义注解可以让使用者使用方便，同时使用注解还可以降低框架与代码的耦合。 可以参考下面的文章来学习注解

> 深入理解Java注解类型(@Annotation) : https://blog.csdn.net/javazejian/article/details/71860633

当我们学会使用注解时，我们就需要考虑对于注解的处理我们应该在什么时候？ 编译期还是运行时？我们知道对于注解的处理是使用反射的，反射是很耗性能的(正常代码运行速度的十倍)。所有我们对于注解的处理，一般应选择在编译时期。下面我们就来看一下自定义注解处理器:

运行时注解处理器 (直接反射处理注解)

> Java中的注解-自定义注解处理器 : https://juejin.im/post/5a6a8ab0f265da3e4b76fc4f 

编译时注解处理器 (使用 Annotation Processor)

> https://www.race604.com/annotation-processing/

### javapoet 与 asm

前面在了解`ARouter`和`WMRouter`时，发现这两个框架都用到了`javapoet`和`asm库`。这两个一个是可以产生java源文件的库，一个是可以修改class文件或者产生class文件的库。接下来我们就大概了解一下这两个库的使用。

#### javapoet

由`square`开源的一个可以生成java源代码的开源工具。API设计的简单易理解。如果我们需要在处理注解时要产生一些java源文件或者我们需要生成一些协议好格式的java源码我们都可以使用这个库。当然生成的都是一些比较简单的java源代码。

我们在使用这个库是尽量不要使用`kotlin`来编写 : 首先使用kotlin来编写产生java代码的源文件,同时思考两种代码写法可能有点绕。再者在使用`addStatement("$T.out.print", System::class.java)`。这种API时`$`会合kotlin产生冲突。

对于这个库API的使用可以参考下面这些文章:

> GitHub : https://github.com/square/javapoet  

> javapoet——让你从重复无聊的代码中解放出来 : https://www.jianshu.com/p/95f12f72f69a 

> 可以从这里拷贝一些模板 : https://juejin.im/entry/58fefebf8d6d810058a610de

#### asm库

ASM 是一个 Java 字节码操控框架。它能被用来动态生成类或者增强既有类的功能。ASM 可以直接产生二进制 class 文件，也可以在类被加载入 Java 虚拟机之前动态改变类行为。

> AOP 的利器：ASM 3.0 介绍: https://www.ibm.com/developerworks/cn/java/j-lo-asm30/#N101F3

对于ASM使用的详细介绍可以看`ASM4使用指南`, 这是个中译版，对ASM相关API讲解的十分清楚

> <a href="gradle插件与字节码注入/ASM4使用指南.pdf"> ASM4使用指南.pdf </a>


### 自定义一个`ImplLoader`

在看过 `java注解`、`javapoet`、`asm`后，我们做一个练习，写一个类似`WMRouter`中`ServiceLoader`这样的功能。代码思路基本上是参考`ARouter`和`WMRouter`的。

> 代码放在了这里 : https://github.com/SusionSuc/ImplLoader

在写的过程中遇到了不少问题，记录一下 : <a href="gradle插件与字节码注入/ImplLoader编写记录.md">ImplLoader编写记录</a>


----

# 屏幕适配

`屏幕适配`这个词在业界应该是一个很熟悉的词。因为Android设备已经有几万种了，各种屏幕大小层出不穷，要想让你写的UI在每种设备上都有较好的表现，这是一个不小的挑战。我一直也是感觉它挺重要的。可是说实话我工作到现在接触的屏幕适配的问题较少。不过对于这方面的知识我还是做了很多了解的。接下来我就总结一下这块的知识，做到一个比较清楚的认识。

对于Android屏幕适配的相关知识可以先看一下官方文档:

>https://developer.android.com/guide/practices/screens_support

首先需要知道的是为什么需要做屏幕适配? 为什么官方推荐使用`dp`来做适配? `dp`适配会引发什么问题?  如何解决`dp`适配所引发的问题: 

> <a href="屏幕适配/使用dp做屏幕适配会出现的问题.md">使用dp做屏幕适配会出现的问题</a>

在上面这篇文章，我们了解了`dp适配会出现的问题`，页面目前出现了两种比较好的适配方案`今日头条适配方案`和`SmallestWidth限定符适配方案`。

<a href="https://www.jianshu.com/u/1d0c0bc634db">JessYan</a>对这两种方案做了很透彻的分析，并且对于一些坑点也做了分析:

> 今日头条适配方案细探 :  https://www.jianshu.com/p/55e0fca23b4f

> SmallestWidth限定符适配方案细探 :  https://www.jianshu.com/p/2aded8bb6ede

----

# 插件化 

目前市面上有许多Android插件化方案。每种方案都有各自的实现思路。而且随着`Google`对`对非 SDK API`管理逐渐严格。普遍认为一个比较好的插件化方案应该对Android系统做到`0 hook`。我们这里不去讨论哪种插件化比较好，只是一起来看一下目前业界诸多插件化方案的实现，理解他们的思想，学习他们实现过程中用到的`Android`相关的诸多知识。

>`Android P`开始对`对非SDK API`进行严格管制，先来看一下`私有对非SDK API`: https://developer.android.google.cn/about/versions/pie/restrictions-non-sdk-interfaces

##  <a href="https://github.com/didi/VirtualAPK">VirtualApk</a>

`VirtualApk`是由滴滴开源的一款插件化框架。主要实现思路是`hook`系统多处对于`Android四大组件`的处理，以达到调用插件的四大组件的实现。我们这里主要看一下它是如何实现的、用到了哪些东西:


在`VirtualApk`中，一个插件会被打成一个`.apk`文件。因此加载插件其实就是加载这个`.apk`文件，那么如何加载一个`.apk`文件，并解析出这个文件中的信息，比如四大组件、resourse、类等等 :

<a href="插件化/插件APK的解析.md">插件APK的解析</a>

其实不只是`VirtualApk`，很多其他插件化框架对于插件apk的解析也是这个思路。








