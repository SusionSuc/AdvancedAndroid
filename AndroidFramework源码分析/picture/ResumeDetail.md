

# 重构
为什么要重构:

1. 原来的结构是两个AC，页面跳转、转场动画实现起来都不是很流畅  
2. 采用传统的MVC，一个View里面包含UI更新、业务逻辑、网络获取数据，十分臃肿 -
3. 笔记结果页实现方式不是很好，头部卡片样式变化，吸顶操作等等。 -> 使用一个RecyclerView实现，数据刷新接口比较简单,页面支持灵活多变 。
4. bug较多，打点逻辑遍布各处，难以梳理。 -> 打点逻辑全部放在View层 。

## 如何重构

### 使用一个AC， View来承载具体UI。(不使用Fragment)。 页面跳转和转场动画实现起来都比较方便和流畅。

## 重构为细粒度的MVP。完全遵循数据驱动UI的思想

## 引入ViewModel/LiveData/LifeCycle。

- ViewModel中的数据可以在Ac被销毁时不被销毁，Ac在异常销毁时可以快速恢复数据
- LiveData可以保证Model在通知UI改变时，View不会处于异常的生命周期中
- LifeCycle可以保证Presenter(继承自LifecycleObserver)观察到Ac的生命周期，及时通知给ViewModel,ViewModel做网络加载的释放，避免RxJava的内存泄漏

## 列表页使用RecyclerView实现，支持多变的页面类型，支持复杂的吸顶操作。

- RecyclerView中的ItemView都是继承自View， 这样可以保证View的独立性和复用性。

> AppcompactActivity默认就是一个LifeOwner,它是生命周期事件派发的实现大致是 : 在Application层次注册了一个ActivityLifecycleCallback, 为Ac填了一个(ReportFragment)。利用这个ReportFragment来报告Ac的生命周期的事件。如果Ac依附的Ac需要排放生命周期事件的话，就会派发生命周期事件。

>ViewModel的实现方式类似于Lifecycle。也是在Ac上依附一个Fragment(HolderFragment)。这个fragment保存了ViewModel对象。 HolderFragment设置了`setRetainInstance()`,它可以保证`HolderFragment`在Activiy重新创建时一直存在。它被保存在fragment manager。 HolderFragment里面有一个ViewModelStore。这个对象管理着多个ViewModel。

>Presenter继承自LifecycleObserver, 可以感知Ac的生命周期事件，管理ViewModel的网络请求。所有的View都必须依赖抽象的Presenter接口。View可以通过dispatch一个Action来触发Presenter的逻辑。可以通过queryStatus来获取数据的状态。这样可以保证的可测试性和复用性。

>livedata在被观察时需要接受一个LifeOwner。它会依据LifeOwner当前的生命周期状态来决定是否通知数据更新。

# 性能调优

## 优化布局层级

- 减少布局层级，优化无用的background，避免overdraw
- 使用 <merge> 标签来优化层级。为什么？主要是由于RecyclerView中装载的都是View，布局是inflate出来的。因此会产生一层无用的父节点
- 对于复杂布局使用<LinearLayout>, 对于比较简单的布局使用<RelativeLayoyt>
- 对于一些很简单的View，不要写XML文件，减少inflate的时间。

## 优化卡顿 (减少ItemView.bindData()所消耗的时间)

- 不要在bindData时设置listener， listener在View被构造时就设置。
- 减少IO(从SharePrefence获取数据)、网络状态的读取操作，这些比较耗时。把这些状态在页面启动时异步的全部读出来
- 加大RecyclerView缓存，提前加载下一页数据，比如当仅剩6个Item可见时就加载下一页数据
- 使用notifyXXX()来更新数据。 

## 减少UI并发更新带来的卡顿

- 比如ViewPage页面切换时， 新页面的刷新不要和ViewPage滚动事件同步，
- 转场动画时，动画不要和键盘弹起、UI更新一块并发

## 减少内存占用

