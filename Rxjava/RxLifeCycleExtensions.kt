import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import io.reactivex.disposables.Disposable
import java.util.*


/**
 * 解决RxJava内存泄漏问题
 *
 * 依托于 [LifecycleOwner]
 * */
private val TAG = "RxLifeCycle"

internal object GlobalRxDisposeManager {

    //生命周期观察者集合
    private val destroyLifeCycleObserver = HashMap<String, DestroyLifeCycleObserver?>()
    private val stopLifeCycleObserver = HashMap<String, StopLifeCycleObserver?>()

    //释放引用，避免内存泄漏
    private val removeLifecycleObserver = object : RequestRemoveLifecycleObserver {
        override fun requestRemoveDestroyObserver(observer: DestroyLifeCycleObserver) {
            destroyLifeCycleObserver.remove(observer.getKey())
            LogUtils.d(TAG, "destroyLifeCycleObserver size : ${destroyLifeCycleObserver.size}")
        }

        override fun requestRemoveStopObserver(observer: StopLifeCycleObserver) {
            stopLifeCycleObserver.remove(observer.getKey())
            LogUtils.d(TAG, "stopLifeCycleObserver size : ${destroyLifeCycleObserver.size}")
        }
    }

    fun getDestroyObserver(key: String): DestroyLifeCycleObserver? {
        return destroyLifeCycleObserver[key]
    }

    fun getStopObserver(key: String): StopLifeCycleObserver? {
        return stopLifeCycleObserver[key]
    }

    fun addDestroyObserver(destroyObserver: DestroyLifeCycleObserver) {
        destroyLifeCycleObserver[destroyObserver.getKey()] = destroyObserver
        destroyObserver.requestRemoveLifecycleObserver = removeLifecycleObserver
    }

    fun addStopObserver(stopObserver: StopLifeCycleObserver) {
        stopLifeCycleObserver[stopObserver.getKey()] = stopObserver
        stopObserver.requestRemoveLifecycleObserver = removeLifecycleObserver
    }
}

//它对应一个生命周期组件，eg : Activity, 观察 Ac 的 destroy 事件
internal class DestroyLifeCycleObserver(val lifeOwner: LifecycleOwner) : LifecycleObserver {

    private val disposableList = ArrayList<Disposable>()
    var requestRemoveLifecycleObserver: RequestRemoveLifecycleObserver? = null

    init {
        lifeOwner.lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        LogUtils.d(TAG, "${getKey()} OnLifecycleEvent ON_DESTROY , disposableList.size : ${disposableList.size}")
        disposableList.forEach {
            if (!it.isDisposed) {
                it.dispose()
            }
        }
        requestRemoveLifecycleObserver?.requestRemoveDestroyObserver(this)
    }

    fun addDisposable(disposable: Disposable) {
        if (disposable.isDisposed) return
        disposableList.add(disposable)
    }

    fun getKey() = lifeOwner.toString()
}

//它对应一个生命周期组件，eg : Activity, 观察 Ac 的 stop 事件
internal class StopLifeCycleObserver(private val lifeOwner: LifecycleOwner) : LifecycleObserver {

    private val disposableList = ArrayList<Disposable>()
    var requestRemoveLifecycleObserver: RequestRemoveLifecycleObserver? = null

    init {
        lifeOwner.lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onDestroy() {
        LogUtils.d(TAG, "${getKey()} OnLifecycleEvent ON_DESTROY , disposableList.size : ${disposableList.size}")
        disposableList.forEach {
            if (!it.isDisposed) {
                it.dispose()
            }
        }
        requestRemoveLifecycleObserver?.requestRemoveStopObserver(this)
    }

    fun addDisposable(disposable: Disposable) {
        if (disposable.isDisposed) return
        disposableList.add(disposable)
    }

    fun getKey() = lifeOwner.toString()

}

internal interface RequestRemoveLifecycleObserver {
    fun requestRemoveDestroyObserver(observer: DestroyLifeCycleObserver)
    fun requestRemoveStopObserver(observer: StopLifeCycleObserver)
}


/**
 * kotlin 扩展函数， 方便使用
 * */

//在 LifecycleOwner onDestroy 时释放 disposable
fun Disposable.disposeOnDestroy(lifeOwner: LifecycleOwner): Disposable {
    LogUtils.d(TAG, "life owner key : ${lifeOwner}")
    var lifecycleObserver = GlobalRxDisposeManager.getDestroyObserver(lifeOwner.toString())

    if (lifecycleObserver == null) {
        lifecycleObserver = DestroyLifeCycleObserver(lifeOwner)
        GlobalRxDisposeManager.addDestroyObserver(lifecycleObserver)
    }

    lifecycleObserver.addDisposable(this)

    return this
}

//在 LifecycleOwner onStop 时释放 disposable
fun Disposable.disposeOnStop(lifeOwner: LifecycleOwner): Disposable {
    LogUtils.d(TAG, "life owner key : ${lifeOwner}")
    var lifecycleObserver = GlobalRxDisposeManager.getStopObserver(lifeOwner.toString())

    if (lifecycleObserver == null) {
        lifecycleObserver = StopLifeCycleObserver(lifeOwner)
        GlobalRxDisposeManager.addStopObserver(lifecycleObserver)
    }

    lifecycleObserver.addDisposable(this)

    return this
}

