# Router

对于一个功能越来越复杂的APP来说，路由对于代码的解耦、页面灵活跳转配置、页面拦截功能提供了很好的支持。一个好的路由框架应支持: Uri的动态注册(不需要再Manifest中配)、 支持跨模块获取接口的实现等。

我仔细阅读了两个业界比较出名的方案`WMRouter`和`ARouter`的源码。用以加深我对目前业界路由技术的了解。

> WMRouter:  (https://github.com/meituan/WMRouter)

> ARouter :   (https://github.com/alibaba/ARouter

### WMRouter

- 整个框架的路由体系是如何设计的 : <a href="WMRouter/基本路由架构梳理.md">基本路由架构梳理</a>

- 每一个路由节点是如何根据注解接编译期动态生成的 : <a href="WMRouter/路由节点的动态生成.md">路由节点的动态生成</a>

- WMRouter是如何提供跨模块加载实现类的 : <a href="WMRouter/利用ServiceLoader运行时加载UriHandler.md">ServiceLoader动态加载路由节点</a>

- <a href="WMRouter/页面路由实例分析.md">页面路由实例分析</a>

### ARouter

- 分析整个路由流程，以及相关类 : <a href="ARouter/基本路由过程.md">基本路由架构梳理</a>

- 路由表示如何根据注解生成并在框架运行时加载到内存 : <a href="ARouter/动态生成路由表.md">路由表的生成</a>

- <a href="ARouter/跨模块加载实现类与参数的自动注入.md">跨模块加载实现类与参数的自动注入</a>

### 方案对比

在阅读完`ARouter`和`WMRouter`的源码后，我对这两个框架的路由功能做了一个对比:

- <a href="Android路由框架:WMRouter与ARouter的对比.md"> WMRouter与ARouter的对比 </a>

