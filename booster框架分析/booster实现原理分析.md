>前面我们对`booster`的大部分功能都做了分析, 并了解到`booster`主要是在`app`编译时做了一些处理来实现这些功能的。可是这些文章并没有分析`booster`能实现这些功能的原理是什么？同时`booster`是一个扩展性极高的框架,它是怎么设计的呢？

本文就主要来分析一下上面两个问题: **`booster`的核心实现原理和`booster`的框架设计**。

>这两个问题其实很好回答,不过想要真正理解答案需要懂的知识还真不少。为了更好的弄明白这两个问题，我们先来回顾一些`booster`编写所基于的一些知识点:

## Gradle 插件

在`gradle`中, 插件是用来模块化和重用的组件。我们可以在插件中定义一些常用的方法，以及一些自定义Task。然后把这个插件提供给其他人使用。

`booster`也是一个`gradle`插件, 因此我们的工程可以引入`booster`:

```
//top level build.gradle
buildscript {
     dependencies {
        classpath "com.didiglobal.booster:booster-gradle-plugin:$booster_version" 
     }
}

//app.gradle
apply plugin: 'com.didiglobal.booster'
```

编写一个插件使用`gradle api`, 并继承自`Plugin<Project>`

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

`booster`整个的编写都是使用`kotlin`来实现的(所以编写一个gradle插件并不困难哦,你了解api就行~),而`booster`插件是在`gradle 工程配置阶段`时加载的。

对于`gradle`插件的编写和`gradle`的构建生命周期可以参考这两篇文章:

[Gradle插件编写概述](https://www.jianshu.com/p/0ba503dc69f8)

[Gradle构建生命周期](Gradle构建生命周期)

## 
