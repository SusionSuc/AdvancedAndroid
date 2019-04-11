
>本文主要是想表达一下在Android UI开发中我对`View`的看法,个人经验有限,有什么问题欢迎一块讨论。

如果说`Activity`是Android提供的页面容器的话，那`View`就是最基础的UI组件(有点是废话)。什么意思呢？我认为绝大部分UI开发工作都可以使用View来完成，下文就结合我工作中的一些实际case来谈一下`View`的使用。(当然不稳不是讲怎么自定义`View`)

# Fragment与View

`Google`推荐使用`Fragment`来在`Activity`中搭建碎片化UI，但我感觉的完全可以使用`View`来代替`Fragment`完成这个功能，并且这样的代码简单易懂可维护并且bug也少。

为什么不推荐使用`Fragment`呢？可以看一下这篇文章: [Square：从今天开始抛弃Fragment吧!](http://www.jcodecraeer.com/a/anzhuokaifa/androidkaifa/2015/0605/2996.html)

`Fragment`都有哪些坑呢？下面这两篇文章了解一下:

[Android实战技巧：Fragment的那些坑](http://toughcoder.net/blog/2015/04/30/android-fragment-the-bad-parts/)

[Fragment全解析系列（一）：那些年踩过的坑](https://www.jianshu.com/p/d9143a92ad94)

当然我也是踩过`Fragment`很多坑的,比如在使用`ViewPager + Fragment + LifeCycle`这种架构时,`ViewPager`切换`Fragment`时，`LifeCycle`根本没做通知。

## View相较于Fragment的优势

>当然都是一些个人观点

- `View`复用性更强,不像`Fragment`那样需要依赖于`FrameLayout`。其实`Fragment`的UI显示逻辑也是交给View的呀(有点是废话)。
- `View`使用起来更灵活,你可以对他进行各种操作，比如remove/add、嵌套在任何地方等等，而且回调写起来更扁平。
- 使用`View`不需要理会复杂的生命周期，其实你大部分情况下`View的生命周期`已经足够你使用了，大不了写个方法让`Activity`来回调就可以了。
- 都是用来显示UI，`View`相较于`Fragment`更直接更纯粹,更轻量级,当然bug更少。

**我们只需要使用`View`创建响应式UI，实现回退栈以及屏幕事件的处理，不用`Fragment`也能满足实际开发的需求。**《出自[Square：从今天开始抛弃Fragment吧!](http://www.jcodecraeer.com/a/anzhuokaifa/androidkaifa/2015/0605/2996.html)》


# View使用实战

下面从几个不同的case来讲一下在实际场景中`View`的使用。

## 使用View来代替Fragment

很简单，只需要自定义一个`ViewGroup`就Ok了。不过对于一些逻辑复杂的页面我们会引入`MVP`，那么如何让`Presenter`来感知生命周期事件呢？在使用`Fragment`时，我们可以直接感受生命周期，对于`View`的话我们可以引入`LifeCycle`，即`View`感知`Activity`的生命周期，其实`Fragment`的生命周期也是跟着`Activity`走的呀。

### View的Presenter对生命周期的感知

假设项目引入了`LifeCycle`， 那么可以这样设计:

>Presenter实现LifecycleObserver
```
class DemoPresenter(demoPage:DemoPageProtocol) : LifecycleObserver {
    private val model...

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun destroy() {
        model.clearDisposable() //释放model中的网络资源
    }
}
```

>View把Presenter注册到LifeCycle中
```
class DemoPage(context: Context) : LinearLayout(context), DemoPageProtocol{

    private val presenter: DemoPresenter  by lazy { DemoPresenter(this) }

    init {
        LayoutInflater.from(context).inflate(R.layout.demo_page, this)  //这里可以使用merge来消除冗余的父节点
        (context as AppCompatActivity).lifecycle.addObserver(presenter)  
    }

}
```

这里的强转其实是没有问题的，我们使用的`Activity`基本都继承自`AppCompatActivity`。（当然你要知道你在写什么）


## RecyclerView中的View

`RecyclerView`是使用频率非常高的一个控件，我个人比较推荐的一种写法是:**直接写`View`,`View`到`ViewHolder`的映射交给`Adapter`来完成。**具体封装方式可以参考下面这篇文章:

[RecyclerView的封装](https://github.com/SusionSuc/AdvancedAndroid/blob/master/AndroidFramework%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90/recyclerview/RecyclerView%E7%9A%84%E4%BD%BF%E7%94%A8%E6%80%BB%E7%BB%93%E4%BB%A5%E5%8F%8A%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88.md)


### View中可以做一些简单的网络请求

`RecyclerView`中的`View`有时是会含有一些简单的网络事件的比如点赞、关注等等。我一般是直接写在`View`中，因为我感觉这样写起来更直观。但是网路请求在什么时候释放呢？我感觉可以在`View onDetachedFromWindow`时把这些网络事件释放掉:

```
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        disposableList.forEach {  //释放 disposable, 防止内存泄漏问题
            it.dispose()
        }
    }
```


## PopupWindow与View

为什么说这个呢？ 其实是对应到`DialogFragment`。对于这个我想说还是别用。它的内部实现是: Fragment->(Dialog ->(PhoneWindow))。这3个东西加在一起就够头疼的了。

所以对于一些侧滑弹窗、上下操作弹窗可以使用`PopupWindow+View`来实现，不过`PopupWindow`在这种场景下也有一些问题，但相较于`DialogFragment`少一些:

[PopupWindow不显示的问题](https://www.jianshu.com/p/a53d90a31591):其实这篇文章也没有完全解决，在某些手机上你一定要定死宽高，`PopupWindow`才可以显示出来。

[PopupWindow的弹出位置](https://www.jianshu.com/p/6c32889e6377):PopupWindow弹出位置的计算。其实我目前适用的都是基于参照物Anchor(一般我都是取`Activity.window.decorView`)的相对位置来展示的。

在具体使用时最好采用组合的方式,比如:

```
class SimplePopupWindow(val context: Context, val mContentView: View) {

    private var mWindow = PopupWindow()

    init {
        mWindow.apply {
            contentView = mContentView
            height = UIUtil.getScreenHeight()
            width = UIUtil.getScreenWidth()
            ...
        }
    }

    fun show(anchor: View) {
        mWindow.update()
        mWindow.showAsDropDown(anchor, 0, 0, Gravity.TOP)
    }

    .....
}
```


## View的生命周期

想较于`Fragment`的生命周期来说，`View`的生命周期就很弱了，`View`的生命周期相关方法可以参考下面这篇文章:

[Android View生命周期](https://www.jianshu.com/p/08e6dab7886e)

这里重点提一下:**对于`View.onAttatchToWindow`方法你应该知道它是在`ViewRootImpl.performTraversal()`中开始回调的，具体回调时机是`measure`前。**

但是当使用`View`来搭建页面级UI时，像`onAttatchToWindow`、`onDetachedFromWindow`这种方法可能就不是很适用了。我的一般操作是写个方法直接让上层(`Activity`)来调用:

>DemoPage
```
class DemoPage(context: Context) : LinearLayout(context),DemoPageProtocol{
    
    //View被展示时,Activtiy回调这个方法
    fun show() {
       //load data
    }

    //Activity被销毁或者Activity不展示View是回调这个方法
    fun onResume(){

    }
}
```

对于`ViewPager+View`的架构来说，完成`View`的懒加载并不是什么难事。

## Dialog与View

我曾经遇到过这样一个需求:

项目中的一个`全局loading`是使用`Dialog`来实现的，这就造成在loading出现时界面是锁死的,在这种情况下如果网络比较慢的话，很容易就让用户以为我们的app死掉了。所以需要实现这样一个全局loading:在它出现的时候不能锁死界面，并且用户点击返回键可以关掉它。

我是怎么实现的呢？其实我的实现方法比较取巧,好不好先不说，说一下思路吧:

1. 自定义一个`ViewGroup`, 它出现时会展示loading动画。
2. 其内部含有一个看不见的`EditText`，用于监听用户是否点击了返回键，点击了返回键的话就关掉loading。
3. 可以自动attach到`Activity`的`DecorView`上，当然也可以当做一个普通的`View`由其他组件来使用。

我个人感觉使用`View`来实现这个loading，相较于`Dialog`来说，还是很灵活的，有兴趣的同学可以看一下代码:

[GlobalLoadingView](picture/GlobalLoadingView.kt)


