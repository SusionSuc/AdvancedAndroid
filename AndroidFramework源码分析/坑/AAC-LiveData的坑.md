# 观察机制被中断

> 描述一下问题

简单的说就是观察者无法观察到`LiveData`的数据变化:

>观察者
```
viewModel.uiData.observe(view.lifeContext(), Observer {
    view.refreshData(it.dataList)
})
```

>LiveData
```
private fun refreshUiData(dataList: ArrayList<Any>..) {
    ....
    uiData.value = uiData.value
}
```

在上面代码片段中**观察者**只观察到一次数据更新回调，在第一次后，无论我怎么调用`refreshUiData()`这个方法，**观察者**的回调都没有再次触发。这是什么原因呢？

我在源码中追了一下才猜测到什么原因(后面再说原因)。再捋一遍源码看看是为什么，从`uiData.value = uiData.value`开始看，这个代码会调用到`dispatchingValue()`:

>LiveData.java
```
private void dispatchingValue(@Nullable ObserverWrapper initiator) {
    if (mDispatchingValue) {
        ..
        return;
    }
    mDispatchingValue = true;
    do {
        ...,
        considerNotify(initiator); //通知观察者
        ...
    } while (mDispatchInvalidated);

    mDispatchingValue = false;
}
```

上面代码我剔除了一些无关逻辑。`considerNotify()`这个方法就是负责通知所有的`LiveData`的观察者数据发生了变化, **那在这个case中就是这个方法没有被调用到!**, 那什么原因会导致这个方法不会被调用呢？

就是`mDispatchingValue`变量。它表示`LiveData`正在通知观察者数据发生变化，如果这个变量为`true`，即:

```
if (mDispatchingValue) {
    ..
    return;
}
```

那就不可能调用到`considerNotify()`这个方法了。可是看正常的代码逻辑的话，这个变量是不可能为`true`的。那什么情况下这个变量会为`false`呢？

其实就是异常的代码逻辑，即`considerNotify()`抛出异常时！！！那它后面的逻辑就走不到了！！而我遇到的这个问题就是`considerNotify()`的调用链中抛出了一个未检查的异常，它是在我的数据观察回调中抛出的:

```
viewModel.uiData.observe(view.lifeContext(), Observer {
    view.refreshData(it.dataList)
})
```

就是在上面`view.refreshData(it.dataList)`抛出的。这就导致了上面`mDispatchingValue`未被正确的设置回`false`。从而导致`LiveData`不能正确的派发数据变化事件给观察者！！！


