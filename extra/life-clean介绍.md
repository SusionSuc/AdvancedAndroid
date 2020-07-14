- [LifeClean](#lifeclean)
    - [规范`MVP`写法](#规范mvp写法)
    - [为`Presenter/View`提供`Lifecycle`](#为presenterview提供lifecycle)
        - [为Presenter提供Activity的生命周期](#为presenter提供activity的生命周期)
        - [为View提供Activity的生命周期](#为view提供activity的生命周期)
- [及时释放`RxJava Disposable`](#及时释放rxjava-disposable)
- [规范`RecyclerView.Adapter`的使用方式 : 对象到View的映射](#规范recyclerviewadapter的使用方式--对象到view的映射)
        - [CommonRvAdapter](#commonrvadapter)
        - [SimpleRvAdapter 与 MergeAdapter](#simplervadapter-与-mergeadapter)
- [规范全局UI状态的刷新](#规范全局ui状态的刷新)
- [End](#end)

# LifeClean

`LifeClean`是一个变种的MVP框架, 适用于常见的UI业务, 也对Android常用的组件定义了一些使用规范, 主要具有以下特点:

1. 规范`MVP`写法
2. 为`Presenter/View`提供`LifeCycle`
3. 及时释放`RxJava Disposable`,避免内容泄漏
4. 规范`RecyclerView.Adapter`的使用方式
5. 规范全局UI状态的刷新

## 规范`MVP`写法

个人认为`MVP`主要是用来做职责分离的, 即`Presenter`负责数据的加载逻辑, `View`负责数据的展示逻辑。

传统`MVP`的写法是将`Presenter`和`View`都抽取出一个接口,然后实现类之间使用这两个接口做隔离。

在`LifeClean`中不会对每一个`Presenter`都抽取一个接口, `LifeClean`规定:

1. 所有的`Presenter`都应该遵守同一个约定(接口)
2. 所有的`View`都应该使用`Presenter`接口来与`Presenter`交互

>抽象的`Presenter`接口:

```
// view 向 presenter 发出的事件(信号)
interface Action

//页面需要的状态
interface State

interface Presenter {

    fun dispatch(action: Action)

    fun <T : State> getState(): T? {
        return null
    }

}
```

它定义了`Presenter`的能力:

- `dispatch(Action)` : `Presenter`可以接收`View`发出的信号(`Action`)

- `getState():T` : `Presenter`可以返回给`View`一些状态(`State`)

**这些`Action/State`在`LifeClean`中都属于`View`, `View`应该在其所遵循的约定(接口)中定义这些`Action/State`**, 比如:

>基于`RecyclerView`来实现的页面的约定:
```
interface SimpleRvPageProtocol {

    //加载数据
    class LoadData(val searchWord: String, val isLoadMore: Boolean) : Action

    //查询数据状态
    class PageState(val currentPageSize: Int) : State

    //刷新页面数据
    fun refreshDatas(datas: List<Any>, isLoadMore: Boolean = false, extra: Any = Any())

    //刷新页面状态
    fun refreshPageStatus(status: String, extra: Any = Any())

}
```

结合`Presenter`的一个具体使用示例:

```
//View
class GitRepoMvpPage(activity: AppCompatActivity) : SimpleRvPageProtocol, FrameLayout(activity) {

    //类型为最抽象的Presenter
    private val presenter: Presenter = GithubPresenter(this)

    init {
         // 通知Presenter做数据的加载
         presenter.dispatch(SimpleRvPageProtocol.LoadData("Android", false))
    }

    override fun refreshDatas(datas: List<Any>, isLoadMore: Boolean, extra: Any) {
        //查询数据状态
        val currentPageSize =presenter.getState<SimpleRvPageProtocol.PageState>()?.currentPageSize ?: 0
        Toast.makeText(context, "当前页 : $currentPageSize", Toast.LENGTH_SHORT).show()
    }
    ...
}

//Presenter
class GithubPresenter(val view: SimpleRvPageProtocol) : Presenter {

    private var page = 0

    override fun dispatch(action: Action) {
        when (action) {
            is SimpleRvPageProtocol.LoadData -> {
                ...
                view.refreshDatas(list)
            }
        }
    }

    override fun <T : State> getState(): T? {
        return SimpleRvPageProtocol.PageState(page) as? T
    }
}
```

**在`LifeClean`中将`View`定义为业务的中心,将`Presenter`的能力(Action)都定义到了`View`(约定)中,`Presenter`可以自己选择性的处理这个`Action`, 即`View`完全解耦于`Presenter`**

## 为`Presenter/View`提供`Lifecycle`

### 为Presenter提供Activity的生命周期

一般会在`Presenter`中做资源的加载工作,比如使用`RxJava`进行网络请求,那么如何及时的释放`Disposable`来避免内存泄漏呢?

在`LifeClean`中`Presenter`可以通过继承`LifePresenter`来观察`Activity`的生命周期:

```
class GithubPresenter(val view: SimpleRvPageProtocol) : LifePresenter() {

    override fun onActivityCreate() {
        Log.d(TAG, "onActivityCreate")
    }

}
```

**即继承`LifePresenter`, 然后复写`Activity`相关生命周期方法**, 那为什么`LifePresenter`拥有`Activity`的生命周期呢? 内部实现如下:

```
abstract class LifePresenter : Presenter, LifecycleObserver {

    private var lifeOwnerReference = WeakReference<AppCompatActivity>(null)

    fun injectLifeOwner(lifecycleOwner: AppCompatActivity) {
        lifeOwnerReference = WeakReference(lifecycleOwner)
        lifecycleOwner.lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    open fun onActivityCreate() {

    }
}
```

**即`LifePresenter`就是一个`LifecycleObserver`,它是`LifeCycle`的一个观察者**, 那`injectLifeOwner()`这个方法在哪里调用的呢?

**其实在`LifeClean`中如果你想让`Presenter`感知`Activity`的生命周期,那么必须继承`LifePresenter`, 并且使用`LifeClean`提供的模板方法来创建这个`Presenter`:**

```
class GitRepoMvpPage(context: AppCompatActivity) : SimpleRvPageProtocol, FrameLayout(context) {

    val presenter: Presenter = LifeClean.createPresenter<GithubPresenter, SimpleRvPageProtocol>(context, this)

}
```

`LifeClean.createPresenter()`会通过反射来构造`GithubPresenter`并调用`injectLifeOwner()`,使`GithubPresenter`可以感知`Activity`的生命周期。


### 为View提供Activity的生命周期

这里的`View`特指使用`ViewGroup`实现的页面,不过由于多继承的问题,在`LifeClean`中`View`感知`Activity`的生命周期的用法与`Presenter`并不相同。

首先你的`ViewGroup`需要实现`LifePage`接口:

```
interface LifePage : LifecycleObserver
```

然后使用`LifeClean`的模板方法创建这个`ViewGroup`:

```
val lifePage = LifeClean.createPage<GitHubLifePage>(activity)
```

然后就可以感知`Activity`的生命周期了:

```
class GitHubLifePage(context: AppCompatActivity) : FrameLayout(context),LifePage {

   @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        Toast.makeText(context, "接收到Activity的生命周期事件 onResume", Toast.LENGTH_SHORT).show()
    }

}
```

**`LifeClean`会在`View Dettach`时自动解除对`Activity`生命周期的观察。**


# 及时释放`RxJava Disposable`

`LifeClean`提供了自动释放`Disposable`的方法:

```
fun Disposable.disposeOnStop(lifeOwner: LifecycleOwner?): Disposable?
```

比如在LifePresenter中释放`Disposable`:

```
    apiService.searchRepos(query + IN_QUALIFIER, requestPage, PAGE_SIZE)
    .subscribe({...})
    .disposeOnDestroy(getLifeOwner())
```

`disposeOnDestroy(getLifeOwner())`会自动在`LifeOwner Destroy`时释放掉`Disposable`。

# 规范`RecyclerView.Adapter`的使用方式 : 对象到View的映射

**`LifeClean`中`RecyclerView.Adapter`应实现`AdapterDataToViewMapping`接口, 它定义了对象与View的映射关系**:

```
interface AdapterDataToViewMapping<T> {
    //对象 ——> Type
    fun getItemType(data: T): Int

    // Type -> View
    fun createItem(type: Int): AdapterItemView<*>?
}
```

**`RecyclerView`的`ItemView`应实现`AdapterItemView`接口,这样`ItemView`只需要拿到数据做UI渲染即可**:

```
interface AdapterItemView<T> {
    fun bindData(data: T, position: Int)
}
```

### CommonRvAdapter

`CommonRvAdapter`是`AdapterDataToViewMapping`的抽象实现类。**它要求所有的`ItemView`都应该是`View`的子类**:

```
//data数据集合应该交给CommonRvAdapter维护
abstract class CommonRvAdapter<T>(val data: MutableList<T> = ArrayList()) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    AdapterDataToViewMapping<T> {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val item = createItem(viewType)
            ?: throw RuntimeException("AdapterDataToViewMapping.createItem cannot return null")
        return CommonViewHolder(item)
    }

    //item必须继承自View
   protected class CommonViewHolder<T> internal constructor(var item: AdapterItemView<T>) :
        RecyclerView.ViewHolder(if (item is View) item else throw RuntimeException("item view must is view"))

}
```

即`CommonRvAdapter`强调的是: **把对象映射为View**。比如:

```
class SimpleDescView(context: Context) : AppCompatTextView(context), AdapterItemView<SimpleDescInfo> {

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        setPadding(30, 30, 30, 0)
        setTextColor(Color.DKGRAY)
    }

    override fun bindData(data: SimpleDescInfo, position: Int) {
        text = data.desc
    }

}
```

### SimpleRvAdapter 与 MergeAdapter

他俩都继承自`CommonRvAdapter`, `SimpleRvAdapter`提供快速**映射对象到View**的能力:

```
val adapter = SimpleRvAdapter<Any>(context).apply {
    registerMapping(String::class.java, SimpleStringView::class.java)
    registerMapping(Repo::class.java, GitRepoView::class.java)
}
```

即通过反射来动态构造对象对应的`View`,**不过这里`View`必须要有`constructor(context)`构造函数**。

`MergeAdapter`可以合并多个遵循`AdapterDataToViewMapping`接口的`RecyclerView.Adapter`,**它可以大大提高`RecyclerView.Adapter`的复用性**:

```
  private val titleAdapter by lazy {
        SimpleRvAdapter<Any>(this).apply {
            registerMapping(SimpleTitleInfo::class.java, SimpleTitleView::class.java)
        }
    }

    private val descAdapter by lazy {
        SimpleRvAdapter<Any>(this).apply {
            registerMapping(SimpleDescInfo::class.java, SimpleDescView::class.java)
        }
    }

    private val mergeAdapter by lazy {
        MergeAdapter(
            adapterTitle,
            adapterDesc
        )
    }
```

上面`mergeAdapter`组合了`titleAdapter`和`descAdapter`的映射能力。


# 规范全局UI状态的刷新

大多数App的页面状态都是相同的, `LifeClean`定义常见的页面状态, 可以用来规范整个`App`的页面状态刷新逻辑:

```
object PageStatus {

    //一些常用的页面状态
    val START_LOAD_MORE = "start_load_more"
    val END_LOAD_MORE = "end_load_more"
    val START_LOAD_PAGE_DATA = "start_load_page_data"
    val END_LOAD_PAGE_DATA = "end_load_page_data"
    val NO_MORE_DATA = "no_more_data"
    val EMPTY_DATA = "empty_data"
    val NET_ERROR = "net_error"
    val TOAST = "show_toast"
    val PRIVACY_DATA = "privacy_data"
    val CONTENT_DELETE = "content_delete"
    val ERROR = "error"
    val UNDEFINE = "undefine"

    ...
}
``` 

# End

本文介绍了`LifeClean`的核心思想以及它的主要特性, 遵循`LifeClean`的思想可以帮助你写出清晰、复用性高的业务代码, 当然太简单的逻辑也没必要硬套, 在思想统一的基础上代码的可维护还是很重要的。

引入方法:

```
implementation 'com.susion:life-clean:1.0.7'
```

**LifeClean仓库地址 : https://github.com/SusionSuc/LifeClean**