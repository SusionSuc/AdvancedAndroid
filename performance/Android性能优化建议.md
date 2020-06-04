
>最近看了一本腾讯测试同学写的书 :《Android移动性能实战》,书中介绍了很多如何检测Android性能问题的工具比如磁盘、内存、CPU、电池等。不过也指出了一些在开发过程中需要注意的点:

# 磁盘

## SharedPreferences

我们知道`SharedPreferences`底层是使用`xml`文件来实现的。所以对于`SharedPreferences`的操作其实是`I/O`操作,是耗时操作。

### commit 

每一次`commit`的调用都会对应一次文件的打开和关闭。`commit`是同步操作，`apply`是异步操作。

>最佳实践

减少`commit`的次数； 在一个逻辑操作(方法)中不要多次`commit`，`commit`应放在最后。或者使用缓存来保存多次写入的数据，最后提交`commit`。

如果对数据的实时性没有要求，可以使用`apply`来代替`commit`

### flag的保存与使用

我们通常会使用`SharedPreferences`来保存一些flag。但是要注意不要随意使用。比如你在一个`Recycleview`的卡片中，根据一个flag来做不同UI的判断:

```
class SimpleCard : LinearLayout, AdapterItem{
    fun bindData(...){
        if(Sp.getBoolean(xxx)){
            ...
        }    
    }
}
```

因为`SimpleCard`在`Recycleview`中频繁被`bindData`, 因此`SP.getBoolean(xxx)`会被频繁调用,前面已经说了`SP`的读或写是io操作。这种写法势必会造成UI卡顿。

>最佳实践

flag一般来说在app启动的时候就已经确定了，所有我们只需要获取一次。可以提前初始化这些flag:

//比如在某个Activity的onCreate
```
    runOnIoThread({
        FlagCenter.initAllFlag()
    })
```

//SimpleCard.bindData
```
 fun bindData(){
        if(FlagCenter.useStyle1){
            ...
        }
    }
```

## ObjectOutputStream

利用它我们可以把对象保存到磁盘中。不过它有一个特点:在保存对象的时候，每个数据成员会带来一次`I/O`操作。因此如果你对象很多、属性很复杂时，`ObjectOutputStream`的`I/O`操作会异常凶猛。

>最佳实践

最好在`ObjectOutputStream`上再封装一个输出流,比如`BufferedOutputStream`。先把对象写入到这个流中，然后再使用`ObjectOutputStream`保存到磁盘。

## 合理设置buffer

在读一个文件我们一般会设置一个`buffer`。即先把文件读到`buffer`中，然后再读取`buffer`的数据。所以: 真正对文件的次数 =  文件大小 / buffer大小 。  所以如果你的buffer比较小的话，那么读取文件的次数会非常多。当然在写文件时buffer是一样道理的。

很多同学会喜欢设置`1KB`的buffer，比如`byte buffer[] = new byte[1024]`。如果要读取的文件有20KB， 那么根据这个buffer的大小，这个文件要被读取20次才能读完。

>最佳实践 -> buffer应该设置多大呢？

java默认的buffer为8KB，最少应该为4KB。那么如何更智能的确定buffer大小呢？

1. buffer的大小不能大于文件的大小。
2. buffer的大小可以根据文件保存所挂载的目录的block size, 什么意思呢？ 来看一下`SQLiteGlobal.java`是如何确定buffer大小的 :

```
public static int getDefaultPageSize() { 
    return SystemProperties.getInt("debug.sqlite.pagesize", new StatFs("/data").getBlockSize());
}
```

## Bitmap的解码

在Android4.4以上的系统上，对于Bitmap的解码，`decodeStream()`的效率要高于`decodeFile()`和`decodeResource()`, 而且高的不是一点。所以解码`Bitmap`要使用`decodeStream()`，同时传给`decodeStream()`的文件流是`BufferedInputStream`

>最佳实践

```
val bis =  BufferedInputStream(FileInputStream(filePath))
val bitmap = BitmapFactory.decodeStream(bis,null,ops)
```

# 内存

在虚拟机的`Heap`内存使用超过堆内存最大值就会发生OOM。当手机内存低于内存警戒线时，占用内存越多的APP越有可能被`Low Memory Killer`给杀掉。

## 内存泄漏

### Activit内存泄漏

Activity对象会间接或者直接引用View、Bitmap等，所以一旦无法释放，会占用大量内存。并且Activity在Destroy的情况下，更新UI是会引发crash的。而Activity内存泄漏最常见的就以下几种case:

1. 生命周期比Activtiy长的对象持有了Activity的引用。比如在`getSystemService`时使用了`Activity`

2. Activity的内部类作为一个异步的回调监听。比如定义了一个`Handler`内部类，这时候`Handler`默认就会引用`Activity`

>最佳实践

在使用`getSystemService`方法时尽量使用`Application`， 比如: `applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE)` 

Activity中的`Handler`定义为静态内部类(解除对Activity的引用),并使用`WeakReference<Activity>`来引用Activity。

## 图片

### Feed流中的图片如果可以应尽可能降低所占内存大小

对于要加载的图片应做压缩，并在适当的情况下可以在解码时降低质量

>最佳实践

对于无透明效果、比较小的图片可以使用`RGB_565`格式来解码。

在设置解码图片的`inSmapleSize`参数时应参考`要显示的View的宽与高`来显示恰当的值。

### 图片资源不要放在错误的目录

Android的drawable分为好几个层级,比如:drawable、drawable-hdpi、drawable-ldpi、drawable-mdpi等，其实就是对应不同的屏幕密度。Android在获取某个屏幕密度的图片时会去对应的drawable目录下寻找，如果找不到就会取相近目录的资源。但这里是存在一个问题的，举个例子:

比如一张 800 * 480 的图片放置在了`ldpi`目录。如果480dpi(xxxhdpi)的屏幕要显示这张图片，那么他就会把这张图片放大4倍，即 3200 *1920,然后再去解码。所以图片在内存中被放大了4倍。如果这种case多了，内存很容易爆掉的。

>最佳实践

尽量为设计师要高品质图片让后往高密度目录下放，这样在低密度上`放大倍数是小于1的`，在保证画质的前提下，内存也是可控的。

### SparseArray与ArrayMap

`SparseArray`也是一个map,它的key必须为int类型， 在数据量不大的情况下(千级以内)，它的性能会要比`HashMap`好，并且更升内存。

`SparseArray与ArrayMap`的key可以为任意类型，当数据量比较小时也可以使用它来代替`HashMap`。

欢迎关注我的[Android进阶计划](https://github.com/SusionSuc/AdvancedAndroid)看更多干货
