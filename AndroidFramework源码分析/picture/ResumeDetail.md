# 性能调优

## 优化布局层级

- 使用 <merge> 标签来优化层级。为什么？主要是由于RecyclerView中装载的都是View，布局是inflate出来的。设计为View是为了保证复用性和封装性。也方便单元测试
- 对于复杂布局使用<LinearLayout>, 对于比较简单的布局使用<RelativeLayoyt>
- 减少布局层级，优化无用的background，避免overdraw

## 优化卡顿

### 搜索入场动画的优化， intent传参到内存传参。 动画结束之后再弹起键盘，然后加载网络数据。 

1. 为什么要实时截取图片？  （会引起轻微卡顿）

> 静态图片对于不同的手机存在适配问题， 只截取一次的话，由于状态栏UI样式会不断变化，所以每次都截取是无bug的做法。

2. 截取的图片的处理

>转为 RGB565， 尽可能减小图片的大小。

3. 为什么不在前一个页面截取

>其实就是代码写在那个组的问题， 写在搜索中，封装比较好，易于维护，不会有其他组操作的风险。

4. 后续可以对图片的裁剪时机做优化，这样可以大大加大搜索页面启动速度

### RecylerView Item 的优化

核心是 : bind data 时不做耗时操作

1. 不每次都取KV中flag，flag在搜索初始化时全部初始化起来
2. 不做获取网络状态的操作， 比如判断是否是wifi，播放GIF。 网络的状态在一个比较恰当的时机获取。 （presenter加载这一次数据时）
3. view构造时就设置好listner

### ViewPager滑动时的卡顿问题， UI事件一步一步的来做

> 解决核心点: ViewPager在滑动的同时不要加载网络数据，当滑动完成后再开始网络数据的加载。

在`onPageScrollStateChanged`中，判断是否完成了页面切换的动作，然后发起网络数据加载

这类问题解决的核心就是:**不要在主线程中同时做多件事，对于UI事件应该一步一步的来做**

- 比如滚动到顶部的问题: 先折起工具栏，然后再把RecylcerView滚动到顶部

## 其他的优化

1. RecyclerView的预加载。
2. 扩大RecyclerView的额外布局区域等。

# 框架设计

MVP + ViewModel/LiveData/LifeCycle 。 整套设计遵循 数据驱动View的思想

## Lifecycle监听Ac生命周期的大致实现

>AppcompactActivity默认就是一个LifeOwner。

> 它是生命周期事件派发的实现大致是 : 在Application层次注册了一个ActivityLifecycleCallback, 为Ac填了一个(ReportFragment)。利用这个ReportFragment来报告Ac的生命周期的事件。如果Ac依附的Ac需要排放生命周期事件的话，就会派发生命周期事件。

## 数据保存在ViewModel中，Ac在异常销毁时数据不会丢失，它跨Ac生命周期的大致实现

>实现方式类似于Lifecycle。也是在Ac上依附一个Fragment(HolderFragment)。这个fragment保存了ViewModel对象。 HolderFragment设置了`setRetainInstance()`,它可以保证`HolderFragment`在Activiy重新创建时一直存在。它被保存在fragment manager。 HolderFragment里面有一个ViewModelStore。这个对象管理着多个ViewModel。

>在Ac异常销毁时，在Ac状态恢复回调中，根据数据恢复UI状态。

## LiveData 来保存数据， 数据是可被观察的, live data 的通知机制会跟随lifecycle。

>livedata在被观察时需要接受一个LifeOwner。它会依据LifeOwner当前的生命周期状态来决定是否通知数据更新。

## 新的 PV 接口

Presenter继承自LifecycleObserver, 可以感知Ac的生命周期事件，管理ViewModel的网络请求。所有的View都必须依赖抽象的Presenter接口。View可以通过dispatch一个Action来触发Presenter的逻辑。可以通过queryStatus来获取数据的状态。这样可以保证的可测试性和复用性。

>可测试性与可复用性 : RecyclerView中View都继承自View，可以直接new。 View依赖于最抽象的Presenter，实例mock起来比较简单。

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





