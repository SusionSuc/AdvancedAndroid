>前面分析了[RecyclerView的基本结构](https://github.com/SusionSuc/AdvancedAndroid/blob/master/AndroidFramework%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90/recyclerview/RecyclerView%E7%9A%84%E5%9F%BA%E6%9C%AC%E7%BB%84%E6%88%90%E7%BB%93%E6%9E%84.md)
>本文继续来看一下`RecyclerView`是如何`完成UI的刷新`以及`在滑动时子View的添加逻辑`。

本文会从源码分析两件事 :

1. `adapter.notifyXXX()`时RecyclerView的UI刷新的逻辑,即`子View`是如何添加到`RecyclerView`中的。
2. 在数据存在的情况下，滑动`RecyclerView`时`子View`是如何添加到`RecyclerView`并滑动的。

本文不会涉及到`RecyclerView`的动画，动画的实现会专门在一篇文章中分析。

# `adapter.notifyDataSetChanged()`引起的刷新

我们假设`RecyclerView`在初始状态是没有数据的，然后往数据源中加入数据后，调用`adapter.notifyDataSetChanged()`来引起`RecyclerView`的刷新:
```
data.addAll(datas)
adapter.notifyDataSetChanged()
```
用图描述就是下面两个状态的转换:

![](picture/adapter.notifyDataSetChanged.png)

接下来就来分析这个变化的源码，在上一篇文章中已经解释过，`adapter.notifyDataSetChanged()`时，会引起`RecyclerView`重新布局(`requestLayout`)，`RecyclerView`的`onMeasure`就不看了，核心逻辑不在这里。因此从`onLayout()`方法开始看:

## RecyclerView.onLayout

这个方法直接调用了`dispatchLayout`:
```
void dispatchLayout() {
    ...
    if (mState.mLayoutStep == State.STEP_START) {
        dispatchLayoutStep1();
        dispatchLayoutStep2();
    } else if (数据变化 || 布局变化) {
        dispatchLayoutStep2();
    }
    dispatchLayoutStep3();
}
```

上面我裁剪掉了一些代码，可以看到整个布局过程总共分为3步, 下面是这3步对应的方法:

```
STEP_START ->  dispatchLayoutStep1()
STEP_LAYOUT -> dispatchLayoutStep2()
STEP_ANIMATIONS -> dispatchLayoutStep2(), dispatchLayoutStep3()
```

第一步`STEP_START`主要是来存储当前`子View`的状态并确定是否要执行动画。这一步就不细看了。 而第3步`STEP_ANIMATIONS`是来执行动画的，本文也不分析了，本文主要来看一下第二步`STEP_LAYOUT`,即`dispatchLayoutStep2()`:

### dispatchLayoutStep2()

先来看一下这个方法的大致执行逻辑:

```
private void dispatchLayoutStep2() {  
    startInterceptRequestLayout(); //方法执行期间不能重入
    ...
    //设置好初始状态
    mState.mItemCount = mAdapter.getItemCount();
    mState.mDeletedInvisibleItemCountSincePreviousLayout = 0;
    mState.mInPreLayout = false;

    mLayout.onLayoutChildren(mRecycler, mState); //调用布局管理器去布局

    mState.mStructureChanged = false;
    mPendingSavedState = null;
    ...
    mState.mLayoutStep = State.STEP_ANIMATIONS; //接下来执行布局的第三步

    stopInterceptRequestLayout(false);
}
```

这里有一个`mState`，它是一个`RecyclerView.State`对象。顾名思义它是用来保存`RecyclerView`状态的一个对象，主要是用在`LayoutManager、Adapter等`组件之间共享`RecyclerView状态`的。可以看到这个方法将布局的工作交给了`mLayout`。这里它的实例是`LinearLayoutManager`，因此接下来看一下`LinearLayoutManager.onLayoutChildren()`:

## LinearLayoutManager.onLayoutChildren()

这个方法也挺长的，就不展示具体源码了。不过布局逻辑还是很简单的:

1. 确定锚点`(Anchor)View`, 设置好`AnchorInfo`
2. 根据`锚点View`确定有多少布局空间`mLayoutState.mAvailable`可用
3. 根据当前设置的`LinearLayoutManager`的方向开始摆放子View

接下来就从源码来看这三步。

### 确定锚点View

`锚点View`大部分是通过`updateAnchorFromChildren`方法确定的,这个方法主要是获取一个View，把它的信息设置到`AnchorInfo`中 :

```
mAnchorInfo.mLayoutFromEnd = mShouldReverseLayout   // 即和你是否在 manifest中设置了布局 rtl 有关

private boolean updateAnchorFromChildren(RecyclerView.Recycler recycler, RecyclerView.State state, AnchorInfo anchorInfo) {
    ...
    View referenceChild = anchorInfo.mLayoutFromEnd
            ? findReferenceChildClosestToEnd(recycler, state) //如果是从end(尾部)位置开始布局，那就找最接近end的那个位置的View作为锚点View
            : findReferenceChildClosestToStart(recycler, state); //如果是从start(头部)位置开始布局，那就找最接近start的那个位置的View作为锚点View

    if (referenceChild != null) {
        anchorInfo.assignFromView(referenceChild, getPosition(referenceChild)); 
        ...
        return true;
    }
    return false;
}
```

即， 如果是`start to end`, 那么就找最接近start(RecyclerView头部)的View作为布局的锚点View。如果是`end to start (rtl)`, 就找最接近end的View作为布局的锚点。

`AnchorInfo`最重要的两个属性时`mCoordinate`和`mPosition`，找到锚点View后就会通过`anchorInfo.assignFromView()`方法来设置这两个属性:
```
public void assignFromView(View child, int position) {
    if (mLayoutFromEnd) {
        mCoordinate = mOrientationHelper.getDecoratedEnd(child) + mOrientationHelper.getTotalSpaceChange();
    } else {
        mCoordinate = mOrientationHelper.getDecoratedStart(child);  
    }
    mPosition = position;
}
```

>- `mCoordinate`其实就是`锚点View`的`Y(X)`坐标去掉`RecyclerView`的padding。
>- `mPosition`其实就是`锚点View`的位置。

### 确定有多少布局空间可用并摆放子View

当确定好`AnchorInfo`后，需要根据`AnchorInfo`来确定`RecyclerView`当前可用于布局的空间,然后来摆放子View。以布局方向为`start to end (正常方向)`为例, 这里的`锚点View`其实是`RecyclerView`最顶部的View:

```
    // fill towards end  (1)
    updateLayoutStateToFillEnd(mAnchorInfo); //确定AnchorView到RecyclerView的底部的布局可用空间
    ...
    fill(recycler, mLayoutState, state, false); //填充view, 从 AnchorView 到RecyclerView的底部
    endOffset = mLayoutState.mOffset; 

    // fill towards start (2)
    updateLayoutStateToFillStart(mAnchorInfo); //确定AnchorView到RecyclerView的顶部的布局可用空间
    ...
    fill(recycler, mLayoutState, state, false); //填充view,从 AnchorView 到RecyclerView的顶部
```

上面我标注了`(1)和(2)`, 1次布局是由这两部分组成的, 具体如下图所示 :

![](picture/RecyclerView的布局步骤.png)

然后我们来看一下`fill towards end`的实现:

### fill towards end

#### 确定可用布局空间

在`fill`之前，需要先确定`从锚点View`到`RecyclerView底部`有多少可用空间。是通过`updateLayoutStateToFillEnd`方法:

```
updateLayoutStateToFillEnd(anchorInfo.mPosition, anchorInfo.mCoordinate);

void updateLayoutStateToFillEnd(int itemPosition, int offset) {
    mLayoutState.mAvailable = mOrientationHelper.getEndAfterPadding() - offset;
    ...
    mLayoutState.mCurrentPosition = itemPosition;
    mLayoutState.mLayoutDirection = LayoutState.LAYOUT_END;
    mLayoutState.mOffset = offset;
    mLayoutState.mScrollingOffset = LayoutState.SCROLLING_OFFSET_NaN;
}
```

`mLayoutState`是`LinearLayoutManager`用来保存布局状态的一个对象。`mLayoutState.mAvailable`就是用来表示`有多少空间可用来布局`。`mOrientationHelper.getEndAfterPadding() - offset`其实大致可以理解为`RecyclerView`的高度。*所以这里可用布局空间`mLayoutState.mAvailable`就是RecyclerView的高度*

#### 摆放子view

接下来继续看`LinearLayoutManager.fill()`方法，这个方法是布局的核心方法，是用来向`RecyclerView`中添加子View的方法:

```
int fill(RecyclerView.Recycler recycler, LayoutState layoutState, RecyclerView.State state, boolean stopOnFocusable) {
    final int start = layoutState.mAvailable;  //前面分析，其实就是RecyclerView的高度
    ...
    int remainingSpace = layoutState.mAvailable + layoutState.mExtra;  //extra 是你设置的额外布局的范围, 这个一般不推荐设置
    LayoutChunkResult layoutChunkResult = mLayoutChunkResult; //保存布局一个child view后的结果
    while ((layoutState.mInfinite || remainingSpace > 0) && layoutState.hasMore(state)) { //有剩余空间的话，就一直添加 childView
        layoutChunkResult.resetInternal();
        ...
        layoutChunk(recycler, state, layoutState, layoutChunkResult);   //布局子View的核心方法
        ...
        layoutState.mOffset += layoutChunkResult.mConsumed * layoutState.mLayoutDirection; // 一次 layoutChunk 消耗了多少空间
        ...
        子View的回收工作
    }
    ...
}
```

这里我们不看`子View回收逻辑`，会在单独的一篇文章中讲。 即这个方法的核心是调用`layoutChunk()`来不断消耗`layoutState.mAvailable`,直到消耗完毕。继续看一下`layoutChunk()方法`, 这个方法的主要逻辑是:

1. 从`Recycler`中获取一个`View`
2. 添加到`RecyclerView`中
3. 调整`View`的布局参数，调用其`measure、layout`方法。

```
void layoutChunk(RecyclerView.Recycler recycler, RecyclerView.State state,LayoutState layoutState, LayoutChunkResult result) {
        View view = layoutState.next(recycler);  //这个方法会向 recycler view 要一个holder 
        ...
        if (mShouldReverseLayout == (layoutState.mLayoutDirection == LayoutState.LAYOUT_START)) { //根据布局方向，添加到不同的位置
            addView(view);   
        } else {
            addView(view, 0);
        }
        measureChildWithMargins(view, 0, 0);    //调用view的measure
        
        ...measure后确定布局参数 left/top/right/bottom

        layoutDecoratedWithMargins(view, left, top, right, bottom); //调用view的layout
        ...
    }
```

到这里其实就完成了上面的`fill towards end`:

```
    updateLayoutStateToFillEnd(mAnchorInfo); //确定布局可用空间
    ...
    fill(recycler, mLayoutState, state, false); //填充view
```

`fill towards start`就是从`锚点View`向`RecyclerView顶部`来摆放子View，具体逻辑类似`fill towards end`，就不细看了。

# RecyclerView滑动时的刷新逻辑

接下来我们再来分析一下在不加载新的数据情况下，`RecyclerView`在滑动时是如何展示`子View`的，即下面这种状态 :

![](picture/RecyclerView滑动时的状态.png)

下面就来分析一下`3、4`号和`12、13`号是如何展示的。

`RecyclerView`在`OnTouchEvent`对滑动事件做了监听，然后派发到`scrollStep()`方法:
```
void scrollStep(int dx, int dy, @Nullable int[] consumed) {
    startInterceptRequestLayout(); //处理滑动时不能重入
    ...
    if (dx != 0) {
        consumedX = mLayout.scrollHorizontallyBy(dx, mRecycler, mState);
    }
    if (dy != 0) {
        consumedY = mLayout.scrollVerticallyBy(dy, mRecycler, mState);
    }
    ...
    stopInterceptRequestLayout(false);

    if (consumed != null) { //记录消耗
        consumed[0] = consumedX;
        consumed[1] = consumedY;
    }
}
```
即把滑动的处理交给了`mLayout`, 这里继续看`LinearLayoutManager.scrollVerticallyBy`, 它直接调用了`scrollBy()`, 这个方法就是`LinearLayoutManager`处理滚动的核心方法。

## LinearLayoutManager.scrollBy

```
int scrollBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
    ...
    final int layoutDirection = dy > 0 ? LayoutState.LAYOUT_END : LayoutState.LAYOUT_START;
    final int absDy = Math.abs(dy);
    updateLayoutState(layoutDirection, absDy, true, state); //确定可用布局空间
    final int consumed = mLayoutState.mScrollingOffset + fill(recycler, mLayoutState, state, false); //摆放子View
    ....
    final int scrolled = absDy > consumed ? layoutDirection * consumed : dy;
    mOrientationHelper.offsetChildren(-scrolled); // 滚动 RecyclerView
    ...
}
```

这个方法的主要执行逻辑是:

1. 根据布局方向和滑动的距离来确定可用布局空间`mLayoutState.mAvailable`
2. 调用`fill()`来摆放子View
3. 滚动RecyclerView

`fill()`的逻辑这里我们就不再看了，因此我们主要看一下`1 和 3`。

### 根据布局方向和滑动的距离来确定可用布局空间

以向下滚动为为例，看一下`updateLayoutState`方法:

```
// requiredSpace是滑动的距离;  canUseExistingSpace是true
void updateLayoutState(int layoutDirection, int requiredSpace,boolean canUseExistingSpace, RecyclerView.State state) {

    if (layoutDirection == LayoutState.LAYOUT_END) { //滚动方法为向下
        final View child = getChildClosestToEnd(); //获得RecyclerView底部的View
        ...
        mLayoutState.mCurrentPosition = getPosition(child) + mLayoutState.mItemDirection; //view的位置
        mLayoutState.mOffset = mOrientationHelper.getDecoratedEnd(child); //view的偏移 offset
        scrollingOffset = mOrientationHelper.getDecoratedEnd(child) - mOrientationHelper.getEndAfterPadding();
    } else {
       ...
    }
    
    mLayoutState.mAvailable = requiredSpace;  
    if (canUseExistingSpace)  mLayoutState.mAvailable -= scrollingOffset;
    mLayoutState.mScrollingOffset = scrollingOffset;
}
```

*所以可用的布局空间就是滑动的距离*。那`mLayoutState.mScrollingOffset`是什么呢？

上面方法它的值是`mOrientationHelper.getDecoratedEnd(child) - mOrientationHelper.getEndAfterPadding();`，其实就是`（childView的bottom + childView的margin） - RecyclerView的Padding`。 什么意思呢？ 看下图:

![](picture/RecyclerView滚动时可使用的布局空间.png)

`RecyclerView的padding`我没标注,不过相信上图可以让你理解: 滑动布局可用空间`mLayoutState.mAvailable`。同时` mLayoutState.mScrollingOffset`就是`滚动的距离 - mLayoutState.mAvailable`

所以 `consumed`也可以理解: 

```
int consumed = mLayoutState.mScrollingOffset + fill(recycler, mLayoutState, state, false);   
```

`fill()`就不看了。子View摆放完毕后就要滚动布局展示刚刚摆放好的子View。这是依靠的`mOrientationHelper.offsetChildren(-scrolled)`, 继续看一下是如何执行`RecyclerView`的滚动的

### 滚动RecyclerView

对于`RecyclerView`的滚动，最终调用到了`RecyclerView.offsetChildrenVertical()`:

```
//dy这里就是滚动的距离
public void offsetChildrenVertical(@Px int dy) {
    final int childCount = mChildHelper.getChildCount();
    for (int i = 0; i < childCount; i++) {
        mChildHelper.getChildAt(i).offsetTopAndBottom(dy);
    }
}
```

可以看到逻辑很简单,就是*改变当前子View布局的top和bottom*来达到滚动的效果。


本文就分析到这里。接下来会继续分析`RecyclerView`的复用逻辑。

>欢迎关注我的[Android进阶计划](https://github.com/SusionSuc/AdvancedAndroid)。看更多干货





