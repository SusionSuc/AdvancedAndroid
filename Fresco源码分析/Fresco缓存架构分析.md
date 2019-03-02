>本文是`Fresco`源码分析系列第二篇文章:

[(第一篇)Fresco架构设计赏析](Fresco架构设计赏析.md)

## 引言

我们先来回顾一下上一篇文章中的一幅图:

![](picture/NetworkFetchSequence.png)

这张图描述了`Fresco`在第一次加载一张网络图片时所经历的过程，从图中可以看出涉及到缓存的`Producer`共有4个:`BitmapMemroyCacheGetProducer`、`BitmapMemoryCacheProducer`、`EncodedMemoryCacheProducer`和`DiskCacheWriteProducer`。`Fresco`在加载图片时会按照图中**绿色箭头**所示依次经过这四个`缓存Producer`，一旦在某个`Producer`得到图片请求结果，就会按照**蓝色箭头**所示把结果依次回调回来,这里我先简单介绍一下这4个`缓存Producer`的作用:

1. `BitmapMemroyCacheGetProducer`:这个`Producer`会去内存缓存中检查有没命中，如果命中则返回图片请求结果。
2. `BitmapMemoryCacheProducer`:这个`Producer`会监听其后面的`Producer`的`Result`，并把`Result(CloseableImage)`存入缓存。
3. `EncodedMemoryCacheProducer`:它也是一个内存缓存，不过它缓存的是未解码的图片，即字节流。
4. `DiskCacheWriteProducer`:顾名思义，它负责把图片缓存到磁盘，它缓存的也是未解码的图片。


## 解码图片与未解码图片

在`Fresco`中，`CloseableImage`就是已解码的图片，而`EncodeImage`就是为解码的图片。

### CloseableImage

`CloseableImage`是一个接口，我们最常接触到的它的实现是`CloseableStaticBitmap`:

>CloseableStaticBitmap.java
```
public class CloseableStaticBitmap extends CloseableBitmap {
    private volatile Bitmap mBitmap;
    ...
}
```

即可以把`CloseableStaticBitmap`理解为`Bitmap`的封装。

### EncodeImage

它内部其实是直接封装了`图片的字节/图片的文件字节流`:

>EncodeImage.java
```
public class EncodedImage implements Closeable {
    private final @Nullable CloseableReference<PooledByteBuffer> mPooledByteBufferRef;  //实际上未解码的图片的字节
    private final @Nullable Supplier<FileInputStream> mInputStreamSupplier;  //直接缓存一个文件字节流
}
```

## Bitmap内存缓存 : BitmapMemoryCacheProducer

`BitmapMemroyCacheGetProducer`派生自`BitmapMemoryCacheProducer`,与`BitmapMemoryCacheProducer`的不同就是**只读不写**而已,比较简单，就不看了。

`BitmapMemoryCacheProducer`的缓存逻辑很简单:

>BitmapMemoryCacheProducer.java
```
public class BitmapMemoryCacheProducer implements Producer<CloseableReference<CloseableImage>> {

    private final MemoryCache<CacheKey, CloseableImage> mMemoryCache;

    @Override
    public void produceResults(Consumer<CloseableReference<CloseableImage>> consumer...){

        //1.先去缓存中获取
        CloseableReference<CloseableImage> cachedReference = mMemoryCache.get(cacheKey);

        //2.命中缓存直接返回请求结果
        if (cachedReference != null) {
            consumer.onNewResult(cachedReference, BaseConsumer.simpleStatusForIsLast(isFinal));
            return;
        }

        ...
        //3.wrapConsumer来观察后续Producer的结果
        Consumer<CloseableReference<CloseableImage>> wrappedConsumer = wrapConsumer(consumer..);

        //4.让下一个Producer继续工作
        mInputProducer.produceResults(wrappedConsumer, producerContext);
    }

    protected Consumer<CloseableReference<CloseableImage>> wrapConsumer(){
        return new DelegatingConsumer<...>(consumer) {

            @Override
            public void onNewResultImpl(CloseableReference<CloseableImage> newResult...){
                //5.缓存结果
                newCachedResult = mMemoryCache.cache(cacheKey, newResult); 

                //6.通知前面的Producer图片请求结果
                getConsumer().onNewResult((newCachedResult != null) ? newCachedResult : newResult, status);
            }
        }
    }
}
```

流程图如下(后面两个缓存的流程与它基本相同，因此对于缓存整体流程只画这一次):

![](picture/BitmapMemoryCacheProducer工作流.png)

**图中红色的字体是正常网络加载图片的步骤**，这里我们来细看一下`MemoryCache`的实现:

### 内存缓存的实现 : MemoryCache

`MemoryCache`是一个接口，在这里它的对应实现是`CountingMemoryCache`，


