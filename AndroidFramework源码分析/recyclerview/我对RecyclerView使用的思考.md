
>本文是`RecyclerView源码分析系列最后一篇文章`, 主要讲一下我个人对于`RecycleView`的使用的一些思考以及一些常见的问题怎么解决。

# RecyclerView的封装

现在很多app都会使用`RecyclerView`来构建一个页面，这个页面中有各种卡片类型。为了支持快速开发我们通常会对`RecycleView`的`Adapter`做一层封装来方便我们写各种类型的卡片,下面这种封装是我认为一种比较好的封装:

```
/**
 * 对 RecyclerView.Adapter 的封装。方便业务书写
 */
abstract class CommonRvAdapter<T>(private val dataSource: List<T>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val item = createItem(viewType)
        return CommonViewHolder(parent.context, parent, item)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val commonViewHolder = holder as CommonViewHolder<T>
        commonViewHolder.adapterItemView.bindData(dataSource[position], position)
    }

    override fun getItemCount() = dataSource.size

    override fun getItemViewType(position: Int): Int {
        return getItemType(dataSource[position])
    }

    /**
     * @param viewType 需要创建的ItemView的viewType, 由 {@link getItemType(item: T)} 根据数据产生
     * @return 返回这个 viewType 对应的 AdapterItemView
     * */
    abstract fun createItem(viewType: Int): AdapterItemView<T>

    /**
     * @param T 代表dataSource中的一个data
     *
     * @return 返回 显示 T 类型的data的 ItemView的 类型
     * */
    abstract fun getItemType(item: T): Int

    /**
     * Wrapper 的ViewHolder。 业务不必理会RecyclerView的ViewHolder
     * */
    private class CommonViewHolder<T>(context: Context?, parent: ViewGroup, val adapterItemView: AdapterItemView<T>)
    //这一点做了特殊处理，如果业务的AdapterItemView本身就是一个View，那么直接当做ViewHolder的itemView。 否则inflate出一个view来当做ViewHolder的itemView
        : RecyclerView.ViewHolder(if (adapterItemView is View) adapterItemView else LayoutInflater.from(context).inflate(adapterItemView.getLayoutResId(), parent, false)) {
        init {
            adapterItemView.initViews(itemView)
        }
    }
}

/**
 * 能被 CommonRvAdapter 识别的一个 ItemView 。 业务写一个RecyclerView中的ItemView，只需要实现这个接口即可。
 * */
interface AdapterItemView<T> {

    fun getLayoutResId(): Int

    fun initViews(var1: View)

    fun bindData(data: T, post: Int)
}
```

为什么我认为这是一个不错的封装?

## 业务如果写一个新的Adapter的话只需要实现两个方法:

```
abstract fun createItem(viewType: Int): AdapterItemView<T>

abstract fun getItemType(item: T): Int
```

即业务写一个`Adapter`只需要对 **数据** -> **UI** 做映射即可, 不需要关心`RecycleView.ViewHolder`的逻辑。

## 因为抽象了`AdapterItemView`, ItemView足够灵活

由于封装了`RecycleView.ViewHolder`的逻辑，因此对于`UI item view`业务方只需要返回一个实现了`AdapterItemView`的对象即可。可以是一个`View`,也可以不是一个`View`, 这是因为`CommonViewHolder`在构造的时候对它做了兼容:

```
val view:View = if (adapterItemView is View) adapterItemView else LayoutInflater.from(context).inflate(adapterItemView.getLayoutResId(), parent, false)
```

即如果实现了`AdapterItemView`的对象本身就是一个`View`,那么直接把它当做`ViewHolder`的`itemview`,否则就`inflate`出一个`View`作为`ViewHolder`的`itemview`。

其实这里我比较推荐实现`AdapterItemView`的同时直接实现一个`View`,即不要把`inflate`的工作交给底层框架。比如这样:

```
private class SimpleStringView(context: Context) : FrameLayout(context), AdapterItemView<String> {

    init {
        LayoutInflater.from(context).inflate(getLayoutResId, this)  //自己去负责inflate工作
    }

    override fun getLayoutResId() = R.layout.view_test

    override fun initViews(var1: View) {}

    override fun bindData(data: String, post: Int) {
        simpleTextView.text = data
    }
}
```

为什么呢？原因有两点 :