- 页面懒加载
- 减少Entites对象的属性，这样在网路解析时Gson的转化数据会加快。 (主要是因为随着版本的迭代一些废弃的属性没有及时删除)

## 预加载图片

- 在Model层拿到数据后，就使用Fresco开始加载数据。这样Model层解析数据-UI的ImageView的图片加载大约几百毫秒的时间，就都用在了图片加载上


# 源码阅读

## RecyclerView

1. 刷新机制
2. 复用机制

## Fresco

1. 框架结构
2. UI显示原理
3. 缓存的实现

## Framework源码

1. Android 中的UI显示原理 : Window, WMS, Surface, ViewRootImpl

2. View事件分发机制等


## Handler

1. 不可以在子线程直接创建Handler， Handler创建时的线程必须要有Looper， 不过在new handler时可以直接指定Looper，拿主线程的Looper就OK了。


## Fragment

1. 搭建UI的小容器，实际UI的还是由View来完成的。 Fragment相比于View具有完整的生命周期，不过他的生命周期是依赖于Ac的。

### 个人感觉fragment不好的地方

1. 没有View灵活。 比如构造方法的坑， 必须要指定默认构造方法，否则在Ac异常销毁重建的时候就会崩溃
2. 整个添加过程比较麻烦， 设计到fragmentmanager
3. 动画实现也比较麻烦，要依靠一些系统级API， 为什么不直接拿View来做动画。
4. 生命周期比较鸡肋， 比如在ViewPager来回切换时， Fragment的生命周期表现就差强人意
5. 使用起来崩溃比较多，会有许多坑。 比如: getAc()会为空，


个人感觉View唯一不如Fragment的地方就是生命周期。不过大部分View都对生命周期需要没有那么苛刻。一般用来代替Fg的View，都是直接在Ac的管理下的，要什么生命周期直接加一个方法给它就是。



## 项目中遇到过的问题


1. IllegalArgumentException: Scrapped or attached views may not be recycled. isScrap:false

阅读RecyclerView源码。发现是数据更新，没有及时notify()


2.除此之外还有Fresco的一些问题

如何预加载图片？ 
 
如何监听图片加载流程中的每一步？
 
View在展示是如何确定当前加载的图片是否已经被加载完毕？（打点，统计到后台）

-> Freco提供了判断一个Uri是否被缓存在 内存， or 磁盘。  如果这两个都不在，那么这个图片肯定还没有被加载完成。  但是是否在磁盘其实是一个耗时操作




## 面试遇到的问题

### 四大组件运行之前都要创建application

### fresco为什么要设置固定宽高 ：https://www.fresco-cn.org/docs/using-drawees-xml.html



### 网络接口多长时间返回

### JVM内存分区

### apk体积优化

1. 清除无用资源， 使用矢量图
2. 加大混淆
3. 动态下发so、rn资源等

### AC的启动流程

### application thread的作用

### 应用性能优化

### ARouter启动时的ANR问题？

### 垃圾回收算法

#### 统计一个对象是否要被回收

1. 引用计数法，当一个对象的引用计数为0时就会被回收。无法检测出循环引用
2. 根搜索算法。从一个对象(GC ROOT)开始遍历其引用到的对象。将没有被引用到的节点视为无用的节点。

#### 清除无用的对象

1. 标记-清除算法。  遍历一遍，统计出有用和无用的对象，对无用的对象直接清理。 会造成内存碎片。 内存碎片过多会造成大对象无法分配
2. 标记-整理算法。  在标记-清除算法的基础上对对象做了移动的操作，不会造成内存碎片的参数。
3. 分代算法。 不同生命周期的对象应该采用不同的清理算法。一般分为年轻代/老年代/持久代。

### JVM内存分区

https://www.jianshu.com/p/a60d6ef0771b

- 程序计数器

用来记录当前线程执行程序的位置。

- java栈

