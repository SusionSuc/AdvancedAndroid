# booster分析- 资源压缩 (booster-task-compression)

>这个组件主要做了两件事: **冗余资源的删除**和**图片资源的压缩**。

为了更好的理解`booster`对于资源压缩的处理，我们先来回顾一下`android build gradle`在编译`app`资源时都经过了哪些步骤:

## 回顾 Android App 资源编译步骤

对于资源编译有哪些步骤我并没有找到比较详细官方文档，不过我们可以通过查看`com.android.tools.build:gradle`的源码来了解这个过程。构建一个`app`包所涉及的到`GradleTask(比如assembleRelease)`的源码大概位于`ApplicationTaskMamager.java`文件中:

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

上面我只截取了`ApplicationTaskMamager.createTasksForVariantScope()`部分代码，`createTasksForVariantScope()`就是用来创建很多`Task`来构建一个可运行的`App`的。通过这个方法我们可以看到构建一个`App`包含下列步骤:

1. 下载依赖
2. 合并`Manifest`文件(`MergeApkManifestsTask`)
3. 合并`res`资源(`MergeResourcesTask`)
4. 合并`assets`资源(`MergeAssetsTask`)

>上面我省略了很多步骤没有列出来。

**booster资源压缩的实现原理就是创建了一些`Task`插入在上面的步骤之间来完成自定义的操作**

## 冗余资源的删除

这个操作会在`gradle app 构建`完成`MergeResourcesTask`之后进行:

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

从上面代码可以看出:根据当前不同的`AAPT`版本创建不同的冗余图片移除任务(操作的图片格式为`png`, 但不包括.9.png)。

### AAPT 的冗余资源的移除

如果对资源编译采用的是`AAPT`，则执行的任务为`RemoveRedundantImages`:

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

可以看到`RemoveRedundantImages`并没有做什么具体的操作。实际上`gradle`会在`AAPT`资源合并操作之前移除冗余的资源，具体规则是:

>默认情况下,`Gradle`会合并同名的资源，如可能位于不同资源文件夹中的同名可绘制对象。这一行为不受`shrinkResources`属性控制，也无法停用，因为当多个资源与代码查询的名称匹配时，有必要利用这一行为来避免错误。只有在两个或更多个文件具有完全相同的资源名称、类型和限定符时，才会进行资源合并。`Gradle`会在重复项中选择它认为最合适的文件（根据下述优先顺序），并且只将这一个资源传递给`AAPT`，以便在APK文件中分发。

>`Gradle`会在以下位置查找重复资源：
>- 与主源集关联的主资源，通常位于 src/main/res/。
>- 变体叠加,来自编译类型和编译特性。
>- 库项目依赖项。

>`Gradle`会按以下级联优先顺序合并重复资源 : 依赖项 → 主资源 → 编译特性 → 编译类型

