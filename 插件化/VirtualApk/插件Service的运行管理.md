
本文出自: https://github.com/SusionSuc/AdvancedAndroid

在继续看`VirtualApk如何启动一个插件Service`之前，先简单的看一下`Android`如何启动一个`Service`, 主要是有个印象。

>下面的源码参考自Android8.0。 贴的源码只是包含一些关键点。

# Service启动的大体流程

我们从`ContextImpl.startService()`开始看。 为什么从这里开始看呢？ 如果你看过前面的文章`插件Activity的启动`, 你应该知道在Activity创建时，其`Context`的实例就是`ContextImpl`。

```
    public ComponentName startService(Intent service) {
        .....
        return startServiceCommon(service, false, mUser);
    }

    private ComponentName startServiceCommon(Intent service, boolean requireForeground,UserHandle user) {
        ..
        ActivityManager.getService().startService(mMainThread.getApplicationThread(), service, ....);
       ......
    }
```

即直接向`ActivityManager`请求启动Service。这是一次通过`Binder`跨进程调用调用。最后调用到`ActivityManagerService.startService()`,然后它又调用了`ActiveServices.startServiceLocked()`, 来看一下关键步骤:

```
 ComponentName startServiceLocked(....){
    //创建 ServiceRecord, 并缓存起来
    ServiceLookupResult res = retrieveServiceLocked(.....); //这里会检测Service是否在 manifest文件中注册
    ServiceRecord r = res.record;
    ....一系列权限相关检查
    //继续启动流程
    ComponentName cmp = startServiceInnerLocked(smap, service, r, callerFg, addToStarting);
 }
```

在后续流程中，真正启动Service的方法是`ActiveServices.bringUpServiceLocked()`:

```
    private String bringUpServiceLocked(ServiceRecord r, .....) {
        ProcessRecord app;
        ....
        //是不是要单独开了一个进程来启动这个Service
        final boolean isolated = (r.serviceInfo.flags&ServiceInfo.FLAG_ISOLATED_PROCESS) != 0;
        ...
        if (!isolated) {
            app = mAm.getProcessRecordLocked(procName, r.appInfo.uid, false);
            .....
            realStartServiceLocked(r, app, execInFg); //真正启动Service
            return null;
        } 

        //启动一个对应的进程
        app = mAm.startProcessLocked(procName, r.appInfo, true, intentFlags,
                    hostingType, r.name, false, isolated, false)
        .....

        //等会启动
        if (!mPendingServices.contains(r)) {
            mPendingServices.add(r);
        }
    }
```

`ActiveServices.realStartServiceLocked()`这个方法做的事情就很熟悉了:

```
    app.thread.scheduleCreateService(r, r.serviceInfo,....);  //这个方法会导致 service.onCreate()调用
    ....
    sendServiceArgsLocked(r, execInFg, true);  //这个方法会导致 service.onStartCommand()调用
```

即切换到了我们应用的进程，接下来就是在`ActivityThread`中启动Service了:

```
    //ActivityThread.java
    private void handleCreateService(CreateServiceData data) {
        ......
        java.lang.ClassLoader cl = packageInfo.getClassLoader();
        service = (Service) cl.loadClass(data.info.name).newInstance();
        ......
        ContextImpl context = ContextImpl.createAppContext(this, packageInfo);
        context.setOuterContext(service); 
    
        Application app = packageInfo.makeApplication(false, mInstrumentation);    //不为null， 直接返回
        service.attach(context, this, data.info.name, data.token, app,
                ActivityManager.getService());  
        service.onCreate();
        mServices.put(data.token, service);
        ......
    }
```

ok分析到这里，我们大致知道了`Service`是如何启动的。 至于`Service`的绑定这里先不做分析。 那分析这一遍源码有什么意义呢？当然是为了方便接下来的分析`插件化Service的启动`。

# VirtualApk-插件化Service的启动

- 服务端对于Service的启动有校验机制吗 ?

前面在分析源码时注释已经标注了。`ActiveServices.retrieveServiceLocked()`这个方法在构造`ServiceRecord`的时候会做这个校验。 所以启动一个插件的Service，我们也需要类似`插件Activity`来启动一个占坑的`Service`。

## 动态代理ActivityManagerService

所以`VirtualApk`对于插件Service启动的第一步就是需要绕过系统校验，原理类似于插件Activity的启动，只不过由于`Service`的启动和`Instrumentation`关系不大，它hook的是`ActivityManagerService`。

```
    protected void hookSystemServices() {
        Singleton<IActivityManager> defaultSingleton;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultSingleton = Reflector.on(ActivityManager.class).field("IActivityManagerSingleton").get();
        } else {
            defaultSingleton = Reflector.on(ActivityManagerNative.class).field("gDefault").get();
        }

        //通过java动态代理 hook 
        IActivityManager activityManagerProxy = (IActivityManager) Proxy.newProxyInstance(mContext.getClassLoader(), new Class[] { IActivityManager.class },
            createActivityManagerProxy(defaultSingleton.get()));
        Reflector.with(defaultSingleton).field("mInstance").set(activityManagerProxy); // hook for android8.0以下版本
    }
```

hook之后目的当然是为了绕过系统对`插件Service`的校验。类似于`Activity`的启动，在插件Service启动时会把`占坑Service`给`ActivityManagerService`校验。在VirtualApk中也定义了两个占坑`Service`:

```
    <service android:exported="false" android:name="com.didi.virtualapk.delegate.LocalService" />
    <service android:exported="false" android:name="com.didi.virtualapk.delegate.RemoteService" android:process=":daemon"/>
```

现在我们看`VirtualApk`是如何在Service启动时替换插件`Service`为这两个`占坑的Service`:

