# 插件化 

目前市面上有许多Android插件化方案。每种方案都有各自的实现思路。而且随着`Google`对`对非 SDK API`管理逐渐严格。普遍认为一个比较好的插件化方案应该对Android系统做到`0 hook`。
我们这里不去讨论哪种插件化比较好，只是一起来看一下目前业界诸多插件化方案的实现，理解他们的思想，学习他们实现过程中用到的`Android`相关的诸多知识。

>`Android P`开始对`对非SDK API`进行严格管制，先来看一下`私有对非SDK API`: https://developer.android.google.cn/about/versions/pie/restrictions-non-sdk-interfaces

##  <a href="https://github.com/didi/VirtualAPK">VirtualApk</a>

`VirtualApk`是由滴滴开源的一款插件化框架。主要实现思路是`hook`系统多处对于`Android四大组件`的处理，以达到调用插件的四大组件的实现。我们这里主要看一下它是如何实现的、用到了哪些东西。下面的文章不会去细究实现逻辑，只看`VirtualApk`关于一些关键点的实现思路。

>在`VirtualApk`中，一个插件会被打成一个`.apk`文件。因此加载插件其实就是加载这个`.apk`文件，那么如何加载一个`.apk`文件，并解析出这个文件中的信息，比如四大组件、resourse、类等等 :

<a href="插件化/VirtualApk/插件APK的解析.md">插件APK的解析</a>

其实不只是`VirtualApk`，很多其他插件化框架对于插件apk的解析也是这个思路。

上一篇文章已经详细了解了一个插件的apk的类、资源、四大组件信息时如何被加载到宿主中了。那么接下来我们就来看一下宿主如何使用插件的四大组件。以启动一个插件的Activity为例:

<a href="插件化/VirtualApk/插件Activity的启动.md">插件Activity的启动</a>