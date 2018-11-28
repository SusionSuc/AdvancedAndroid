
>本文主要讨论如何将Android中的 Presenter 以一种简洁的方式做到与View的解耦，即View只依赖于最抽象的Presenter接口, 而不是具体的Presenter接口。

## 常规的写法

对于Android中的VP我们为了做到互相解耦，我们通常要给Presenter定义一个接口，给View定义一个接口, 假设我们要写一个搜索逻辑，可能会写出如下代码:

1. 定义接口
```
     class SearchProtocol{
        interface Presenter{
            fun search() //搜索
        }    

        interface View {
            fun showSearchResult() //显示搜索结果
        }
    }
```
2. 接口实现 
```
    class SearchPresenter : SearchProtocol.Presenter{ }

    class SearchView : SearchProtocol.View{

        val presenter:SearchProtocol.Presenter = LoginPresenter()

        fun doSearch(){
            presenter.search()
        }

        overried showSearchResult(){}
    }
```
这样写有什么问题呢 ？

- VP还没开始写，两个接口先定义下来了

- 对于某些例子, 会导致View依赖于Presenter

比如说现在大家经常使用的一种构建UI的方式:一个RecyclerView构建所有UI，假如下图这个搜索结果页就是使用RecyclerView构建的:

![](https://upload-images.jianshu.io/upload_images/2934684-94707585f9ece32e.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

如果用户点击筛选按钮(其实本质还是搜索)，那么就需要调用 persenter.search()。但是筛选这个item实际上是使用RecyclerView的一个Item构建的，因此我可能就需要把presenter传到这个ItemView，ItemView在筛选时调用`presenter.search()`

这样做有什么不好呢？：

1. 依赖了一个固定的presenter接口，不利于复用，如果在其他的界面我想复用这个ItemView，那么传另一个界面的Presenter很明显是不合适的。

2. 不利于单元测试: 其实RecyclerView中的ItemView也是一个View，如果在实例化这个View的时候还需要传一个指定的Presenter,那么单元测试这个View时为了提供它的环境就有点麻烦了。

## 更纯净的VP写法

### 统一Presenter的处理逻辑

在往下阅读之前可以先看一下这篇文章 : https://segmentfault.com/a/1190000008736866
这篇文章介绍了redux的设计思想，而下文所要介绍的Presenter的新实现就是借鉴了Redux的设计思想。

对于常规的写法，Presenter的处理逻辑是通过调用固定的方法实现的，这就导致依赖于一个固定的Presenter接口, 参考Redux的设计，我们可以这样设计Presenter:
```
    class Action

    class BasePresenter{
        abstract fun dispatch(action: Action)
    }
```
即所有的Presenter都实现这一个接口，外界对于Presenter逻辑的触发都通过`dispatch()`方法实现，对于上面搜索那个例子可以这样实现:
```
    class SearchAction(val keyword:String):Action

    class SearchPresenter(searchView:SearchViewProtocol):BasePresenter{
        overried fun dispatch(action:Action){
            when(action){
                is SearchAction -> doSearch()
            }
        }

        fun doSearch(){
          //...
          searchView.showSearchResult()
        }
    }

    class SearchView:SearchViewProtocol{

        val presenter:BasePresenter = SearchPresenter(this)

         fun doSearch(){
            presenter.dispatch(SearchAction("narato"))
        }
        ......
    }
```

这样写后对比于常规的写法有什么好处呢？

1. 减少了Presneter接口的定义，由于现在Presenter对外层的抽象是`dispatch`方法,因此新的VP不需要特定定义与View配套的Presenter接口。
2. View不依赖于固定的Presenter接口，统一使用BasePresenter，View可以很好的复用和进行单元测试。
3. View发出的Action，Presenter可以选择处理，也可以不处理。

### View对于状态的获取

在Redux中，View dispatch Action后对于数据的变化，可以通过订阅(观察)数据来刷新UI。不过对于这次我介绍的VP，View的数据是由Presenter所提供的，那么就不能使用Redux这种方法了。
其实在Android中，对于VP，我们 **认为且应该** :View所需要的数据应该在presenter刷新UI时由Presenter传递过来, 比如:

    presenter.showSearchResult(result)

即，View只负责展示UI，不应有其他逻辑。上面这种方式在一定程度上可以使View完成自己的职责，但在一些情况下就有问题了:

比如有一个按钮，它是否可以点击执行一些事情，依赖于当前界面某些数据的状态。

那常规我们可能会这样做:

    class MyBtton(presenter:Presenter){
        fun onClick(){
            if(presenter.canExecute()){

            }
        }
    }

如果这样写那就又会出现上面的问题:

1. 依赖具体的presenter,复用困难
2. 单元测试麻烦

为了达到 **view完全依赖抽象的Presenter** 我们可以借用`dispatch`的设计:
```
    class SeachState

    class SeachBasePresenter{
        fun <T : SeachState> queryState(statteClass: KClass<T>): T?
    }
```
即我们可以这样实现这个需求:
```
    class MyBtton(presenter:SeachBasePresenter){
        fun onClick(){
            if(presenter.queryState(MyButtonState::class)?.canExecute == true){

            }
        }
    }

    class MyButtonState(val canExecute:Boolean = false):SearchState

    class SeachButtonPresenter{
        override fun <T : SearchState> queryStatus(statusClass: KClass<T>): T? {
            return when (statusClass) {
                MyButtonState::class -> {
                    MyButtonState(true) as T
                }
                else -> null
            }
        }
    }
```
这样做依旧是达到了View只依赖于抽象的SearchBasePresenter的目的，不依赖于具体的Presenter，解决了上面的问题。

## 总结

因此我们在设计VP结构时可以设计成这种结构，可以达到View完全依赖于抽象的Presenter:

```
    open class Action()

    open class State()

    abstract class BasePresenter()  {

        abstract fun dispatch(action:Action)

        abstract fun <T : State> queryStatus(statusClass: KClass<T>): T?
    }
```














