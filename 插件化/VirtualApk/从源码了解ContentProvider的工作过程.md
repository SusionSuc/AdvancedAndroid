>这篇文章也应该是继续看`VirtualApk`中关于`插件ContentProvider`的处理的。不过由于处理逻辑比较简单,所以到最后再看。本文的目的是了解系统对于`ContentProvider`的整个处理的过程。只看重点过程。

# ContentProvider的启动

我们知道`ContentProvider`是一个可以跨进程的组件,比如我们可以使用通讯录的`ContentProvider`来获取手机中的通信录信息。 `ContentResolver`封装了`ContentProvider`跨进程通信的逻辑，使我们在使用`ContentProvider`时不需要关心这些细节。

我们从`ContextImp.getContentResolver().query()`开始看:

```
public final @Nullable Cursor query(...) {
    IContentProvider unstableProvider = acquireUnstableProvider(uri); 
}
```

即首先要获得一个`IContentProvider`。我们在调用`ContextImp.getContentResolver()`获得的其实是`ApplicationContentResolver`。因此来看一下它的`acquireUnstableProvider()`:

```
protected IContentProvider acquireProvider(Context context, String auth) {
    return mMainThread.acquireProvider(context,
                ContentProvider.getAuthorityWithoutUserId(auth),
                resolveUserIdFromAuthority(auth), true);
    }
```

*`ContentProvider`并不是(没有实现)`IContentProvider`*,它是ContentProvider与系统交互的一个`aidl`接口。继续看源码, 切换到主线程:

```
public final IContentProvider acquireProvider(Context c, String auth, int userId, boolean stable) {
    // 如果是本进程的`ContentProvider`，并且已经实例化过了，则直接返回
    final IContentProvider provider = acquireExistingProvider(c, auth, userId, stable);
    if (provider != null) return provider;

    ContentProviderHolder holder = null;
    
    holder = ActivityManager.getService().getContentProvider(
                    getApplicationThread(), auth, userId, stable);

    //获取失败
    if (holder == null) return null;

    //这一步是为了完善
    //holder = installProvider(c, holder, holder.info, true /*noisy*/, holder.noReleaseNeeded, stable);

    return holder.provider;
}
```

即在本地没有获得过`IContentProvider`时，直接向`ActivityManagerService`发起`getContentProvider`的请求,最终调用`getContentProviderImpl()`, 这个方法就是`启动ContentProvider`的核心了:

首先来看一下这个方法的声明:

`ContentProviderHolder getContentProviderImpl(IApplicationThread caller,String name, IBinder token, boolean stable, int userId)`

即最终是返回一个`ContentProviderHolder`,它是什么呢？

它其实是一个可以在进程间传递的数据对象，我看一下它的定义:

```
public class ContentProviderHolder implements Parcelable {
    public final ProviderInfo info;
    public IContentProvider provider;
    public IBinder connection;
    ...
```

这个方法比较长，所以接下来我们分段来看这个方法 :

>`ActivityManagerService.getContentProviderImpl()`(1)
```
    ContentProviderRecord cpr;
    ContentProviderConnection conn = null;
    ProviderInfo cpi = null;
    ...
    cpr = mProviderMap.getProviderByName(name, userId); // 看看系统是否已经缓存了这个ContentProvider
```

先来解释一下`ContentProviderRecord`、`ContentProviderConnection`、`ProviderInfo`、`mProviderMap`分别是什么 :

`ContentProviderRecord`: 它是系统用来记录一个`ContentProvider`相关信息的对象。

`ContentProviderConnection`: 它是一个`Binder`。连接服务端(系统)和客户端(我们的app)。里面记录着一个`ContentProvider`的状态，比如是否已经死掉了等。

`ProviderInfo`: 用来保存一个`ContentProvider`的信息, 比如`authority`、`readPermission`等。

`mProviderMap`: 它的类型是`ProviderMap`。可以简单的理解为他保存着`key`为`ContentProvider`的名字，`value`为`ContentProviderRecord`的集合。

解析完上面4个对象后我们继续来看源码:

>`ActivityManagerService.getContentProviderImpl()`(2)
```
    ProcessRecord r = getRecordForAppLocked(caller); //获取客户端(获得content provider的发起者)的进程信息
    boolean providerRunning = cpr != null && cpr.proc != null && !cpr.proc.killed;
    if (providerRunning) { 
        ...
    }

    if (!providerRunning) {
        ...
    }
```

即根据`ContentProvider`所在的进程是否是活跃状态来进行不同的处理 :

## ContentProvider所在的进程正在运行

>`ActivityManagerService.getContentProviderImpl()`(3)
```
    if (r != null && cpr.canRunHere(r)) { // r的类型是ProgressRecord 。 如果请求的ContentProvider和客户端位于同一个进程
        ContentProviderHolder holder = cpr.newHolder(null);
        holder.provider = null; //注意，这里置空是让客户端自己去实例化！！
        return holder;
    }

    conn = incProviderCountLocked(r, cpr, token, stable); // 直接根据 ContentProviderRecord和ProcessRecord 构造一个 ContentProviderConnection

    ...
```

即如果请求的是同进程的`ContentProvider`则直接回到进程的主线程去实例化`ContentProvider`。否则使用`ContentProviderRecord`和`ProcessRecord`构造一个`ContentProviderConnection`

## ContentProvider所在的进程没有运行

>`ActivityManagerService.getContentProviderImpl()`(4)

上面我们知道，cpr(`ContentProviderRecord`)为空就会走到这个分支

```
    //先解析出来一个ProviderInfo
    cpi = AppGlobals.getPackageManager().resolveContentProvider(name, STOCK_PM_FLAGS | PackageManager.GET_URI_PERMISSION_PATTERNS, userId);
    ...
    ComponentName comp = new ComponentName(cpi.packageName, cpi.name);
    cpr = mProviderMap.getProviderByClass(comp, userId); //这个content provider 没有被加载过

    final boolean firstClass = cpr == null;
    if (firstClass) {
        ...

        cpr = new ContentProviderRecord(this, cpi, ai, comp, singleton); // 构造一个 ContentProviderRecord
    }

    if (r != null && cpr.canRunHere(r)) {
        return cpr.newHolder(null);  //还是，如果是同一个进程的 ContentProvider, 则直接交由客户端处理
    }

    final int N = mLaunchingProviders.size(); //  mLaunchingProviders它是用来缓存正在启动的 ContentProvider的集合的
    int i;
    for (i = 0; i < N; i++) {
        if (mLaunchingProviders.get(i) == cpr) {  // 已经请求过一次了，provider正在启动，不重复走下面的逻辑
            break;
        }
    }

    if (i >= N) {
        ProcessRecord proc = getProcessRecordLocked(cpi.processName, cpr.appInfo.uid, false);
        ...
        
        //启动content provider 所在的进程, 并且唤起 content provider
        proc = startProcessLocked(cpi.processName,cpr.appInfo, false, 0, "content provider",new ComponentName(cpi.applicationInfo.packageName, cpi.name), false, false, false);

        cpr.launchingApp = proc;
        mLaunchingProviders.add(cpr); //添加到正在启动的队列
    }

    //缓存 ContentProvider信息
    if (firstClass) {
        mProviderMap.putProviderByClass(comp, cpr);
    }
    mProviderMap.putProviderByName(name, cpr);

    //构造一个 ContentProviderConnection
    conn = incProviderCountLocked(r, cpr, token, stable);
    if (conn != null) {
        conn.waiting = true; //设置这个connection
    }

```










