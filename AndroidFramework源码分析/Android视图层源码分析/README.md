# Android视图层源码分析

>这个系列的文章只是大致了解一下Android视图层的工作原理, 并不会深入去探讨源码中的某个点, 毕竟整个视图层过于复杂，如果没有原因的去抓住分析某个细节并没有太大的意义。

`Window`是Android提供的用来构建UI的类，它是承载UI的基本单元。我们在正常开发中一般不会对他直接操作，不过我们所使用的`Activity/Dialog`等UI的展示都是依托于`Window`。所以想要深入分析Android UI的
展示原理，其实沿着`Window`这条线就可以了。目前Android Framework所提供的唯一`Window`实现类是`PhoneWindow`。

首先我们通过剖析`Window`的组成来大致了解一下`Window`: 

[深入剖析Window组成](Android视图层源码分析/深入剖析Window组成.md)

上面这篇文章我只是简单了讲了`Window`的添加过程和`Activity`中`PhoneWindow`的UI组成结构。那:

1. `Window`创建后在服务端(`WindowManagerService`)是如何管理的呢？
2. 大名鼎鼎的`Surface`是如何创建的？

上面这2个问题，在下面这篇文章都可以找到答案:

[Android的UI显示原理之Surface的创建](Android的UI显示原理之Surface的创建.md)

视图的基本承载单元`Surface`已经准备完毕，那么怎么渲染一个`Surface`呢？

[Android的UI显示原理之Surface的渲染](Android的UI显示原理之Surface的渲染.md)