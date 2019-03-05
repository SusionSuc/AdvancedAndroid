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

[一张图把Android Activity启动过程安排的明明白白](一张图弄清Activity的启动过程.md)

ing [从源码理解Service的工作过程](从源码理解Service的工作过程.md)

## Android视图层源码分析

>这个系列的文章只是大致了解一下Android视图层的工作原理, 并不会深入去探讨源码中的某个点, 毕竟整个视图层过于复杂，如果没有原因的去抓住分析某个细节并没有太大的意义。

### UI视图的渲染原理

`Window`是Android提供的用来构建UI的类，它是承载UI的基本单元。我们在正常开发中一般不会对他直接操作，不过我们所使用的`Activity/Dialog`等UI的展示都是依托于`Window`。
所以想要深入分析Android UI的展示原理，其实沿着`Window`这条线就可以了(目前Android Framework所提供的唯一`Window`实现类是`PhoneWindow`), 首先我们通过剖析`Window`的组成来大致了解一下`Window`: 

[深入剖析Window组成](深入剖析Window组成.md)

上面这篇文章只是简单的讲了`Window`的添加过程和`Activity`中`PhoneWindow`的UI组成结构。那`Window`的UI到底是怎么绘制的呢？

其实是通过`Surface`来显示的。每一个窗口都有一个自己的`Surface`,可以把它理解为一块画布，应用可以通过`Canvas`和`OpenGL`在上面作画。`Surface`的具体显示由`SurfaceFlinger`负责完成。`SurfaceFlinger`可以
将多块`Surface`的内容按照特定的顺序(Z-order)进行混合并输出到`FrameBuffer`，从而显示出用户所见到的`UI`。所以下面我们要好好研究一下`Surface`,首先看一下它是怎么创建的:

[Android的UI显示原理之Surface的创建](Android的UI显示原理之Surface的创建.md)

视图的基本承载单元`Surface`已经准备完毕，那么怎么渲染一个`Surface`呢?

[Android的UI显示原理之Surface的渲染](Android的UI显示原理之Surface的渲染.md)

上面3篇文章中的源码分析几乎包含了整个视图层源码的所有关键对象，下面总结一下这3篇文章:

[Android的UI显示原理总结](Android的UI显示原理总结.md)

### UI视图的用户交互事件处理原理

#### 触摸事件原理分析

下面这篇文章分析一下: 触摸事件怎么产生和收集的? `Activity.dispatchTouchEvent()`是怎么调用到的?

[Android触摸事件全过程分析](Android触摸事件全过程分析.md)





