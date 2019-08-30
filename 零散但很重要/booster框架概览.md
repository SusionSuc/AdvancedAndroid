
# ActivityThread

>核心原理就是重新设置了`ActivityThread`的`hander`的`Handler.Callback`,处理这个回调中的异常，如果是系统异常就catch住:

```
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


# 图片压缩

`booster`中一共采用了两种图片压缩方案:

1. 通过`pngquant`压缩图片
2. 通过`cweb`将`png/jpeg`图片装换为`webp`格式

[压缩方案的讨论](https://juejin.im/entry/587f14378d6d810058a18e1f)

## pngquant

[官方文档](https://pngquant.org/)

## web

[了解webp](https://aotu.io/notes/2016/06/23/explore-something-of-webp/index.html)
