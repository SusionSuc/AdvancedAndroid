
>本文是[Android视图层源码分析](https://github.com/SusionSuc/AdvancedAndroid/blob/master/AndroidFramework%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90/Android%E8%A7%86%E5%9B%BE%E5%B1%82%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90/README.md)系列第4篇文章，主要是对前几篇文章做一个总结，理解Android视图的主要组成部分和相互之间的工作逻辑。本文内容是基于[Google Android Repo](https://android.googlesource.com/)中的较新的源码分析而得来的。

先来看一张`Android`视图层主要工作原理图,本文的内容就是逐一解释下图各个模块的作用:

![](picture/Android视图层主要工作原理图.png)


## PhoneWindow

它是`Framework`层提供的唯一`Window`的实例。它是`Android`提供的很多UI组件的UI承载者,比如`Activity/Dialog`, 它提供了一些列友好的API来屏蔽内部的实现细节，比如:`setContentView(layoutResID)`。
它内部自带一个`DecorView`作为`View Tree`的根容器。这个`DecorView`可以通过`WindowManager`来添加到`WindowManagerService`。


## WindowManager

可以说它是和`PhoneWindow`绑定在一块的。通过这个类的`addView()`方法我们可以添加一个`抽象Window`,这个`抽象Window`和`PhoneWindow`不同。可以把`抽象Window`简单理解为`ViewRootImpl`。`WindowManager`的实现类是`WindowManagerImpl`,不过实际的功能是委托给`WindowManagerGlobal`来实现的。`WindowManagerGlobal`管理着一个应用程序的所有`ViewRootImpl`。

## ViewRootImpl

通过`WindowManager`来add一个View时就会创建一个`ViewRootImpl`。它是`View Tree`的管理者，负责调控`View Tree`的测量、布局和绘制。对于`View Tree`的刷新是通过`Choreographer`来控制的,即`Choreographer.postCallback(mTraversalRunnable)`。

它不仅和`Surface`是一对一的关系，它还具有接收用户输入事件的能力。它可以和`WindowManagerService`通信，来显示它所管理的`View Tree`, 其实它就对应着`WindowManagerService`中的一个`WindowState`。

## Choreographer

它是一个单例对象，它可以接收VSync信号。ViewRootImpl通过它来按照系统的部署进行`View Tree`的测量、布局和绘制。

## Surface

它实际上对应着`SurfaceFlinger`中的`Layer`。可以把它理解为一个画布，在它上面做画的方式有多种，较长使用的是`Canvas`,它和`Canvas`是一对一的关系。`Canvas`的绘制数据可以画在`Surface`上，经过一系列的处理，最终由`SurfaceFlinger`展示。

## Canvas

它也可以被比喻为一个画布，不过它内部有一个`Bitmap`, 通过`drawXXX`方法所绘制的内容，都会作为它的`Bitmap`的内容。

## IWindowSession

和`WindowManagerGlobal`一样，一个进程只会存在一个，它是应用程序与`WindowManagerService`交互的Binder。

## WindowState

`WindowState`用于在`WindowManagerService`中代表一个`Window`，它含有一个窗口的所有属性，它和`ViewRootImpl`是对应的。它被保存在`WindowManagerService`的`mWindowMap`集合中。`mWindowMap`是整个系统所有窗口的一个全集。

## WindowToken

它将属于同一个应用程序组件的窗口组织在一起。在`WindowManagerService`对窗口管理的过程中，用`WindowToken`代表一个应用组件。例如在进行窗口Z-Order排序时,属于同一个`WindowToken`的窗口会被安排在一起。一个token下会有多个`WindowState`:

>WindowManagerService.addWindow()
```
win.mToken.addWindow(win);//一个token下会有多个win state
```

## SurfaceControl

可以简单的把它理解为`WindowManagerService`中的`Surface`的管理者。它和`Surface`是一对一的关系，构建`SurfaceControl`的同时就会构造`Surface`。它可以通过`SurfaceComposerClient`来与`SurfaceFlinger`交流。比如请求`SurfaceFlinger`创建`Layer(Surface)`

## SurfaceComposerClient

这个对象也是进程唯一的，一个应用只有一个。它负责与`SurfaceFlinger`建立连接，并维护跨进程通信对象`Client`。它是`SurfaceControl`的创建者。

## Client

它是一个`Binder`,`SurfaceComposerClient`可以通过它来与`SurfaceFlinger`沟通。通过它可以使`SurfaceFlinger`创建一个`Layer`。它也维护着一个应用程序所有的`Layer`。

## SurfaceFlinger

它负责`Layer`的合成和渲染。

## Layer

它是一个可被`SurfaceFlinger`渲染的单元，它有一个`BufferQueueProducer`,里面维护着很多可以被渲染的`GraphicBuffer`,这个buffer可能被渲染完毕，也可能处于待渲染状态。