更具体的合并规则可查看: [合并重复资源](https://developer.android.com/studio/build/shrink-code#merge-resources)

**当然`gradle`的资源合并操作是必须的**

### AAPT2 的冗余资源的移除

>`Android Gradle Plugin 3.0.0`及更高版本默认会启用`AAPT2`。相较于`AAPT`,`AAPT2`会利用增量编译加快app打包过程中资源的编译。对于`AAPT2`更加详细的介绍可以参考 : https://developer.android.com/studio/command-line/aapt2

当app编译使用的是`AAPT2`时,`booster RemoveRedundantFlatImages`的处理:

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

代码比较长，其实`RemoveRedundantFlatImages`所做的操作是: **在资源合并后，对于同名的png图片，它会取`density`最高的图片，然后把其他的图片删除**

比如你有下面3张启动图:

- mipmap-hdpi -> ic_launcher.png
- mipmap-xhdpi -> ic_launcher.png
- mipmap-xxxhdpi -> ic_launcher.png

经`booster`处理后就会剩下`mipmap-xxxhdpi -> ic_launcher.png`这一张图片打包到apk中。

## 图片资源的压缩

`booster`图片压缩的大致实现是:

1. 对于`minSdkVersion > 17`的应用，在资源编译过程中使用`cwebp`命令将图片转为`webp`格式。
2. 对于`minSdkVersion < 17`的应用，在资源编译过程中使用`pngquant`命令对图片进行压缩。

对于这两个工具的详细了资料可以参考下面文章:

> webp使用指南 : https://developers.google.com/speed/webp/docs/using

> pngquant使用实践 : https://juejin.im/entry/587f14378d6d810058a18e1f

### 具体实现

图片资源的压缩分为两步:

1. `assets`下的图片资源压缩
2. `res`下的图片资源压缩

>这里直接压缩**assets**下图片资源是存在一些问题的:如果工程中引入了`flutter`,`flutter`中对图片资源是明文引用的,因此`booster`将图片转为`webp`格式的话会造成`flutter`中图片失效。因此这点要注意。

以`res`的资源压缩为例来做分析:

### res下的图片压缩

>SimpleCompressionTaskCreator.java
```
override fun createResourcesCompressionTask(variant: BaseVariant, results: CompressionResults): Task {
    val aapt2 = variant.project.aapt2Enabled

    //1. 创建资源压缩工具
    val install = variant.createCompressionToolIfNotExists() 

    //2. 创建资源压缩任务
    return variant.project.tasks.create("compress${variant.name.capitalize()}ResourcesWith${cmdline.name.substringBefore('.').capitalize()}", getCompressionTaskClass(aapt2).java) {
        it.outputs.upToDateWhen { false }
        it.cmdline = cmdline
        it.variant = variant
        it.results = results
        it.sources = { variant.scope.mergedRes.search(if (aapt2) ::isFlatPng else ::isPng) }
    }.apply {
        dependsOn(install, variant.mergeResourcesTask)
        variant.processResTask.dependsOn(this)
    }
}
```

将图片转为`webp`格式需要使用`cwebp`命令，因此要先创建可以执行的`cwebp`命令, `createCompressionToolIfNotExists()`就是使`cwebp`命令可执行(其实就是改了命令的执行权限，具体细节可参考源码))。

`getCompressionTaskClass(aapt2).java`是创建了用于压缩图片资源的任务,如果是使用`cwebp`压缩的话，最终使用的任务是`CwebpCompressFlatImages`:

```
internal open class CwebpCompressImages : CompressImages() {
    open fun compress(filter: (File) -> Boolean) {
        // cmdline.executable!!.absolutePath 指向的是 cwebp 命令
        sources().parallelStream().filter(filter).map { input ->
            val output = File(input.absolutePath.substringBeforeLast('.') + ".webp")
            ActionData(input, output, listOf(cmdline.executable!!.absolutePath, "-mt", "-quiet", "-q", "80", "-o", output.absolutePath, input.absolutePath))
        }.forEach {
            val s0 = it.input.length()
            val rc = project.exec { spec ->
                spec.isIgnoreExitValue = true
                spec.commandLine = it.cmdline
            }
            when (rc.exitValue) {
                0 -> {
                    val s1 = it.output.length()
                    if (s1 > s0) {
                        results.add(CompressionResult(it.input, s0, s0, it.input))
                        it.output.delete()
                    } else {
                        results.add(CompressionResult(it.input, s0, s1, it.input))
                        it.input.delete()
                    }
                }
                else -> {
                    logger.error("${CSI_RED}Command `${it.cmdline.joinToString(" ")}` exited with non-zero value ${rc.exitValue}$CSI_RESET")
                    results.add(CompressionResult(it.input, s0, s0, it.input))
                    it.output.delete()
                }
            }
        }
    }
    ...
}
```

`CompressImages`继承自`DefaultTask`。可以看到`CwebpCompressImages`具体做的事是: **执行cwbp命令,将图片转换为webp格式**

>上面其实跳过了很多详细步骤，不过大概核心代码已经列出，如果有兴趣可以翻阅`booster`源码查看详细实现。

