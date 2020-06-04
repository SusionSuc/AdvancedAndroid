# RecyclerView

对于经常做业务的同学来说，`RecyclerView`可能是Android开发中最常使用的一个View了。但在使用中可能多少会出现诸如使用不顺(老是crash)、不知道`RecyclerView`是否可以实现你想要的功能、怎么实现最好等问题，这时候就需要花费一些时间来解决这些问题。

我认为这些问题产生的原因的很大一部分是因为对于`RecyclerView`不熟悉。那么怎么解决这个问题呢？个人认为就是把`RecyclerView`实现细节了解一下，即看一下源码实现。想象一下，如果`RecyclerView`是你自己写的，那么当你遇到crash时、一个需求是不是能够实现时是不是很快就能得到结果？

所以我打算深入`RecyclerView`的源码来看一下它的实现。经过一番挣扎我只是大概捋顺了它的实现机制。不过我认为这样已经够了，至少以后出现问题上述问题我可能会有一些解决方案浮现在脑中。

`RecyclerView`的源码实现还是很庞大的，一篇文章肯定是不可能讲清楚的。另外由于`RecyclerView`的整体设计还是很优秀的，各部分解耦和扩展性都挺高。在了解了它的大体结构后，我们可以一部分一部分的来看它的源码实现。即整个源码分析一共分为下面几个小节:

## RecyclerView的基本设计结构

第一节先来看一下`RecyclerView`的基本设计结构，为接下来的源码阅读做个索引:

[RecyclerView的基本设计结构](RecyclerView的基本设计结构.md)

## RecyclerView的刷新机制

这节主要看一下`RecyclerView`是如何实现UI刷新的，即:

1. 给RecyclerView设置数据后，RecyclerView是怎么展现的？
2. RecyclerView在滚动时，新的item是如何出现的？

[RecyclerView的刷新机制](RecyclerView的刷新机制.md)

## RecyclerView的复用机制

`RecyclerView`最大的特点就是`ItemView`可以复用，但它的复用逻辑你知道吗？所以这一节主要看一下:

1. 在`adaper.notifyXXX`时RecyclerView的复用逻辑是怎么样的？
2. `RecyclerView`在滚动时是如何实现复用的呢？

[RecyclerView的复用机制](RecyclerView的复用机制.md)


## RecyclerView动画源码浅析

`RecyclerView`支持各种`Item动画`,比如删除、添加、交换等。本节就来看一下`ItemView删除动画`是如何实现的:

[RecyclerView动画源码浅析](RecyclerView动画源码浅析.md)

## 我对RecyclerView使用的思考

我与`RecyclerView`打交道也有一段日子了，本节就来讲一下这段日子中我对于`RecyclerView`的使用的一个小总结吧:

[RecyclerView的使用总结以及常见问题解决方案](RecyclerView的使用总结以及常见问题解决方案.md)


>至于其他的内容，待日后感觉可以再写一下的时候继续补充吧。



