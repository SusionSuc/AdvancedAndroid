# 屏幕适配

`屏幕适配`这个词在业界应该是一个很熟悉的词。因为Android设备已经有几万种了，各种屏幕大小层出不穷，要想让你写的UI在每种设备上都有较好的表现，这是一个不小的挑战。我一直也是感觉它挺重要的。可是说实话我工作到现在接触的屏幕适配的问题较少。不过对于这方面的知识我还是做了很多了解的。接下来我就总结一下这块的知识，做到一个比较清楚的认识。

对于Android屏幕适配的相关知识可以先看一下官方文档:

>https://developer.android.com/guide/practices/screens_support

首先需要知道的是为什么需要做屏幕适配? 为什么官方推荐使用`dp`来做适配? `dp`适配会引发什么问题?  如何解决`dp`适配所引发的问题: 

> <a href="使用dp做屏幕适配会出现的问题.md">使用dp做屏幕适配会出现的问题</a>

在上面这篇文章，我们了解了`dp适配会出现的问题`，页面目前出现了两种比较好的适配方案`今日头条适配方案`和`SmallestWidth限定符适配方案`。

<a href="https://www.jianshu.com/u/1d0c0bc634db">JessYan</a>对这两种方案做了很透彻的分析，并且对于一些坑点也做了分析:

> 今日头条适配方案细探 :  https://www.jianshu.com/p/55e0fca23b4f

> SmallestWidth限定符适配方案细探 :  https://www.jianshu.com/p/2aded8bb6ede