线程私有，与线程生命周期绑定。每个方法都会创建一个栈帧，一个栈帧包含局部变量表、操作数栈、动态连接、方法出口。

- 本地方法栈

线程私有，用于运行native代码

- java堆、native堆

被所有线程共享。堆又分为新生代和老年带。对象经过几次回收后会从新生代移动到老年代。对于这两个区域又采用不同的垃圾回收算法。比如标记-清除、标记-整理。

- 方法区

被虚拟机加载的类信息、常量、静态变量等。这一代也会进行垃圾回收。比如回收常量。常量池用于存放编译期生成的各种字节码和符号引用，常量池具有一定的动态性，里面可以存放编译期生成的常量；运行期间的常量也可以添加进入常量池中，比如string的intern()方法。


#### 垃圾回收器

垃圾回收器有串行和并行之分。hotspot虚拟机对于堆中的不同代采用了不同的垃圾回收器。比如G1垃圾回收器

### view的整个布局过程

measure/layout/draw

- measure :根据父View的measureSpec，和自己的布局参数以及内容来确定自己应该多大。这对于View/ViewGroup来说是不同的

>ViewGroup: 以LinearLayout为例(vertical)，它需要测量出全部的子View的高度，然根据measureSpec来计算出自己的高度，从而决定自己measure的大小
>View : 它需要根据父布局传给它的measureSpec和自己的一些特性来决定自己measure的大小。

specmode ： UN_SPECIFIED, AT_MOST(最大), EXACTLY。

- layout : 确定自身摆放的位置， 这个方法对于ViewGroup比较重要

一般Viewgroup需要重写这个方法，来根据自己的特性来摆放子View的位置。摆放子View时会给子View指定left，right，top，bottom的位置，这些参数是父View在去除margin，padding后得出的。

- draw ： 把View的内容绘制到Canvas上。

一般view的绘制顺序如下： 绘制背景、绘制自己、绘制chidlren、绘制装饰(比如滚动条)

### 自定义View

一般业务开发中:

1. 会自定义一个View扩充其功能，比如hack掉ViewPager的滚动特地奥
2. 自定义一个ViewGroup来实现View的复用， 代码的抽离
3. 实现特定的Layout方式，需要继承ViewGroup，定义自己的measure和layout。 区别于系统常见的ViewGroup
4. 继承View重写onDrawe方法。需要支持wrap_content。不支持的话，这个属性有可能自动对应到match_parent



### Android中的动画

- 关于View滚动的实现

>- scrollTo/scrollBy : 滚动的是View的内容。 即View在绘制内容时会加上这个偏移量。
>- 属性动画来改变translationX/translationY: x = left + translationX。 所以通过这个改变的是View的绝对位置。主要适合没有交互的View和实现复杂的动画效果。
>- 改变布局参数: 动态更改 layoutparamas的参数，比如width， margin。 它比较适用于有交互的View。
>- 当然也可以使用Scroller来实现弹性滑动。它内部有一个类似插值器的概念，可以根据时间流逝的百分比来算出scrollX和srollY的值。


- View动画
 
帧动画/View动画/属性动画

>帧动画原理

一张张图片连续播放  AnimationDrawable + <animation-list> 。 由于同时存在的图片较多，容易引发OOM。所以帧动画的图片不能太大。

>View动画
移动的影像。

>属性动画: 改变View的属性

真正改变View的位置。alph、scale、translateX。  x = left + translateX 。 

ObjectAnimation很容易扩展，因为他是通过反射调用的对象的方法，进而来动态改变对象的属性。 因此你只要把你想改变的属性传给它，它就可以帮你实现你这个属性动画试的更改。

>插值器和估值器

插值器的作用是根据时间流逝的百分比来计算出当前属性改变的百分比。 估值器的作用是根据当前属性的百分比来计算改变后的属性值。 所以使用顺序是 插值器 -> 估值器。

使用动画时可以开启硬件加速。为什么硬件加速动画会流畅？ ->硬件加速的原理 : https://blog.csdn.net/qian520ao/article/details/81144167#_224


