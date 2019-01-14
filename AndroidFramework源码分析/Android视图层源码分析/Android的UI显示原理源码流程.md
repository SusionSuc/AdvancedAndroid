本文仅是源码流程跟踪的一个索引过程，比较乱，后续我会基于这篇文章仔细整理。

>上一篇文章[深入剖析Window组成](深入剖析Window组成.md)了解到Android应用程序的`UI组成结构`。并且知道`windowManager.addView(view, layoutParams)`可以算是添加了一个理论上的`Window`，那这个`Window`在`WindowManagerService`中的添加逻辑是什么？都做了什么？本文主要跟随源码来理一下系统的处理逻辑，只求弄清楚一些关键步骤，并不做深究。`SurfaceFlinger`还是比较复杂的，有兴趣的同学可以按照本文的逻辑去仔细研究一下源码。本文是基于Android8.0以上源码分析的。

`ViewRootImpl`具有应用程序与系统服务`WindowManagerService`交流的的功能，即可以跨进程通信。`windowManager.addView(view, layoutParams)`的调用会在应用程序中创建一个`ViewRootImpl`对象，并调用`ViewRootImpl.setView()`。这个方法会向`WindowManagerService`正式发起了`Window`的添加操作。

# Window在WindowManagerService中的创建逻辑

>ViewRootImpl.java
```
public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
    ...
    //mWindowSession是一个aidl，ViewRootImpl利用它来和WindowManagerService交互
    //mWindow是一个aidl，WindowManagerService可以利用这个对象与服务端交互
    //mAttachInfo可以理解为是一个data bean，可以跨进程传递
    res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
            getHostVisibility(), mDisplay.getDisplayId(), mWinFrame,¡
            mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
            mAttachInfo.mOutsets, mAttachInfo.mDisplayCutout, mInputChannel);
    ...
}
```

`ViewRootImpl.setView()`核心就是调用`mWindowSession.addToDisplay()`，相关参数的含义在上面已经标注。`mWindowSession.addToDisplay()`跨进程调用到了`WindowManagerService.addWindow()`:

```
public int addWindow(Session session, IWindow client...) {
    ...
    //WindowState用描述一个Window
    final WindowState win = new WindowState(this, session, client, token, parentWindow,
                appOp[0], seq, attrs, viewVisibility, session.mUid,
                session.mCanAddInternalSystemWindow);
    ...
    win.attach();  //会创建一个SurfaceSession

    mWindowMap.put(client.asBinder(), win); //mWindowMap是WindowManagerService用来保存当前所有Window新的的集合
    ...
    win.mToken.addWindow(win); //一个token下会有多个win state。 其实token与PhoneWindow是一一对应的。
    ...
}
```

上面这段代码涉及到UI显示原理的是`win.attach()`。这个方法会调用到`Session.windowAddedLocked()`:

```
void windowAddedLocked(String packageName) {
    ...
    if (mSurfaceSession == null) { 
        ...
        mSurfaceSession = new SurfaceSession();
        ...
    }
}

//SurfaceSession类的构造方法
public final class SurfaceSession {
    private long mNativeClient; // SurfaceComposerClient*

    public SurfaceSession() {
        mNativeClient = nativeCreate(); //
    }
```

`nativeCreate()`是一个native方法,具体实现位于`android_view_SurfaceSession.cpp`:

```
static jlong nativeCreate(JNIEnv* env, jclass clazz) {
    SurfaceComposerClient* client = new SurfaceComposerClient();
    client->incStrong((void*)nativeCreate);
    return reinterpret_cast<jlong>(client);
}
```

即`nativeCreate`返回了一个`SurfaceComposerClient`对象的指针。一般一个应用程序只会有一个`SurfaceComposerClient`对象，应用程序可以使用它来和`SurfaceFlinger`跨进程通信，那`SurfaceComposerClient`对象构造时做了什么呢？

