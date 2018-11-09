
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
