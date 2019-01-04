# AndroidFramework源码分析

阅读Framework的源码可以让你理解Android常用组件的实现原理，在写代码时可以胸有成竹。业界许多技术的实现原理如果脱离Android源码是无何说起的，比如热修复、插件化。

当你阅读完一遍AndroidFramework层代码后，相信其他的框架的源代码对你来说都是小儿科。

## 如何阅读源码呢？

每个人阅读源码的方式都不一样。我呢，并没有把Android源代码全部下载下来编译，我只是下载了Framework层`base`的代码 : https://android.googlesource.com/platform/frameworks/base/

我查看的方式也很简单，就是使用`IntelLij IDEA`。所以就不存在想看哪里跳哪里的情况，一个类里面的跳转直接看类结构、跨类我一般都是全局搜索。我感觉这样看起来也是ok的。熟练后效率也不低。

## Binder

这是AndroidFramework层一个非常核心的东西，在阅读源码时你会发现，哪里都有它，所以，要想好好理解源码，得先把它弄明白了。网上介绍`Binder`的文章很多，这里我谈一下我所理解的`Binder` :

[我所理解的Binder机制](我所理解的Binder机制.md) 

## 四大组件源码

Android四大组件是我们日常开发中最常用的。我们天天和都在用他们提供的接口。所以，如果你深入源码，了解一下他们实现的机制，那么你在使用它们的时候是不是更有一种做`主人`的感觉呢？哈哈

[从源码理解BroadcastReceiver的工作过程](从源码理解BroadcastReceiver的工作过程.md)

[从源码理解ContentProvider的工作过程](ContentProvider启动过程分析.md)

ing [从源码理解Activity的工作过程](从源码理解Activity的工作过程.md)

ing [从源码理解Service的工作过程](从源码理解Service的工作过程.md)

## Android UI层源码分析

### 基于Window的UI体系分析

### UI的刷新逻辑

### UI事件的产生与传递
