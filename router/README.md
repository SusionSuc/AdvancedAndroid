# Router

对于一个功能越来越复杂的APP来说，路由对于代码的解耦、页面灵活跳转配置、页面拦截功能提供了很好的支持。下面我将分析业界比较出名的两个路由框架[WMRouter](https://github.com/meituan/WMRouter)和[ARouter](https://github.com/alibaba/ARouter)的源码，了解他们的实现原理。

这两个框架的实现核心原理是差不多的: *通过注解标注路由信息,在编译期动态扫描路由信息，生成加载路由表信息的java类。并利用 `gradle transform`和`asm`生成加载全部路由信息的class文件。在app运行时，路由框架反射调用这个class文件,从而完成了路由表的装载*

### [WMRouter](https://github.com/meituan/WMRouter)

在看整个框架的工作原理之前，先来分析一下它的它是如何完成一次路由的:

[基本路由架构梳理](https://github.com/SusionSuc/AdvancedAndroid/blob/master/router/WMRouter/%E5%9F%BA%E6%9C%AC%E8%B7%AF%E7%94%B1%E6%9E%B6%E6%9E%84%E6%A2%B3%E7%90%86.md)

那么路由的基本类`UriHandler`是如何生成的呢？

[路由节点的动态生成](https://github.com/SusionSuc/AdvancedAndroid/blob/master/router/WMRouter/%E8%B7%AF%E7%94%B1%E8%8A%82%E7%82%B9%E7%9A%84%E5%8A%A8%E6%80%81%E7%94%9F%E6%88%90.md)

节点生成了？框架是怎么在运行时动态加载这些路由节点的呢？

[路由节点的加载](https://github.com/SusionSuc/AdvancedAndroid/blob/master/router/WMRouter/%E8%B7%AF%E7%94%B1%E8%8A%82%E7%82%B9%E7%9A%84%E5%8A%A0%E8%BD%BD.md)

整个框架理解了上面这些基本就了解了其核心，再来分析一个`Activity`的路由实例

[页面路由实例分析](https://github.com/SusionSuc/AdvancedAndroid/blob/master/router/WMRouter/%E9%A1%B5%E9%9D%A2%E8%B7%AF%E7%94%B1%E5%AE%9E%E4%BE%8B%E5%88%86%E6%9E%90.md)


### [ARouter](https://github.com/alibaba/ARouter)

`ARouter`的路由节点的动态加载类似于`WMRouter`，就不再分析了，这里主要看一下`ARouter`中是如何组织路由节点，并做Uri的分发的，这是两个框架最大的区别。

接下来就来看一下这两个点在`ARouter`中是如何处理的:

![基本路由过程](https://github.com/SusionSuc/AdvancedAndroid/blob/master/router/ARouter/%E5%9F%BA%E6%9C%AC%E8%B7%AF%E7%94%B1%E8%BF%87%E7%A8%8B.md)

对比一下这两个方案，以此来对`Android`中对于一个路由框架需要的功能做更明确的理解:

### 方案对比

|  | WMRouter | ARouter |
|:------|:------|:------|
|多scheme和host的支持|✅;可随意添加,scheme、host不需要强制配置|❎;支持标准URL跳转。有组的概念，一个路由(url)中的path必须属于某个组 |
|动态注册路由节点|✅;@RouterUri标注|✅;@Route标注 |
|URI正则匹配|✅;使用@RouterRegex标注，匹配的path可以直接跳转到对应界面,比如weblink的跳转可以配置正则匹配来路由|❎;组的概念存在，不支持 |
|拦截器|✅;支持配置全局拦截器和局部拦截器,分别可配置多个,可以自定义拦截顺序|✅;支持配置全局拦截器,可以自定义拦截顺序 |
|转场动画|✅;|✅;|
|降级策略|✅;支持全局降级和局部降级|✅;支持全局降级和局部降级 |
|跳转监听|✅;支持全局和单次|✅;支持全局和单次|
|跳转参数|✅;支持基本类型和自定义类型 |✅;支持基本类型和自定义类型 |
|参数自动注入|❎;|✅; @Autowired 注解的属性可被自动注入 |
|外部跳转控制|✅; 需要配置入口Acitity，支持的uri需要在Manifest中配置|✅;需要配置入口Acitity，支持的uri需要在Manifest中配置|
|特殊页面跳转控制|✅;“exported”注解属性配置,特定页面可以配置不允许跳转|❎;|
|自动生成路由文档|❎;|✅;  |
|路由节点的生成方式|✅; 框架加载时加载全部路由节点到内存|✅;按照组的划分进行懒加载|
|路由节点扩展|✅;扩展性高，可以通过一个Uri不止做页面的跳转|✅; 一般 |
|kotlin支持|❎;不支持，不过可以简单引入kotlin来支持|✅; 支持|


## 模块间通信

|  | WMRouter | ARouter |
| :------| :------ | :------ |
|获取特定接口的实现|✅; @RouterService 注解配置，支持获取接口的所有实现，或根据Key获取特定实现|✅; @Route 注解配置，支持根据Path获取对应接口实现|


## 更多特性

这里只是按照自己的理解做了简单对比，关于两个框架更多特性可访问官方链接:

- WMRouter:  https://mp.weixin.qq.com/s/pKRi5qpZmol7xFIfeBbK_A

- ARouter :  https://github.com/alibaba/ARouter/blob/master/README_CN.md

> 水平有限，如果错误，欢迎指出。

>欢迎Star我的[Android进阶计划](https://github.com/SusionSuc/AdvancedAndroid),看更多干货。
