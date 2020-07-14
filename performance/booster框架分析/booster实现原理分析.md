
- [Gradle 插件](#gradle-插件)
- [SPI 与 ServiceLoader](#spi-与-serviceloader)
- [booster transform 实现](#booster-transform-实现)

>前面我们对`booster`的大部分功能都做了分析, 并了解到`booster`主要是在`app`编译时做了一些处理来实现这些功能的。可是这些文章并没有分析`booster`能实现这些功能的原理是什么？同时`booster`是一个扩展性极高的框架,它是怎么设计的呢？

本文就主要来分析一下上面两个问题: **`booster`的核心实现原理和`booster`的框架设计**。

>为了更好的弄明白与回答这两个问题，我们先来回顾一些`booster`编写所基于的一些知识点:

## Gradle 插件

在`gradle`中, 插件是用来模块化和重用的组件。我们可以在插件中定义一些常用的方法，以及一些自定义Task。然后把这个插件提供给其他人使用。

`booster`也是一个`gradle`插件:

```
//app.gradle
apply plugin: 'com.didiglobal.booster'
```

编写一个插件需要使用`gradle api`, 并继承自`Plugin<Project>`

>build.gradle
```
dependencies {
    compile gradleApi()
}
```

>BoosterPlugin.kt
```
class BoosterPlugin : Plugin<Project> {
    override fun apply(project: Project) {
       ...
    }
}
```

`booster`插件是在`gradle 工程配置阶段`时加载的。对于`gradle`插件的编写和`gradle`的构建生命周期可以参考这两篇文章:

[Gradle插件编写概述](https://www.jianshu.com/p/0ba503dc69f8)

[Gradle构建生命周期](https://www.jianshu.com/p/a45286b08db0)

## SPI 与 ServiceLoader

`SPI`全称Service Provider Interface, 它提供了一种机制:**为某个接口寻找实现实例的机制**, 而`ServiceLoader`是用来实现`SPI`的核心类。

>理解`SPI`可以参考这篇文章 : https://juejin.im/post/5b9b1c115188255c5e66d18c


## booster transform 实现

整个`booster`框架中有许多`transform`: `booster-transform-activity-thread ` 、`booster-transform-thread `等等。

`booster`利用`SPI`对`Transform`的编写做了高度的抽象，从而使在`booster`中编写一个`transform`十分的容易:

```
class BoosterPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        when {
            project.plugins.hasPlugin("com.android.application") -> project.getAndroid<AppExtension>().let { android ->
                android.registerTransform(BoosterAppTransform()) // 加载所有的 transform
                ...
            }
            ...
        }
    }
}
```

即在注册插件时注册了`BoosterAppTransform`,`BoosterAppTransform`继承自`BoosterTransform`:

```
abstract class BoosterTransform : Transform() {

    override fun getName() = "booster"

    override fun isIncremental() = true

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> = TransformManager.CONTENT_CLASS

    final override fun transform(invocation: TransformInvocation?) {
        invocation?.let {
            BoosterTransformInvocation(it).apply {
                if (isIncremental) {
                    onPreTransform(this)
                    doIncrementalTransform()
                } else {
                    buildDir.file(AndroidProject.FD_INTERMEDIATES, "transforms", "dexBuilder").let { dexBuilder ->
                        if (dexBuilder.exists()) {
                            dexBuilder.deleteRecursively()
                        }
                    }
                    outputProvider.deleteAll()
                    onPreTransform(this)
                    doFullTransform()
                }
                this.onPostTransform(this)
            }
        }
    }
}
```

即`BoosterTransform`的实际工作交给了`BoosterTransformInvocation`。他是`TransformInvocation`的代理类，这个类会使用`ServiceLoader`加载所有的`transform`, 并依次调用`transform`方法:

```
internal class BoosterTransformInvocation(private val delegate: TransformInvocation) : TransformInvocation{
    
    private val transformers = ServiceLoader.load(Transformer::class.java, javaClass.classLoader).toList()

    internal fun doFullTransform() {
        this.inputs.parallelStream().forEach { input ->
            input.directoryInputs.parallelStream().forEach {
                project.logger.info("Transforming ${it.file}")
                it.file.transform(outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes, Format.DIRECTORY)) { bytecode ->
                    bytecode.transform(this)
                }
            }
            input.jarInputs.parallelStream().forEach {
                project.logger.info("Transforming ${it.file}")
                it.file.transform(outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes, Format.JAR)) { bytecode ->
                    bytecode.transform(this)
                }
            }
        }
    }
}
```





