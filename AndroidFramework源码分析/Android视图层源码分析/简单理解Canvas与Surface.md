>本文将简单梳理一下`Canvas`与`Surface`的关系。算是对前面[Android的UI显示原理之Surface的渲染](Android的UI显示原理之Surface的渲染.md)一文的补充。


`Canvas`是一个绘图的工具类，其API提供了一系列绘图指令供开发者使用。根据绘制加速模式的不同，`Canvas`有软件`Canvas`与硬件`Canvas`只分。`Canvas`的绘图指令可以分为两个部分:绘制指令和辅助指令。那一个`Canvas`是怎么创建的呢？与`Surface`有什么关联呢？

这些问题从源码中可以找到答案:

## Canvas的创建

`Surface`和`Canvas`可以说是一对一的关系。在new一个`Surface`时就创建了一个对应的`Canvas`:

>Surface.java
```
public class Surface implements Parcelable {
    private final Canvas mCanvas = new CompatibleCanvas(); 
    ...
}
```

`CompatibleCanvas`是一个兼容`软硬`渲染的`Canvas`，本文我们只追踪`软Canvas`的实现。看一下它的构造函数:

```
public Canvas() {
    if (!isHardwareAccelerated()) { //软 Canvas
        mNativeCanvasWrapper = nInitRaster(null);
        mFinalizer = NoImagePreloadHolder.sRegistry.registerNativeAllocation(this, mNativeCanvasWrapper);  //susionw 把这个canvas分配的内存注册在native上？
    } else {
        mFinalizer = null;
    }
}
```

`nInitRaster`是一个nativie方法:
```
static jlong initRaster(JNIEnv* env, jobject, jobject jbitmap) {
    SkBitmap bitmap;
    if (jbitmap != NULL) { //默认new canvas时是null
        GraphicsJNI::getSkBitmap(env, jbitmap, &bitmap);
    }
    return reinterpret_cast<jlong>(Canvas::create_canvas(bitmap));  //创建了一个 SkiaCanvas
}

Canvas* Canvas::create_canvas(const SkBitmap& bitmap) {
    return new SkiaCanvas(bitmap);
}
```



>参考文章

- 《深入理解Android 卷3》