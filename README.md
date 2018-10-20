
这是一份Android进阶计划。主要是学习和总结一些Android项目中会用到的一些关键技术。下面是文章列表:

>文章大部分是我自己写的，也有的是贴的一些比较好的文章链接(如有侵权，请联系我)。

# Router

对于一个功能越来越复杂的APP来说，路由对于代码的解耦、页面灵活跳转配置、页面拦截功能提供了很好的支持。一个好的路由框架应支持: Uri的动态注册(不需要再Manifest中配)、 支持跨模块获取接口的实现等。

我拜读了两个业界比较出名的方案`WMRouter`和`ARouter`的源码。用以加深我对目前业界路由技术的了解。

## WMRouter (https://github.com/meituan/WMRouter)

<p><a href="router/WMRouter/基本路由架构梳理.md">基本路由架构梳理</a></p>

<p><a href="router/WMRouter/路由节点的动态生成.md">路由节点(UrlHander)的动态生成</a></p>

<p><a href="router/WMRouter/利用ServiceLoader运行时加载UriHandler.md">ServiceLoader动态加载路由节点</a></p>

<p><a href="router/WMRouter/页面跳转的梳理与拦截器的使用.md">页面路由实例分析</a></p>


## ARouter (https://github.com/alibaba/ARouter)

<p><a href="router/ARouter/基本路由过程.md">基本路由架构梳理</a></p>

<p><a href="router/ARouter/动态生成路由表.md">路由表的生成</a></p>

<p><a href="router/ARouter/跨模块加载实现类与参数的自动注入.md">跨模块加载实现类与参数的自动注入</a></p>

## 方案对比

<p><a href="router/Android路由框架:WMRouter与ARouter的对比.md"> WMRouter与ARouter的对比 </a></p>