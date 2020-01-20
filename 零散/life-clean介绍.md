
# 引入方法

```
implementation 'com.susion:life-clean:1.0.1'
```

# 介绍

`LifeClean`提供了一个稳定、简单的方式来组织你的Android代码,主要有以下特点:

1. 为`Presenter`和`ViewModel`提供生命周期组件`LifeOwner`
2. 使`View`具有感知`Activity`生命周期的能力
3. 新的`MVP`书写方式, `View`与`Presenter`完全解耦
4. 摒弃`Fragment`, 基于``View`构建应用UI
5. 提供`RxDisposeManager`,可以非常方便的释放`Disposable`,避免内存泄漏
6. 规范页面的更新状态、统一`RcyclerView`的用法

**`LifeClean`的目标是构建一份简单易维护的业务代码**

# 为`Presenter`和`ViewModel`提供`LifeOwner`

通过`LifeClean`来对`Presenter`或`ViewModel`进行创建:

```
val presenter: LifePresenter =  LifeClean.createPresenter<GithubPresenter, SimpleRvPageProtocol>(context, this) //context 为 activity, this为View所遵守的页面协议
```

经过`LifeClean`实例化的`Presenter`可以很方便的拿到`LifeOwner`:

```
class GithubPresenter(val view: SimpleRvPageProtocol) : LifePresenter() {

    private fun loadSearchResult(query: String, isLoadMore: Boolean = false) {
        apiService
        .searchRepos(query + IN_QUALIFIER, requestPage, PAGE_SIZE)
        .subscribe({
            ...
        }, {
            ...
        }).disposeOnDestroy(getLifeOwner())
    }

}
```

即上面通过`getLifeOwner()`方法可以很方便的获取`LifeOwner`来对`Disposable`进行及时释放。

`ViewModel`的创建类似:

```
private val viewModel by lazy {
    LifeClean.createLifeViewModel<GithubViewModel>(context)  //context 为 activity
}
```

# 为`View`提供感知`Activity`生命周期的能力

在`LifeClean`中需要感知`Activity`生命周期的`View`首先需要实现`LifePage`:

```
interface LifePage : LifecycleObserver
```

然后通过`LifeClean`来构建`View`实例:

```
val lifePage = LifeClean.createPage<GitHubLifePage>(this)  //this 为 activity
```

这样创建的`GitHubLifePage`就可以感知`Activity`的生命周期事件:

```
class GitHubLifePage(activity: AppCompatActivity) : FrameLayout(context),LifePage {

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        Toast.makeText(context, "接收到Activity的生命周期事件 onResume", Toast.LENGTH_SHORT).show()
    }
}
```

# 完全解耦`View`与`Presenter`

在`LifeClean`中定义的`View`与`Presenter`的交互接口如下:

```
interface Presenter {
    /**
     * view 发送Action, Presenter可以选择处理这个时间
     * */
    fun dispatch(action: Action)

    /**
     * view 希望获得的数据状态,这种数据一般是指一种作用范围很大的数据状态
     * */
    fun <T : State> getState(): T? {
        return null
    }
}
```

>`getStatus()`默认为空实现, 因为在完全遵守`数据驱动UI`的思想中, 一个`View`很少需要知道更大作用域的数据状态。

即:

1. `View`只能通过发送一个`Action`告诉`Presenter`要做的事情。
2. `View`可以通过`getStatus()`来查询需要的数据状态。

>`LifePresenter`继承自`Presenter`

在`LifeClean`中`View`与`Presenter`的交互应基于约定(protocol)，约定(protocol)中定义`View`能发出的`Action`、需要获取的`State`、`View`的刷新接口。比如:

```
interface SimpleRvPageProtocol {

    /**
     * 该页面发出的事件
     * */
    class LoadData(val searchWord: String, val isLoadMore: Boolean) : Action

    /**
     * 该页面需要的状态
     * */
    class PageState(val currentPageSize: Int) : State

    fun refreshDatas(datas: List<Any>, isLoadMore: Boolean = false, extra: Any = Any())

