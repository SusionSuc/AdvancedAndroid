
- [预加载图片](#预加载图片)
- [图片加载过程的监听](#图片加载过程的监听)
- [无缝切换到Fresco](#无缝切换到fresco)
- [先显示低分辨率的图，然后是高分辨率的图](#先显示低分辨率的图然后是高分辨率的图)

>本文简单介绍一下`Fresco`中一些不是很常用的API,但这些API如果用好了会极大的提升用户体验

## 预加载图片

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

## 图片加载过程的监听

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


## 无缝切换到Fresco

如果你想在一个比较老的项目中引入`Fresco`但又不想在替换`View`层的代码，其实这时候你可以简单的通过自定义`View`的方式引入`Fresco`。即只使用`Fresco`的`DraweeHolder`,具体引入步骤可以参考官方文档:

[在现有项目引入Fresco的一种方案](https://www.fresco-cn.org/docs/writing-custom-views.html)


>其实`Fresco`官方文档还提到了非常多有用的特性，这里简单介绍几个:


## 先显示低分辨率的图，然后是高分辨率的图

假设你要显示一张高分辨率的图，但是这张图下载比较耗时。与其一直显示占位图，你可能想要先下载一个较小的缩略图。这时，你可以设置两个图片的URI，一个是低分辨率的缩略图，一个是高分辨率的图。但是需要注意**动图无法在低分辨率那一层显示。**


```
    Uri lowResUri, highResUri;
    DraweeController controller = Fresco.newDraweeControllerBuilder()
        .setLowResImageRequest(ImageRequest.fromUri(lowResUri))
        .setImageRequest(ImageRequest.fromUri(highResUri))
        .setOldController(mSimpleDraweeView.getController())
        .build();
    mSimpleDraweeView.setController(controller);
```
