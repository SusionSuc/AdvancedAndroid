
组件设计思路:

**通过观察`LifeOwner(Activity)`的`LifeCycle Event`,在Activity`onDestroy`时释放掉其所有的`Disposable`**

>了解`LifeOwner`可以看一下这篇文章: [Android Architecture Components 组件介绍](https://www.jianshu.com/p/db8e804902f5)

# 使用方法:

### 在Activity与Fragment中

```
RxBus
    .toObservable(SynEvent::class.java)
    .subscribe {
      ...
    }
    .disposeOnDestroy(this)
```

项目中所有的`Activity`都是继承自`AppCompatActivity`。`AppCompatActivity`本身就是一个`LifeOwner`。

对于`Fragment`,其本身也是`LifeOwner`对象，使用方式同上。

### 在View中

```
RxBus
    .toObservable(SynEvent::class.java)
    .subscribe {
      ...
    }
    .disposeOnDestroy(context as AppCompatActivity)
```

对于依托于`Activity`的`View`来说，其`Context`就是`Activity(AppCompatActivity)`,所以这里直接做了强转。

那`view`的`context`一定是`Activity`吗？ 可以看这篇文章了解一下:

[View.getContext()一定会返回 Activity 对象么?](https://www.jianshu.com/p/d48a6e723f35)

**即在5.0以上的系统上返回的就是`Avctivity`，即`LifeOwner`，所以对于这个强转还是需要注意的。**

>PS: 目前我们的项目`minSdkVersion`是`21`。如果不是的话不能这样使用。

### 在Presenter中

```
RxBus
    .toObservable(SynEvent::class.java)
    .subscribe {
      ...
    }
    .disposeOnDestroy(view.lifeContext())
```

由于我们项目中所有MVP中的`View`都继承自下面接口:

```
interface BaseLifeCycleView {
    fun lifeContext(): AppCompatActivity
}
```

所以上面`view.lifeContext()`就是`LifeOwner`。

### 在Application中

```
RxBus
    .toObservable(SynEvent::class.java)
    .subscribe {
      ...
    }
    .disposeOnDestroy(ProcessLifecycleOwner.get())
```

`ProcessLifecycleOwner`也是`Android Architecture Components`中的组件，它可以用来观察整个app的生命周期。

### disposeOnStop 扩展函数

使用方式与`disposeOnDestroy`相同，不同的是会在`OnStop`时释放掉所有的`Disposable`。


# 不支持

不支持在`Service`、`BroadcastReceiver`和`ContentProvider`中使用，因为他们并不是`LifeOwner`。不过可以简单继承一下，然后自己改造成`LifeOwner`。


# 实现原理

实现原理很简单: 

**一个`LifeOwner`对象创建一个`LifeObserver`,它持有着`LifeOwner`的所有`Disposable`。在`LifeOwner的Lifecycle.Event.ON_DESTROY`时，释放`LifeOwner`的所有`Disposable`**

主要有2个组件:

## RxLifeCycleObserver

它是一个`LifecycleObserver`,持有`LifecycleOwner`并负责其所有的`Disposable`的释放工作。

```
internal class DestroyLifeCycleObserver(val lifeOwner: LifecycleOwner) : LifecycleObserver {

    private val disposableList = ArrayList<Disposable>()
    var requestRemoveLifecycleObserver: RequestRemoveLifecycleObserver? = null

    init {
        lifeOwner.lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        LogUtils.d(TAG, "${getKey()} OnLifecycleEvent ON_DESTROY , disposableList.size : ${disposableList.size}")
        disposableList.forEach {
            if (!it.isDisposed) {
                it.dispose()
            }
        }
        requestRemoveLifecycleObserver?.requestRemoveDestroyObserver(this)
    }

    fun addDisposable(disposable: Disposable) {
        if (disposable.isDisposed) return
        disposableList.add(disposable)
    }

    fun getKey() = lifeOwner.toString()
}
```

## GlobalRxDisposeManager

主要负责维护所有的LifeCycleObserver, 以`DestroyLifeCycleObserver`为例:

```
object GlobalRxDisposeManager {

    private val rxLifecycleObservers = HashMap<String, DestroyLifeCycleObserver?>()

    fun getLifecycleObserver(key: String): DestroyLifeCycleObserver? {
        return rxLifecycleObservers[key]
    }

    fun addLifecycleObserver(lifeCycleObserver: DestroyLifeCycleObserver) {
        rxLifecycleObservers[lifeCycleObserver.getKey()] = lifeCycleObserver
        lifeCycleObserver.requestRemoveLifecycleObserver = object : RequestRemoveLifecycleObserver {
            override fun requestRemove(observer: DestroyLifeCycleObserver) {
                rxLifecycleObservers.remove(observer.getKey())  //释放引用，避免内存泄漏
            }
        }
    }
    
    ...
}
```

## disposeOnDestroy扩展函数

组合`GlobalRxDisposeManager`与`DestroyLifeCycleObserver`并简化使用:

```
fun Disposable.disposeOnDestroy(lifeOwner: LifecycleOwner): Disposable {
    LogUtils.d(TAG, "life owner key : ${lifeOwner}")
    var lifecycleObserver = GlobalRxDisposeManager.getDestroyObserver(lifeOwner.toString())

    if (lifecycleObserver == null) {
        lifecycleObserver = DestroyLifeCycleObserver(lifeOwner)
        GlobalRxDisposeManager.addDestroyObserver(lifecycleObserver)
    }

    lifecycleObserver.addDisposable(this)

    return this
}
```


源码 : [RxLifeCycleExtensions](RxLifeCycleExtensions.kt)