>SurfaceComposerClient.cpp
```
//构造函数其实并没有做什么
SurfaceComposerClient::SurfaceComposerClient() : mStatus(NO_INIT){ }

//这个方法在第一次使用SurfaceComposerClient的指针的时候会调用
void SurfaceComposerClient::onFirstRef() {
    ....
    sp<ISurfaceComposerClient> conn;
    //sf 就是SurfaceFlinger
    conn = (rootProducer != nullptr) ? sf->createScopedConnection(rootProducer) :
            sf->createConnection();
    ...
}
```

即`SurfaceComposerClient`对象在使用时会调用`sf->createConnection()`创建一个`ISurfaceComposerClient`。它其实是一个`aidl`对象。它的实例时`Clinet`。我们看一下它的创建过程`SurfaceFlinger.createConnection()`:

# SurfaceComposerClient连接到SurfaceFlinger

>SurfaceFlinger.cpp
```
sp<ISurfaceComposerClient> SurfaceFlinger::createConnection() {
    return initClient(new Client(this)); //initClient这个方法其实并没有做什么，
}
```

即构造了一个`Client`对象，`Client`实现了`ISurfaceComposerClient`接口。是一个可以跨进程通信的aidl对象。它的构造函数其实并没有做什么特别的事情，先不看了。


综上所述,`ViewRootImpl.setView()`所引发的主要操作是:

1. `WindowManagerService`创建了一个`WindowState`。用来表示客户端的一个`Window`
2. `WindowManagerService`创建了一个`SurfaceSession`,`SurfaceSession`会与`SurfaceFlinger`构建链接，创建了一个可以跨进程通信的对象`Client(ISurfaceComposerClient)`,利用它可以和`SurfaceFlinger`通信。


**一个`(ViewRootImpl)Window`要想开始渲染UI，必须经过上面这两个步骤，经过上面的步骤才会被`WindowManagerService`识别，并且具有与`SurfaceFlinger`通信的能力。**


# Surface的创建

其实`ViewRootImpl`对象在构造的时候就创建了一个`Surface`, 但其实并没有做什么逻辑，`Surface`的构造函数是空的。那`Surface`到底在什么地方去做一些初始化操作的呢? 其实入口逻辑还是在`ViewRootImpl.setView()`:

>ViewRootImpl.java
```
public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
    ...
    requestLayout(); //susion 请求layout。先添加到待渲染队列中  
    ...
    res = mWindowSession.addToDisplay(mWindow, ...);
    ...
}
```

即在与`SurfaceFlinger`建立连接之前(创建`Client(ISurfaceComposerClient)`)调用`requestLayout()`。这个方法会引起整个`ViewRootImpl`所管理的整个view tree的重新渲染，即走`measure/layout/draw`三部曲。
但是这里就有疑问:

ViewRootImpl所代表的Window还没有添加到`WindowManagerService`中，怎么可能进行渲染呢？

深入看一下这个方法吧:

## ViewRootImpl.requestLayout()

它会调用`scheduleTraversals()`，这个方法的主要逻辑是:

```
void scheduleTraversals() {
    ...
    mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
    ...
}
```

即通过`mChoreographer(Choreographer)`post了一个`Choreographer.CALLBACK_TRAVERSAL`类型的`callback`。那`Choreographer`是什么呢？


## Choreographer