# 并发问题

可以使用`Executors`来创建线程，不过一般推荐使用`ThreadPoolExecutor`来创建线程池，它可以对`BlockQueue`来指定容量。

## volatile

>线程的工作内存与主内存，多个线程并发读写一个共享变量的时候，有可能某个线程修改了变量的值，但是其他线程看不到！也就是对其他线程不可见！

volatile保证，任何一个线程修改了变量值，其他线程立马就可以看见了！这就是所谓的volatile保证了可见性的工作原理！

## 原子性问题的解决

可以使用 synchronized、ReentrantLock、Atomic原子类


`synchronized` : 它属于互斥锁，任何时候只允许一个线程的读写操作，其他线程必须等待。 它使用(.class)和静态方法使用的是类锁。 它使用对象和this，使用的是对象锁。

`ReentrantReadWriteLock` : 允许多个线程获得读锁，但只允许一个线程获得写锁，效率相对较高一些



## 实现一个读写锁(可以同时读，但不可以同时写)

## 实现一个线程并发的观察者模式

## 无序集合获取第11大的元素的index

## Url Log (addUrl， 获取最常用的5个url， 获取5个最近的URL)

## kotlin协程


## RxJava线程切换原理

- RxJava : Observable.create()， 整个被订阅流程的原理

Observable 接收一个被subscribe时实现了subscribe方法的对象。 并传给这个对象一个ObservableEmitter,调用这个对象的onNext/onError等方法时实际上会调用到Observer对象。

- 线程切换原理

在指定subscribeOn()时，会对Observable再做一次包装，包装为`ObservableSubscribeOn`, 这个对象内部使用传入的Scheduler来对`subscribe`做回调，即会运行在`Scheduler`指定的线程。`Scheduler`指定它会运行在Worker中。 不同的`Sccheduler`创建不同的Worker。IoScheduler内部是一个线程池，池中只有一个线程。subscribe会回调在这个线程中。

## RxJava连续subscribe最终会运行在哪个线程

运行在第一个subscribeOn的线程

## map 和 flatMap 有什么区别

map 可以将被观察者发送的数据类型转变成其他的类型

flatMap 可以将事件序列中的元素进行整合加工，返回一个新的被观察者。


## Fresco如何引入到一个老的项目中

## Fresco框架的结构

- 优势: 支持多种UI显示特性，可以灵活配置，可以灵活监听图片加载流程中的事件， UI层设计的很灵活

>自定义解码器、网络加载器、缓存配置、图片加载配置。 设计listener， 可以监听到每一步图片处理

>预加载图片到缓存，判断缓存中是否存在图片

>GenericDraweeHierarchy, 普通View只需引入 DraweeHolder就可以



- 劣势: 包比较大、Bitmap开发人员不能灵活使用，比如不能随便传到其他的界面，必须在Fresco规定的范围内使用

## 对于Fresco的最大印象，适用在哪里？

## LifeCycle的大致原理

实现原理是在Ac上设置了一个Fragment， Fragment设置了retainInstance属性。这个fragment用来派发生命周期事件。 (通过在Application中注册了LifeCycle来观察LifeOwner的生命周期)LifeOwner的事件会到这个fragment， 它会把事件告诉LifeOwner。

## 为什么要去阅读VirtualApk，与其他插件化框架有什么区别


## 性能优化都做了什么事？ 用了什么工具？ 怎么检查到的这些问题

## JVM内存分区， 垃圾回收算法， GC-root对象有哪些

## Ac的启动流程

## ARouter的大致实现原理

## 如何保证线程同步

## 有哪些跨进程通信的方式

## Binder的实现原理

## 内存泄漏如何检测？ 有哪些内存方面的优化

## RecyclerView源码阅读后在项目中有哪些使用

## MVP 、MVVM。 为什么不选用MVVM

## LeakCanary/Gt的原理？

