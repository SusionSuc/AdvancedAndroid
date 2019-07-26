
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import io.reactivex.disposables.Disposable


object GlobalRxDisposeManager {

    private val rxLifecycleObservers = HashMap<String, RxLifeCycleObserver?>()

    fun getLifecycleObserver(key: String): RxLifeCycleObserver? {
        return rxLifecycleObservers[key]
    }

    fun addLifecycleObserver(lifeCycleObserver: RxLifeCycleObserver) {
        rxLifecycleObservers[lifeCycleObserver.getKey()] = lifeCycleObserver
        lifeCycleObserver.requestRemoveLifecycleObserver = object : RxLifeCycleObserver.RequestRemoveLifecycleObserver {
            override fun requestRemove(observer: RxLifeCycleObserver) {
                rxLifecycleObservers.remove(observer.getKey())  //释放引用，避免内存泄漏
                LogUtils.d(TAG, "current rxLifecyclerObservers size : ${rxLifecycleObservers.size}")
            }
        }
    }
}


fun Disposable.disposeByLifeCycle(lifeOwner: LifecycleOwner): Disposable {

    var lifecycleObserver = GlobalRxDisposeManager.getLifecycleObserver(RxLifeCycleObserver.createKey(lifeOwner))

    if (lifecycleObserver == null) {
        lifecycleObserver = RxLifeCycleObserver(lifeOwner)
        GlobalRxDisposeManager.addLifecycleObserver(lifecycleObserver)
    }

    lifecycleObserver.addDisposable(this)

    return this
}

//它对应一个生命周期组件， Activity
class RxLifeCycleObserver(val lifeOwner: LifecycleOwner) : LifecycleObserver {

    companion object {
        fun createKey(lifeOwner: LifecycleOwner) = lifeOwner.javaClass.name
    }

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
        requestRemoveLifecycleObserver?.requestRemove(this)
    }

    fun addDisposable(disposable: Disposable) {
        if (disposable.isDisposed) return
        disposableList.add(disposable)
    }

    fun getKey() = lifeOwner.javaClass.name

    interface RequestRemoveLifecycleObserver {
        fun requestRemove(observer: RxLifeCycleObserver)
    }
}

