## Fresco图片预加载实验

## Wifi状态检测实验

```
object WifiStateChecker {

    private var mWifiState = WifiManager.WIFI_STATE_DISABLED

    fun init(context: Context) {
        val filter = IntentFilter()
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)  //wifi开关变化广播
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_DISABLED)
            }
        }, filter)

        Observable.just(context)
                .subscribeOn(LightExecutor.createScheduler(ModuleCate.MATRIX))
                .autoDisposable(ScopeProvider.UNBOUND)
                .subscribe({
                    mWifiState = if (CUtils.isWifiNetwork(it)) WifiManager.WIFI_STATE_ENABLED else WifiManager.WIFI_STATE_DISABLED
                }, {})
    }

    fun isUseWifi(): Boolean {
        return when (mWifiState) {
            WifiManager.WIFI_STATE_ENABLED -> true
            else -> false
        }

    }
}
```

## Android HttpDns