这个类的详细分析可以看[Android Choreographer源码分析](https://www.jianshu.com/p/996bca12eb1d)一文，我们可以先简单的理解为它是用来同步控制一个`ViewRootImpl`的UI显示操作。操作一共分为4种:

```
CALLBACK_INPUT：输入
CALLBACK_ANIMATION:动画
CALLBACK_TRAVERSAL:遍历，执行measure、layout、draw
CALLBACK_COMMIT：遍历完成的提交操作，用来修正动画启动时间
```

我们可以通过它所提供的`postXXX(type,callback)`来触发一个上面这4类动作，Choreographer接收显示系统的时间脉冲(垂直同步信号-VSync信号)，在下一个frame渲染时控制执行这些操作。

所以前面`scheduleTraversals`中post的`callback(mTraversalRunnable)`在什么时候会执行呢？其实是在`WindowManagerService`链接到`SurfaceFlinger`后开始执行的。`mTraversalRunnable`这个callback会调用到`ViewRootImpl.performTraversals()`,大部分同学可能知道这个方法是一个`view tree`的`measure/layout/draw`的控制方法:

>ViewRootImpl.java
```
private void performTraversals() {
    finalView host = mView; //mView是一个Window的根View，对于Activity来说就是DecorView
    ...
    relayoutWindow(params, viewVisibility, insetsPending);
    ...
    performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
    ...         
    performLayout(lp, mWidth, mHeight);
    ...
    performDraw();
    ...
}
```

这个方法巨长，这里我只贴出来本文需要分析的几点，而核心就是`relayoutWindow(params, viewVisibility, insetsPending)`,它包含了`Surface`的真正创建逻辑。它会通过IPC调用到`WindowManagerService.relayoutWindow()`:

```
public int relayoutWindow(Session session, IWindow client....Surface outSurface){  //这个outSurface其实就是ViewRootImpl中的那个Surface
    ...
    result = createSurfaceControl(outSurface, result, win, winAnimator);  
    ...
}

private int createSurfaceControl(Surface outSurface, int result, WindowState win,WindowStateAnimator winAnimator) {
    ...
    surfaceController = winAnimator.createSurfaceLocked(win.mAttrs.type, win.mOwnerUid);
    ...
    surfaceController.getSurface(outSurface);
}
```

`winAnimator.createSurfaceLocked`其实是通过`SurfaceControl`的构造函数创建了一个`SurfaceControl`对象,这个对象的作用其实就是负责在维护`Surface`。看一下这个对象的构造方法:

```
long mNativeObject; 
private SurfaceControl(SurfaceSession session, String name, int w, int h, int format, int flags,
            SurfaceControl parent, int windowType, int ownerUid){
    ...
    mNativeObject = nativeCreate(session, name, w, h, format, flags,
        parent != null ? parent.mNativeObject : 0, windowType, ownerUid);
    ...
}
```
即调用native方法`nativeCreate()`。其实是通过native创建了一个`Surface`:

## Surface 创建

### 创建一个Surface

>android_view_SurfaceControl.cpp 
```
static jlong nativeCreate(JNIEnv* env, ...) {
    sp<SurfaceComposerClient> client(android_view_SurfaceSession_getClient(env, sessionObj)); //这个client其实就是前面创建的SurfaceComposerClinent
    sp<SurfaceControl> surface; //创建成功之后，这个指针会指向新创建的Surface
    status_t err = client->createSurfaceChecked(String8(name.c_str()), w, h, format, &surface, flags, parent, windowType, ownerUid);
    ...
    return reinterpret_cast<jlong>(surface.get()); //返回这个SurfaceControl的地址
}
```

>SurfaceComposerClient.cpp
```
status_t SurfaceComposerClient::createSurfaceChecked(...sp<SurfaceControl>* outSurface..)
{
   sp<IGraphicBufferProducer> gbp; //这个对象很重要
    ...
    err = mClient->createSurface(name, w, h, format, flags, parentHandle, windowType, ownerUid, &handle, &gbp);
    if (err == NO_ERROR) {
        //Surface 创建成功，就创建一个SurfaceControl
        *outSurface = new SurfaceControl(this, handle, gbp, true /* owned */);
    }
    return err;
}
```

这个`mClient`就是`ISurfaceComposerClient(aidl接口)`类型的对象，它可以与`SurfaceFlinger`通信, 这里调用了`mClient->createSurface()`,这里传入了一个`gbp(IGraphicBufferProducer)`对象，这个对象很重要，因为后面在UI渲染时，渲染完的一帧，实际上会添加到这个队列中。因此下面我们在跟源码时要仔细注意这个对象。除此之外还有`handle`。这两个对象在`mClient->createSurface()`完成创建后，会用来创建`SurfaceControl`对象。

>gui/Clinet.cpp
```
status_t Client::createSurface(...)
{
    ...
    //gbp 直接透传到了SurfaceFlinger
    return mFlinger->createLayer(name, this, w, h, format, flags, windowType, ownerUid, handle, gbp, &parent);
}
```

即调用`SurfaceFlinger`创建了一个`Layer`:

```
status_t SurfaceFlinger::createLayer(const String8& name,const sp<Client>& client...)
{
    status_t result = NO_ERROR;
    sp<Layer> layer; //将要创建的layer
    switch (flags & ISurfaceComposerClient::eFXSurfaceMask) {
        case ISurfaceComposerClient::eFXSurfaceNormal:
            result = createBufferLayer(client,
                    uniqueName, w, h, flags, format,
                    handle, gbp, &layer); // 注意gbp，这时候还没有构造呢！
            break;
            ... //Layer 分为好几种，这里不全部列出
    }
    ...
    result = addClientLayer(client, *handle, *gbp, layer, *parent);  //这个layer和client相关联, 添加到Client的mLayers集合中。
    ...
    return result;
}
```

从`SurfaceFlinger.createLayer()`方法可以看出`Layer`分为好几种。我们这里只对普通的`Layer`做一下分析，看`createBufferLayer()`:

```
status_t SurfaceFlinger::createBufferLayer(const sp<Client>& client... sp<Layer>* outLayer)
{
    ...
    sp<BufferLayer> layer = new BufferLayer(this, client, name, w, h, flags);
    status_t err = layer->setBuffers(w, h, format, flags);  //设置layer的宽高
    if (err == NO_ERROR) {
        *handle = layer->getHandle(); //创建handle
        *gbp = layer->getProducer(); //创建 gbp IGraphicBufferProducer
        *outLayer = layer; //把新建的layer的指针拷贝给outLayer,这样outLayer就指向了新建的BufferLayer
    }
    return err;
}
```

由`layer->getProducer()`可以看出，一个`IGraphicBufferProducer`是属于一个`Layer`的,下面我们就来看一下它的创建过程。

### IGraphicBufferProducer(gbp)的创建

>BufferLayer.cpp
```
sp<IGraphicBufferProducer> BufferLayer::getProducer() const {
    return mProducer;
}
```
即`mProducer`其实是`Layer`的成员变量，它的创建时机是`Layer`第一次被使用时:

```
void BufferLayer::onFirstRef() {
    ...
    BufferQueue::createBufferQueue(&producer, &consumer, true); //susionw  SurfaceFlinger构造一个 gpb buffer
    mProducer = new MonitoredProducer(producer, mFlinger, this);
    ...
}
```

所以`mProducer`的实例是`MonitoredProducer`,但其实它只是一个装饰类，它实际功能都委托给构造它的参数`producer`:

>BufferQueue.cpp
```
void BufferQueue::createBufferQueue(sp<IGraphicBufferProducer>* outProducer,
    ...
    sp<IGraphicBufferProducer> producer(new BufferQueueProducer(core, consumerIsSurfaceFlinger));
    sp<IGraphicBufferConsumer> consumer(new BufferQueueConsumer(core)); //注意这个consumer
    ...
    *outProducer = producer;
    *outConsumer = consumer;
}
```

所以实际实现`mProducer`的工作的`queueProducer`是`BufferQueueProducer`。

所以上面`SurfaceControl.nativeCreate()`所做的工作就是创建了一个`SurfaceControl`,并在让`SurfaceFlinger`创建了一个对应的`Layer`，`Layer`中有一个`IGraphicBufferProducer`,它的实例是`BufferQueueProducer`。

### 获取Surface

上面已经创建了一个`SurfaceControl`,那么怎么获取一个`Surface`呢？回顾一个`WindowManagerService.createSurfaceControl()`

```
private int createSurfaceControl(Surface outSurface, int result, WindowState win,WindowStateAnimator winAnimator) {
    ...
    surfaceController = winAnimator.createSurfaceLocked(win.mAttrs.type, win.mOwnerUid);
    ...
    surfaceController.getSurface(outSurface); //outSurface
}
```

所有我们看一下`surfaceController.getSurface(outSurface)`, `surfaceController`是`WindowSurfaceController`的实例:

```
void getSurface(Surface outSurface) {
    outSurface.copyFrom(mSurfaceControl);
}

public void copyFrom(SurfaceControl other) {
    ...
    long surfaceControlPtr = other.mNativeObject;
    ...
    long newNativeObject = nativeGetFromSurfaceControl(surfaceControlPtr);
    ...
}
```

即`Surface.copyFrom()`方法调用`nativeGetFromSurfaceControl()`来获取一个指针，这个指针是根据前面创建的`SurfaceControl`的指针来寻找的，即传入的参数`surfaceControlPtr`

```
static jlong nativeGetFromSurfaceControl(JNIEnv* env, jclass clazz, jlong surfaceControlNativeObj) {
    sp<SurfaceControl> ctrl(reinterpret_cast<SurfaceControl *>(surfaceControlNativeObj)); //把java指针转化内native指针
    sp<Surface> surface(ctrl->getSurface()); //直接构造一个Surface，指向 ctrl->getSurface()
    if (surface != NULL) {
        surface->incStrong(&sRefBaseOwner); //强引用
    }
    return reinterpret_cast<jlong>(surface.get());
}
```

所以看一下`ctrl->getSurface()`:

>SurfaceControl.cpp
```
sp<Surface> SurfaceControl::getSurface() const
{
    Mutex::Autolock _l(mLock);
    if (mSurfaceData == 0) {
        return generateSurfaceLocked();
    }
    return mSurfaceData;
}

sp<Surface> SurfaceControl::generateSurfaceLocked() const
{
    mSurfaceData = new Surface(mGraphicBufferProducer, false); //这个mGraphicBufferProducer其实就是上面分析的BufferQueueProducer
    return mSurfaceData;
}
```

即直接new了一个`nativie的Surface`返回给java层，`java层的Surface`指向的就是`native层的Surface`。

## UI渲染

经过上面我们阅读`ViewRootImpl.relayoutWindow()`方法我们知道，在java层有一个`SurfaceControl`对象来控制`Surface`的使用，`relayoutWindow()`方法会导致native构造一个`SurfaceControl`，并且`SurfaceFlinger`会构造一个`BufferLayer`，并且native也会构造一个`natived的Surface对象`，`java层的Surface`实际指向的是`native层的Surface`。

到目前为止，看样相关UI渲染的对象都已经准备完毕了，那么接下来看一下UI是如何渲染的, 即看一下`performDraw()`相关的逻辑,`performDraw`最终会调用到`drawSoftware()`:

```
private boolean drawSoftware(Surface surface, AttachInfo attachInfo, int xoff, int yoff,boolean scalingRequired, Rect dirty, Rect surfaceInsets) {
    // Draw with software renderer.
    final Canvas canvas;
    ...
    canvas = mSurface.lockCanvas(dirty); 
    ...
    mView.draw(canvas); 
    ...
    surface.unlockCanvasAndPost(canvas); 
}
```

`mView`在这里是`ViewRootImpl`的根View。`mView.draw(canvas)`会利用`canvas`来绘制整个`view tree`。`canvas`的绘制流程不是本文所探究的点，我们来看一下`surface.unlockCanvasAndPost(canvas)`,这个方法其实是把`canvas`所绘制的内容渲染到`surface`上,这个方法最终会调用到native方法`nativeUnlockCanvasAndPost()`:

>android_view_Surface.cpp
```
//这个nativeObject其实指向SurfaceControl， canvasObj指向native的Canvas对象
static void nativeUnlockCanvasAndPost(JNIEnv* env, jclass clazz,jlong nativeObject, jobject canvasObj) { 
    ...
    // detach the canvas from the surface
    Canvas* nativeCanvas = GraphicsJNI::getNativeCanvas(env, canvasObj);  // 把java canvas指针转化为native 指针
    nativeCanvas->setBitmap(SkBitmap());

    // unlock surface
    status_t err = surface->unlockAndPost();
}
```

即调用`surface->unlockAndPost`:

>Sruface.cpp
```
status_t Surface::unlockAndPost()
{
    if (mLockedBuffer == 0) {
        ALOGE("Surface::unlockAndPost failed, no locked buffer");
        return INVALID_OPERATION;
    }

    int fd = -1;
    status_t err = mLockedBuffer->unlockAsync(&fd);

    err = queueBuffer(mLockedBuffer.get(), fd); //canvas 在绘制内容时内容会绘制到 mLockedBuffer 

    mPostedBuffer = mLockedBuffer;
    mLockedBuffer = 0;
    return err;
}
```

这里`queueBuffer()`是把一个绘制好的`buffer`插入到待绘制序列:

```
int Surface::queueBuffer(android_native_buffer_t* buffer, int fenceFd) {
    ...
    int i = getSlotFromBufferLocked(buffer);
    ...
    IGraphicBufferProducer::QueueBufferOutput output;
    IGraphicBufferProducer::QueueBufferInput input(timestamp, isAutoTimestamp,
            static_cast<android_dataspace>(mDataSpace), crop, mScalingMode,
            mTransform ^ mStickyTransform, fence, mStickyTransform,
            mEnableFrameTimestamps);

    ...
    status_t err = mGraphicBufferProducer->queueBuffer(i, input, &output); //susionw 把 i 放进buffer
    ...
    mQueueBufferCondition.broadcast();
    return err;
}
```

其实核心是调用了`mGraphicBufferProducer->queueBuffer(i, input, &output)`。上面我们已经分析了，我们可以把`mGraphicBufferProducer`当做`BufferQueueProducer`的实例:

>BufferQueueProducer.cpp
```
status_t BufferQueueProducer::queueBuffer(int slot,const QueueBufferInput &input, QueueBufferOutput *output) { 
    sp<IConsumerListener> frameAvailableListener;
    sp<IConsumerListener> frameReplacedListener;
    BufferItem item; //item其实就有一个待渲染的帧

    ...下面就是对item的一系列赋值操作
    
    ...
    if (frameAvailableListener != NULL) {
        frameAvailableListener->onFrameAvailable(item); //susionw item是一个frame，准备完毕，要通知外界
    } else if (frameReplacedListener != NULL) {
        frameReplacedListener->onFrameReplaced(item);
    }

    addAndGetFrameTimestamps(&newFrameEventsEntry,etFrameTimestamps ? &output->frameTimestamps : nullptr);

    return NO_ERROR;
}
```

这里这个`frameAvailableListener`是什么呢？有兴趣的同学可以去跟一下, 不过最终回到`BufferLayer.onFrameAvailable()`

>BufferLayer.cpp
```
// ---------------------------------------------------------------------------
// Interface implementation for SurfaceFlingerConsumer::ContentsChangedListener
// ---------------------------------------------------------------------------

void BufferLayer::onFrameAvailable(const BufferItem& item) {
    ...
    mFlinger->signalLayerUpdate();
}
```

这个方法直接调用了`mFlinger->signalLayerUpdate()`,看样是要让`SurfaceFlinger`来渲染了:

>SurfaceFlinger.cpp
```
void SurfaceFlinger::signalLayerUpdate() {
    mEventQueue->invalidate();
}
```

至于`SurfaceFlinger`是如何渲染的，本文就不继续追踪了。











