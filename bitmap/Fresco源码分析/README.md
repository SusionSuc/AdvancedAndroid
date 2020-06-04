## Fresco 源码赏析

首先来看一下`Fresco`的整体设计,为后续整个框架的源码阅读找准思路和方向:

[Fresco整体设计架构赏析](Fresco架构设计赏析.md)

`Fresco`中存在着3中图片缓存: 解码图片内存缓存、编码图片内存缓存和磁盘缓存，下面来大致看一下它们的实现:

[Fresco缓存架构分析](Fresco缓存架构分析.md)

[浅谈Fresco编码图片缓存](浅谈Fresco编码图片缓存.md)

最后大致来看一下`Fresco`的UI层的原理，比如占位图是如何切换到实际图片的?

[Fresco图片显示原理浅析](Fresco图片显示原理浅析.md)


### 最后贴一下网上和`Fresco`有关的文章:

缩减Fresco的内存缓存来降低Fresco的内存占用 : [Fresco 5.0以上内存持续增长问题优化](https://blog.csdn.net/honjane/article/details/65629799)。

如何加载图片资源文件: [fresco加载drawable图片和asstes图片](https://blog.csdn.net/zxzxzxy1985/article/details/49020287)

Fresco为什么必须设置`wrap_content` : [WHY](https://www.fresco-cn.org/docs/wrap-content.html)

Fresco图片加载框架使用经验小结 : [十条使用心得](http://www.10tiao.com/html/169/201608/2650820929/1.html)


### Fresco使用的一些零碎

我们一般在使用`Fresco`时只会简单的调用`DraweeView.setImageUri`,不过`Fresco`还有很多`API`，这个`API`运行你对整个图片加载流程做各种操作，了解这些API的使用可能会为应用的图片加载优化提供不少思路。

其实对于`Fresco`的使用，官网已经写了十分详细的文档，绝大多数你需要的功能都在官方文档中可以找到: [Fresco官方文档](https://www.fresco-cn.org/docs/index.html)

这里我简单的说两个我使用的API:

#### 预加载图片

>先来理解一下**预加载**

比如我们从网络拉取图片集合显示到`RecyclerView`中,在这整个过程中我们取从网络获取到图片数据为时间节点T1,我们把图片URL设置给`SimpleDraweeView`的时间为T2。`Fresco`从T2开始加载图片,T1到T2的时间大约间隔个几百毫秒。如果`Fresco`可以从T1就开始加载图片的话，那不相当于图片被提前加载了几百毫秒(一般一个接口的请求事件在1秒内),这也就代表着用户可以更快速的看到图片，进而用户体验大大提升。那么如何做到呢？

其实`Fresco`提供了图片预加载的API:

>ImagePipeline.java
```
    /**
    * Submits a request for prefetching to the bitmap cache.
    *
    * <p> Beware that if your network fetcher doesn't support priorities prefetch requests may slow
    * down images which are immediately required on screen.
    *
    * @param imageRequest the request to submit
    * @return a DataSource that can safely be ignored.
    */
    public DataSource<Void> prefetchToBitmapCache(ImageRequest imageRequest,Object callerContext) {
        ...
    }
```

使用这个API，我们不需要`View`就可以开启图片的加载:

```
    Fresco.getImagePipeline().prefetchToBitmapCache(...)
```

#### 图片加载过程的监听

我们知道`Fresco`的图片加载过程分为好多步，我们可能会有这种需求:监听`Fresco`图片加载过程每一步，比如统计一个图片网络加载时间的耗时。

其实`Fresco`提供了全局的监听器来监听整个图片加载过程中的每一步,我们在配置`Fresco`时就可以把这个`Listenter`设置好:

```
    val pipelineConfig = ImagePipelineConfig.newBuilder(this)
                        .setRequestListeners(listener)
                        .build()
```

来看一下这个`Listenter`的回调:

```
public interface RequestListener extends ProducerListener {

}

public interface ProducerListener {


    void onProducerStart(String requestId, String producerName);


    void onProducerFinishWithSuccess(
        String requestId,
        String producerName,
        @Nullable Map<String, String> extraMap);

    void onProducerFinishWithFailure(
        String requestId,
        String producerName,
        Throwable t,
        @Nullable Map<String, String> extraMap);

    ...
}
```

`Producer`在`Fresco`中代表这个图片加载流程中的某一步，比如网络加载、解码等等。每一个`Producer`都有一个特定的名字，因此我们只需要在回调中解析我们感兴趣的`Producer`的事件即可。