1. 继承自一个View可复用性很高，即这个`SimpleStringView`不仅可以在`RecycleView`中当一个`itemView`,也可以在任何地方使用。
2. 方便单元测试

但其实直接继承自一个`View`是有坑的，即上面那行inflate代码`LayoutInflater.from(context).inflate(getLayoutResId, this)`

它其实是把`xml`文件inflate成一个`View`。然后add到你`ViewGroup`中。因为`SimpleStringView`就是一个`FrameLayout`，所有相当于add到这个`FrameLayout`中。这其实就有问题了。**这相当于你可能多加了一层无用的父View**

所有如果是直接继承自一个View的话，我推荐这样写:

1. 布局文件中尽可能使用<merge>标签来消除这层无用的父View
2. 很简单的布局的可以直接在代码中写，不要inflate。这样其实也可以减少inflate的耗时，稍微提高了一点性能吧。

当然，如果你不需要对这个View做复用的话你可以不用直接继承自`View`,只实现`AdapterItemView`接口即可。


# RecyclerView使用常见的问题

## RecycleView设置了数据不显示

这个往往是因为你没有设置`LayoutManger`。 没有`LayoutManger`的话`RecycleView`是无法布局的，即是无法展示数据。

```
void dispatchLayout() { //没有设置 Adapter 和 LayoutManager， 都不可能有内容
        if (mAdapter == null) {
                Log.e(TAG, "No adapter attached; skipping layout");
                // leave the state in START
                return;
        }
        if (mLayout == null) {
                Log.e(TAG, "No layout manager attached; skipping layout");
                // leave the state in START
                return;
        }
}
```

即 `Adapter`或`Layout`任意一个为null,就不会执行布局操作。

## RecyclerView数据多次滚动后出现混乱

`RecycleView`在滚动过程中`ViewHolder`是会不断复用的，因此就会带着上一次展示的UI信息，所以在设置一个`ViewHolder`的UI是，尽量要做`resetUi()`操作:

```
override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.resetUi()
        ...设置信息UI
}
```

`resetUi()`这个方法就是用来把Ui还原为最初的操作。当然如果你的每一次`bindData`操作会对每一个UI对象重新赋值的话就不需要有这个操作。也不会出现`itemView`的UI混乱问题。

## IndexOutOfBoundsException: Inconsistency detected. Invalid item position 5(offset:5).state:9 

这个异常通常是由于`Adapter的数据源`改变没有及时通知`RecycleView`做UI刷新导致的，或者通知的方式有问题。 比如如果数据源变化了(比如数量变少了),而没有调用`notifyXXX`, 那么此时滚动`RecycleView`就会产生这个异常。

解决办法很简单 : **`Adapter的数据源`改变时应立即调用`adapter.notifyXXX`来刷新`RecycleView`** 。

分析一下这个异常为什么会产生:

在`RecycleView刷新机制`一文介绍过，`RecycleView`的滚动操作是不会走`RecycleView`的正常布局过程的，它直接根据滚动的距离来摆放`新的子View`, 而在`子View`是从`Adapter`获取的。 想象一下这种场景，原来数据源集合中
有8个Item，然后删除了4个后没有调用`adapter.notifyXXX()`，这时直接滚动`RecycleView`，比如滚动将要出现的是第6个Item，`LinearLayoutManager`就会向`Recycler`要第6个Item的View:

>`Recycler.tryGetViewHolderForPositionByDeadline()`:

```
final int offsetPosition = mAdapterHelper.findPositionOffset(position);  //position是6 
if (offsetPosition < 0 || offsetPosition >= mAdapter.getItemCount()) {  //但此时  mAdapter.getItemCount() = 5
        throw new IndexOutOfBoundsException("Inconsistency detected. Invalid item "
                + "position " + position + "(offset:" + offsetPosition + ")."
                + "state:" + mState.getItemCount() + exceptionLabel());
}
```

即这时就会抛出异常。如果调用了`adapter.notifyXXX`的话，`RecycleView`就会进行一次完全的布局操作，就不会有这个异常的产生。

其实还有很多异常和这个原因差不多，所以在使用`RecycleView`时一定要注意保证**数据和UI的同步，数据变化，及时刷新RecyclerView**

`IllegalArgumentException: Scrapped or attached views may not be recycled. isScrap:false` 很多时候也是这个原因导致的。