    fun refreshPageStatus(status: String, extra: Any = Any())

}
```
>上面是一个很通用的页面协议，已经被集成到`LifeClean`中。

结合上面谈到的两点, 使用`LifeClean`定义出一个页面(View)的写法如下:

```
class GitRepoMvpPage(context: AppCompatActivity) : SimpleRvPageProtocol, FrameLayout(context) {
    
    private val presenter: LifePresenter by lazy {
        LifeClean.createPresenter<GithubPresenter, SimpleRvPageProtocol>(context, this)
    }

    init {
        .....
        presenter.dispatch(SimpleRvPageProtocol.LoadData("Android", false))
    }

    ...
}
```

即在`LifeClean`中,`View`基本只使用`Presenter.dispatch(action)`方法与`Presenter`交互, 对于`View`发出的事件，`Presenter`可以选择处理,也可以选择不处理:

```
class GithubPresenter(val view: SimpleRvPageProtocol) : LifePresenter() {
   override fun dispatch(action: Action) {
        when (action) {
            is SimpleRvPageProtocol.LoadData -> {
                loadSearchResult(action.searchWord, action.isLoadMore)
            }
        }
    }
}
```

在这里`Presenter`是耦合于约定(`Protocol`)的。

# RxDisposeManager

现在使用`RxJava`来开发`app`基本已成为主流,为了解决`RxJava`的内存泄漏问题, `LifeClean`提供了`RxDisposeManager`,具体用法如下:

```
    apiService
    .searchRepos(query + IN_QUALIFIER, requestPage, PAGE_SIZE)
    .subscribe({
        ...
    }, {
        ...
    }).disposeOnDestroy(getLifeOwner())
```

即调用`disposeOnDestroy(getLifeOwner())`,`disposeOnDestroy()`接收的参数是`LifeOwner`。它会观察`LifeOwner`的生命周期事件并及时释放掉`Disposable`


# 规范页面状态

`LifeClean`简单的定义了下面这个页面状态类:

```
object PageStatus {

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

即你可以使用类似下面的方法来使你的`app`统一页面状态的更新:

```
    apiService.searchRepos(query + IN_QUALIFIER, requestPage, PAGE_SIZE)
    .doOnSubscribe {
        view.refreshPageStatus(if (isLoadMore) PageStatus.START_LOAD_MORE else PageStatus.START_LOAD_PAGE_DATA)
    }.doOnTerminate {
        view.refreshPageStatus(if (isLoadMore) PageStatus.END_LOAD_MORE else PageStatus.END_LOAD_PAGE_DATA)
    }.subscribe({ dataList->

        if(dataList.isEmpty){
             view.refreshPageStatus(PageStatus.EMPTY_DATA)
        }

    },{error->
        view.refreshPageStatus(PageStatus.ERROR)
    })
```

# 统一`RecyclerView`的用法

在`LifeClean`中所有`RecyclerView`的`ItemView`都必须继承自`View`并实现下面这个约定 :

```
interface AdapterItemView<T> {
    fun bindData(data: T, position: Int)
}
```

>即`ItemView`只需要简单的拿到数据渲染UI即可。

这个约定是通过`Adapter`来实现的, 你需要使用`LifeClean`中定义好的`Adapter`,比如`SimpleRvAdapter`, 举个例子 :

## 定义ItemView

```
class GitRepoView(context: Context) : AdapterItemView<Repo>, ConstraintLayout(context) {

    init {
        LayoutInflater.from(context).inflate(R.layout.repo_view_item, this)
        ......
    }

    override fun bindData(repo: Repo, position: Int) {
        repo_name.text = repo.fullName
        ...
    }
}
```

>对于`ItemView`做这种限制的原因是因为: 

1. 不希望把`ItemView`渲染的代码写在页面中，比如`Activity`
2. 解耦代码易维护

## 使用`SimpleRvAdapter`

```
class GitRepoMvpPage(activity: AppCompatActivity) : SimpleRvPageProtocol, FrameLayout(context) {

    private val adapter = SimpleRvAdapter<Any>(activity)).apply {
        registerMapping(String::class.java, SimpleStringView::class.java)
        registerMapping(Repo::class.java, GitRepoView::class.java)
    }

}
```

>`SimpleRvAdapter`提供了快速注册`ItemView`的能力。

