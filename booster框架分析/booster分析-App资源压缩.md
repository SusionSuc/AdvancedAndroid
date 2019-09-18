# booster分析- 资源压缩 (booster-task-compression)

>这个组件主要做了2件事: **冗余资源的删除**和**图片资源的压缩**。

为了更好的理解`booster`对于资源压缩的处理，我们先来回顾一下`android build gradle`在编译`app`资源时都经过了哪些步骤。

## 回顾 Android App 资源编译步骤

对于资源编译有哪些步骤我并没有找到相关比较详细官方文档，不过我们可以通过查看`com.android.tools.build:gradle`的源码来了解这个过程。构建一个`app`包所涉及的到`Gradle Task`的源码位于`ApplicationTaskMamager.java`文件中:

>ApplicationTaskManager.java
```
@Override
public void createTasksForVariantScope( @NonNull final TaskFactory tasks, @NonNull final VariantScope variantScope) {
    BaseVariantData variantData = variantScope.getVariantData();
    ...
    // Create all current streams (dependencies mostly at this point)
    createDependencyStreams(tasks, variantScope);

    ...
    // Add a task to process the manifest(s)
    recorder.record(
            ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
            project.getPath(),
            variantScope.getFullVariantName(),
            () -> createMergeApkManifestsTask(tasks, variantScope));

    ...

    // Add a task to merge the resource folders
    recorder.record(
            ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
            project.getPath(),
            variantScope.getFullVariantName(),
            (Recorder.VoidBlock) () -> createMergeResourcesTask(tasks, variantScope, true));

    // Add a task to merge the asset folders
    recorder.record(
            ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
            project.getPath(),
            variantScope.getFullVariantName(),
            () -> createMergeAssetsTask(tasks, variantScope, null));

    ...
}
```

上面我只截取了`ApplicationTaskMamager.createTasksForVariantScope()`部分代码，`createTasksForVariantScope()`就是用来创建很多`Task`来构建一个可运行的`App`的。通过这个方法我们可以看到构建一个`App`包含:

1. 下载依赖
2. 合并`Manifest`文件(`MergeApkManifestsTask`)
3. 合并`res`资源(`MergeResourcesTask`)
4. 合并`assets`资源(`MergeAssetsTask`)

>上面我省略了一些步骤没有列出来。

**booster资源压缩的实现原理就是创建了一些`Task`插入在上面的步骤之间来完成自定义的操作**

## 冗余资源的删除

这个操作会在`android gradle `完成`MergeResourcesTask`之后进行。

```
//移除冗余资源的 task， 执行位于资源合并之后
val klassRemoveRedundantFlatImages = if (aapt2) RemoveRedundantFlatImages::class else RemoveRedundantImages::class

val reduceRedundancy = variant.project.tasks.create("remove${variant.name.capitalize()}RedundantResources", klassRemoveRedundantFlatImages.java) {
    it.outputs.upToDateWhen { false }
    it.variant = variant
    it.results = results
    it.sources = { variant.scope.mergedRes.search(pngFilter) }
}.dependsOn(variant.mergeResourcesTask)
```

从上面代码可以看出:根据当前不同的`aapt`版本创建不同的图片压缩任务，并且图片压缩任务操作的图片格式为`png`(不包括.9.png)。

### AAPT 的冗余资源的移除

如果对资源编译采用的是`aapt`，则执行的任务为`RemoveRedundantImages`:

```
open class RemoveRedundantImages: DefaultTask() {

    lateinit var variant: BaseVariant

    lateinit var results: CompressionResults

    lateinit var sources: () -> Collection<File>

    @TaskAction
    open fun run() {
        TODO("Reducing redundant resources without aapt2 enabled has not supported yet")
    }
}
```

可以看到`RemoveRedundantImages`并没有做什么具体的操作。实际上`aapt`在资源合并时会移除冗余的资源，具体规则是:

>默认情况下，Gradle 还会合并同名的资源，如可能位于不同资源文件夹中的同名可绘制对象。这一行为不受 shrinkResources 属性控制，也无法停用，因为当多个资源与代码查询的名称匹配时，有必要利用这一行为来避免错误。

>只有在两个或更多个文件具有完全相同的资源名称、类型和限定符时，才会进行资源合并。Gradle 会在重复项中选择它认为最合适的文件（根据下述优先顺序），并且只将这一个资源传递给 AAPT，以便在 APK 文件中分发。

>Gradle 会在以下位置查找重复资源：
>- 与主源集关联的主资源，通常位于 src/main/res/。
>- 变体叠加，来自编译类型和编译特性。
>- 库项目依赖项。

>Gradle 会按以下级联优先顺序合并重复资源 : 依赖项 → 主资源 → 编译特性 → 编译类型

更具体的合并规则可查看: [合并重复资源](https://developer.android.com/studio/build/shrink-code#merge-resources)

### AAPT2 的冗余资源的移除

>`Android Gradle Plugin 3.0.0`及更高版本默认会启用`AAPT2`。相较于`AAPT`,`AAPT2`会利用增量编译加快app打包过程中资源的编译。对于`AAPT2`更加详细的介绍可以参考 : https://developer.android.com/studio/command-line/aapt2

对于`AAPT2`,看一下`RemoveRedundantFlatImages`的处理:

```
internal open class RemoveRedundantFlatImages : RemoveRedundantImages() {
    @TaskAction
    override fun run() {
        val resources = sources().parallelStream().map {
            it to it.metadata
        }.collect(Collectors.toSet())

        resources.groupBy({
            it.second.resourceName.substringBeforeLast('/')   // 同文件夹下的文件
        }, {
            it.first to it.second
        }).forEach { entry ->
            entry.value.groupBy({
                it.second.resourceName.substringAfterLast('/')
            }, {
                it.first to it.second
            }).map { group ->
                group.value.sortedByDescending {
                    it.second.config.screenType.density // 按密度降序排序
                }.takeLast(group.value.size - 1)  //同名文件，取密度最大的
            }.flatten().parallelStream().forEach {
                try {
                    if (it.first.delete()) {  // 删除冗余的文件
                        val original = File(it.second.sourcePath)
                        results.add(CompressionResult(it.first, original.length(), 0, original))
                    } else {
                        logger.error("Cannot delete file `${it.first}`")
                    }
                } catch (e: IOException) {
                    logger.error("Cannot delete file `${it.first}`", e)
                }
            }
        }
    }
}
```

代码比较长，其实`RemoveRedundantFlatImages`所做的操作是: **在资源合并后，对于同目录下同名的png图片，它会取`density`最高的图片，然后把其他的图片删除**

>其实我在测试过程中发现`AAPT2`也会有类似于`AAPT`的冗余资源合并的操作，但是我暂时并没有找到相关具体合并细节。`RemoveRedundantFlatImages`可能是覆盖了`AAPT2`的合并操作规则。因为经它操作后不会有重复的`png`资源了。


## 图片资源的压缩

`booster`图片压缩的大致实现是:

1. 对于`minSdkVersion > 17`的应用，在资源编译过程中将图片转为`webp`格式
2. 对于`minSdkVersion < 17`的应用，在资源编译过程中使用`pngquant`对图片进行压缩

对于这两个工具的详细了解可以参考下面资料:

> webp使用指南 : https://developers.google.com/speed/webp/docs/using

> pngquant使用实践 : https://juejin.im/entry/587f14378d6d810058a18e1f

### 具体实现






https://fucknmb.com/2017/10/31/aapt2%E8%B5%84%E6%BA%90compile%E8%BF%87%E7%A8%8B/#comments  appt2 compile

>TaskManager.createAndroidTasks()
>TaskManager.createAndroidTestVariantTasks()
>ApplicationTaskManager.createTasksForVariantScope()