```
public class ActivityManagerProxy implements InvocationHandler { //前面已经标注，对ActivityManagerService的hook是通过java的动态代理
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("startService".equals(method.getName())) { 
            return startService(proxy, method, args);  
        }

       if ("stopService".equals(method.getName())) {
           return stopService(proxy, method, args);
        }
    }
```

即如果调用的是`ActivityManagerService.startService`,那就做一些处理:

```
    protected Object startService(Object proxy, Method method, Object[] args) throws Throwable {
        Intent target = (Intent) args[1];
        ...如果这个intent不是插件的Service直接返回 
        Intent wrapperIntent = wrapperTargetIntent(target, serviceInfo, extras, RemoteService.EXTRA_COMMAND_START_SERVICE);
        return mPluginManager.getHostContext().startService(wrapperIntent);
    }

    protected Object stopService(Object proxy, Method method, Object[] args) throws Throwable {
        ....流程同   startService
        startDelegateServiceForTarget(target, resolveInfo.serviceInfo, null, RemoteService.EXTRA_COMMAND_STOP_SERVICE);
    }

    protected Intent wrapperTargetIntent(Intent target, ServiceInfo serviceInfo, Bundle extras, int command) {
        target.setComponent(new ComponentName(serviceInfo.packageName, serviceInfo.name));
        String pluginLocation = mPluginManager.getLoadedPlugin(target.getComponent()).getLocation();

        //插件启动的service是 本地的还是远程的
        Class<? extends Service> delegate = PluginUtil.isLocalService(serviceInfo) ? LocalService.class : RemoteService.class;
        Intent intent = new Intent();
        intent.setClass(mPluginManager.getHostContext(), delegate);
        intent.putExtra(RemoteService.EXTRA_TARGET, target);
        intent.putExtra(RemoteService.EXTRA_COMMAND, command); // 注意这个command。 这里是: EXTRA_COMMAND_START_SERVICE
        intent.putExtra(RemoteService.EXTRA_PLUGIN_LOCATION, pluginLocation);
        .....
        return intent;
    }
```

即如果启动的是`插件Service`, 就把要启动的`插件Serivice`的信息保存在intent中，然后启动`占坑Service`。并且对于不同的操作在intent中标记为一个不同的`command`，比如上面的 `EXTRA_COMMAND_START_SERVICE`和`EXTRA_COMMAND_STOP_SERVICE`。其余还有`EXTRA_COMMAND_BIND_SERVICE`和`EXTRA_COMMAND_UNBIND_SERVICE`。

接下来还要像启动`插件Activity`那样再把`Activity`换回来吗？ `VirtualApk`并没有这么做，它就是直接启动了`LocalService` or `RemoteService`, 只不过在这两个Service中对不同的`command`做了不同的处理,比如`EXTRA_COMMAND_START_SERVICE`:

```
public class LocalService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int command = intent.getIntExtra(EXTRA_COMMAND, 0);
        switch (command) {
            case EXTRA_COMMAND_START_SERVICE: {
                ActivityThread mainThread = ActivityThread.currentActivityThread();
                IApplicationThread appThread = mainThread.getApplicationThread();
                Service service;
                if (this.mPluginManager.getComponentsHandler().isServiceAvailable(component)) {
                    service = this.mPluginManager.getComponentsHandler().getService(component); //如果这个Service已经运行
                } else {
                    //实例化插件Service
                    service = (Service) plugin.getClassLoader().loadClass(component.getClassName()).newInstance();
                    Application app = plugin.getApplication();
                    IBinder token = appThread.asBinder();
                    Method attach = service.getClass().getMethod("attach", Context.class, ActivityThread.class, String.class, IBinder.class, Application.class, Object.class);
                    IActivityManager am = mPluginManager.getActivityManager();
                    attach.invoke(service, plugin.getPluginContext(), mainThread, component.getClassName(), token, app, am);
                    service.onCreate();

                    //缓存Service，避免重复实例化
                    this.mPluginManager.getComponentsHandler().rememberService(component, service);
                }
                //调用插件Service的 onStartCommand
                service.onStartCommand(target, 0, this.mPluginManager.getComponentsHandler().getServiceCounter(service).getAndIncrement());
                break;
        }
    }
}
```

即它在`LocalService`的`onStartCommand`中，模拟了Service的生命周期方法的调用，具体生命周期方法调用步骤可以回顾前面看过的`Service`源码。对于其他`stopService`，`bindService`也是由`LocalService`在`onStartCommand`中来模拟的。对于`RemoteService`这里就不列了，处理其实和`LocalService`是一样的，只不过`RemoteService`是跑在另一个进程中的。

所以这里来总结一下`VirtualApk`中的`插件Serivice`的真正运行情况吧,这里以主进程中的Service为例:

1. `插件Service`都是运行在`LocalService`中的。其实这无所谓，都是跑在主进程中的。
2. `插件Service`的相关生命周期方法都是`LocalService`来模拟调用的(在主线程中)。
3. 即真正在后台一直跑的Service是`LocalService`。


所以从源代码来看，`VirtualApk`对于`插件Service`通过依赖于系统`Service`，创建了一套自己的`插件Service`运行模型。有没有什么缺点呢？当然有

1. 自己模拟的Service生命周期方法毕竟不是系统，随着系统版本的更新需要不断维护
2. 没有真正支持开`多进程Service`。并且远程Service的名称的修改，需要修改源代码。

好，对于`VirtualApk`的`插件Service`的管理源码的解读就看到这里。 欢迎关注我的Android进阶计划 : https://github.com/SusionSuc/AdvancedAndroid














