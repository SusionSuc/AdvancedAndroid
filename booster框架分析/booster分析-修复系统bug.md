
[Booster](https://github.com/didi/booster)是一款专门为移动应用设计的易用、轻量级且可扩展的质量优化框架，其目标主要是为了解决随着 APP 复杂度的提升而带来的性能、稳定性、包体积等一系列质量问题。它提供了性能检测、多线程优化、资源索引内联、资源去冗余、资源压缩、系统 Bug 修复等一系列功能模块，可以使得稳定性能够提升 15% ~ 25%，包体积可以减小 1MB ~ 10MB。

>本文就分析一下`booster`是如何实现**系统bug修复**功能的。

# booster-transform-activity-thread

**这个组件可以处理系统Crash**

## 实现原理

`booster`hook了`ActivityThread.mH.mCallback`:

>ActivityThreadHooker.java
```
public static void hook() {
    try {
        final Handler handler = getHandler(thread); //通过反射获取到了ActivityThread.mH
        if (null == handler || !(hooked = setFieldValue(handler, "mCallback", new ActivityThreadCallback(handler)))) {
            Log.i(TAG, "Hook ActivityThread.mH.mCallback failed");
        }
    } catch (final Throwable t) {
        Log.w(TAG, "Hook ActivityThread.mH.mCallback failed", t);
    }
}
```

即上面用`ActivityThreadCallback`代理`ActivityThread.mH.mCallback`。它做了以下处理:

>ActivityThreadCallback.java
``` 
private final Handler mHandler; //代理的 Handler

@Override
public final boolean handleMessage(final Message msg) {
    try {
        this.mHandler.handleMessage(msg);
    } catch (final NullPointerException e) {
        if (hasStackTraceElement(e, ASSET_MANAGER_GET_RESOURCE_VALUE, LOADED_APK_GET_ASSETS)) {//没有栈信息，直接杀掉应用
            abort(e);
        }
        rethrowIfNotCausedBySystem(e);  //如果不是系统异常就抛出去
    } catch (final SecurityException
            | IllegalArgumentException
            | AndroidRuntimeException
            | WindowManager.BadTokenException e) {
        rethrowIfNotCausedBySystem(e);
    } catch (final Resources.NotFoundException e) {
        rethrowIfNotCausedBySystem(e);
        abort(e);
    } catch (final RuntimeException e) {
        final Throwable cause = e.getCause();
        if (((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) && isCausedBy(cause, DeadSystemException.class))
                || (isCausedBy(cause, NullPointerException.class) && hasStackTraceElement(e, LOADED_APK_GET_ASSETS))) {
            abort(e);
        }
        rethrowIfNotCausedBySystem(e);
    } catch (final Error e) {
        rethrowIfNotCausedBySystem(e);
        abort(e);
    }
    return true;
}
```

即**如果`mHandler.handleMessage()`发生了异常并且是系统异常的话就catch住**

## 问题

### catch哪些系统异常呢？

`booster`会检查异常堆栈，如果是以系统包名打头就认为是系统异常:

```
private static final String[] SYSTEM_PACKAGE_PREFIXES = {
        "java.",
        "android.",
        "androidx.",
        "dalvik.",
        "com.android.",
        ActivityThreadCallback.class.getPackage().getName() + "."
};


private static boolean isSystemStackTrace(final StackTraceElement element) {
    final String name = element.getClassName();
    for (final String prefix : SYSTEM_PACKAGE_PREFIXES) {
        if (name.startsWith(prefix)) {
            return true;
        }
    }
    return false;
}
```

### ActivityThread.Handler.handleMessage() 是什么时候调用的呢？

阅读`ActivityThread`源码可以看出`ActivityHread.mH`的类型是`ActivityThread.H`,它的`handleMessage`主要处理了下面这些事件:

```
public static final int BIND_APPLICATION        = 110;
public static final int EXIT_APPLICATION        = 111;
public static final int SERVICE_ARGS            = 115;
public static final int RECEIVER                = 113;
public static final int CREATE_SERVICE          = 114;
public static final int BIND_SERVICE            = 121;
public static final int UNBIND_SERVICE          = 122;
public static final int RELAUNCH_ACTIVITY = 160;
public static final int INSTALL_PROVIDER        = 145;        
...
```

即基本上就是系统通过`IPC`回调到我们应用的一些事件。

# booster-transform-toast

**这个组件修复了Toast在Android 7.1 上的 Bug**

## Toast在Android 7.1 上的问题

具体原因分析可以看这篇文章: [Android7.1.1Toast崩溃解决方案](https://juejin.im/post/5b96134f5188255c352d3ed7)

简单的说就是`Android 7.1`上弹Toast的代码没有`try catch`。 解决办法也很简单，就是`try catch`住。 那`booster`是怎么做的呢?

>ShadowToast.java
```
public class ShadowToast {

    /**
     * Fix {@code WindowManager$BadTokenException} for Android N
     * @param toast The original toast
     */
    public static void show(final Toast toast) {
        if (Build.VERSION.SDK_INT == 25) {
            workaround(toast).show(); 
        } else {
            toast.show();
        }
    }
}
```

上面`workaround(toast).show()`的实现就是通过反射`try-catch`住了可能crash的代码。

那`ShadowToast`怎么生效的呢？它其实是通过:

**自定义`gradle transform`在编译期间把所有的`Taost.show()`变为了`ShadowToast.show()`**, 可以查看构建报告:

```
 *android/widget/Toast.show()V => com/didiglobal/booster/instrument/ShadowToast.apply(Lcom/didiglobal/booster/instrument/ShadowToast;)V: com/draggable/library/extension/glide/GlideHelper$downloadPicture$2.accept(Ljava/io/File;)V
```

# booster-transform-res-check

**这个组件修复了"检查覆盖安装导致的 Resources 和 Assets 未加载的 Bug"**

对于这个bug我没有遇到过,我在网上简单的查找了一下也没有找到，不过看一看`booster`的解决方案吧:

>ResChecker
```
public class ResChecker {
    public static void checkRes(final Application app) {
        if (null == app.getAssets() || null == app.getResources()) {
            final int pid = Process.myPid();
            Process.killProcess(pid);
            System.exit(10);
        }
    }
}
```

上面这段代码 `ResChecker.checkRes()`会通过`gradle transform`动态插入到`Application`的`attachBaseContext()`和`onCreate()`中。

即解决办法是: **覆盖安装启动应用时在`Application.attachBaseContext/onCreate`中判断资源是否加载，如果没有加载的话直接kill掉应用**


# booster-transform-media-player

**这个组件用来修复`MediaPlayer`的崩溃**

解决方案也是类似于上面的组件，就是把崩溃的地方`try-catch`住,然后通过`gradle transform`动态替换掉代码中的`MediaPlayer`。具体`try-catch`的范围是:

```
public final class ShadowMediaPlayer implements Constants {

    public static MediaPlayer newMediaPlayer() {
        return workaround(new MediaPlayer());
    }

    public static MediaPlayer create(Context context, Uri uri) {
        return workaround(MediaPlayer.create(context, uri));
    }

    public static MediaPlayer create(final Context context, final Uri uri, final SurfaceHolder holder) {
        return workaround(MediaPlayer.create(context, uri, holder));
    }

    public static MediaPlayer create(final Context context, final Uri uri, final SurfaceHolder holder, final AudioAttributes audioAttributes, final int audioSessionId) {
        return workaround(MediaPlayer.create(context, uri, holder, audioAttributes, audioSessionId));
    }

    public static MediaPlayer create(final Context context, final int resid) {
        return workaround(MediaPlayer.create(context, resid));
    }

    public static MediaPlayer create(final Context context, final int resid, final AudioAttributes audioAttributes, final int audioSessionId) {
        return workaround(MediaPlayer.create(context, resid, audioAttributes, audioSessionId));
    }
    ...
}
```

# booster-transform-finalizer-watchdog-daemon 

**这个组件用来修复`finalizer`导致的`TimeoutException`**

finalizer`导致的`TimeoutException : 简单的说就是对象的`finalize()`执行时间过长。对于具体的分析详见这篇文章:[滴滴出行安卓端 finalize time out 的解决方案](https://segmentfault.com/a/1190000019373275)

这篇文章最终给出了2个解决方案是:

## 手动停掉 FinalizerWatchdogDaemon 线程

```
try {
    Class clazz = Class.forName("java.lang.Daemons$FinalizerWatchdogDaemon");
    Method method = clazz.getSuperclass().getDeclaredMethod("stop");
    method.setAccessible(true);
    Field field = clazz.getDeclaredField("INSTANCE");
    field.setAccessible(true);
    method.invoke(field.get(null));
} catch (Throwable e) {
    e.printStackTrace();
}
```

即通过反射停掉FinalizerWatchdogDaemon线程

## try-cathch 住异常

```
final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (t.getName().equals("FinalizerWatchdogDaemon") && e instanceof TimeoutException) {
             //ignore it
        } else {
            defaultUncaughtExceptionHandler.uncaughtException(t, e);
        }
    }
});
```

## booster使用的方案

`booster`采用的方案是**手动停掉 FinalizerWatchdogDaemon线程**, 具体实现也是通过`gradle transform`在`Application`创建的时候新开一个线程调用停掉`FinalizerWatchdogDaemon`线程的代码:

```
public class FinalizerWatchdogDaemonKiller {
    private static final int MAX_RETRY_TIMES = 10;
    private static final long THREAD_SLEEP_TIME = 5000;

    @SuppressWarnings("unchecked")
    public static void kill() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int retry = 0; isFinalizerWatchdogDaemonExists() && retry < MAX_RETRY_TIMES; retry++) {
                    try {
                        final Class clazz = Class.forName("java.lang.Daemons$FinalizerWatchdogDaemon");
                        final Field field = clazz.getDeclaredField("INSTANCE");
                        field.setAccessible(true);
                        final Object watchdog = field.get(null);
                        try {
                            final Field thread = clazz.getSuperclass().getDeclaredField("thread");
                            thread.setAccessible(true);
                            thread.set(watchdog, null);
                        } catch (final Throwable t) {
                            try {
                                final Method method = clazz.getSuperclass().getDeclaredMethod("stop");
                                method.setAccessible(true);
                                method.invoke(watchdog);
                        ....
            }
        }, "FinalizerWatchdogDaemonKiller").start();
    }
}
```
# END

到这里`booster`修复系统bug的`feature`就简单的过了一遍，后面会继续分析`booster`框架的其他功能。

更多文章见 : [AdvancedAdnroid](https://github.com/SusionSuc/AdvancedAndroid)


