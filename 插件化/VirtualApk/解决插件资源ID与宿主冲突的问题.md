>前面分析了`VirtualApk`[支持插件中的4大组件运行的原理](https://github.com/SusionSuc/AdvancedAndroid/blob/master/%E6%8F%92%E4%BB%B6%E5%8C%96/README.md)。本文就来讨论一下如何解决插件资源id和宿主资源id冲突的问题。
>本文会涉及到`Andoird资源的编译和打包`原理。因此对这方面的知识最好有一定的了解。可以参考`老罗`的[Andoird资源的编译和打包](https://blog.csdn.net/luoshengyang/article/details/8744683)一文。

## 为什么会冲突？为什么要解决资源id冲突？

首先宿主apk和插件apk是两个不同的apk，他们在编译时都会产生自己的`resources.arsc`。即他们是两个独立的编译过程。那么它们的`resources.arsc`中的资源id必定是有相同的情况。这就会出现问题了:

我们前面已经了解过，宿主在加载插件的资源的时候其实是新new了一个`Resources`，这个新的`Resources`是包含宿主和插件的资源的。所以一个`Resources`中就出现了资源id重复的情况，这样在运行的时候使用资源id来获取资源就会报错。

## 怎么解决呢？

目前一共有两种思路:

1. 修改aapt源码，定制aapt工具，编译期间修改PP段。(PP字段是资源id的第一个字节，表示包空间)

`DynamicAPK`的做法就是如此，定制aapt，替换google的原始aapt，在编译的时候可以传入参数修改PP段：例如传入0x05编译得到的资源的PP段就是0x05。对于具体实现可以参考这篇博客[Android中如何修改编译的资源ID值](https://blog.csdn.net/jiangwei0910410003/article/details/50820219)

2. 修改aapt的产物，即，编译后期重新整理插件Apk的资源，编排ID。

`VirtualApk`采用的就是这个方案。本文就大致看一下这个方案的实现。

## VirtualApk的解决方案

大体实现思路:*自定义gradle transform 插件，在apk资源编译任务完成后，重新设置插件的`resources.arsc`文件中的资源id,并更新`R.java`文件* 

比如，你在编译插件apk时设置了:

```
apply plugin: 'com.didi.virtualapk.plugin'

virtualApk {
    packageId = 0x6f //插件资源ID的PP字段
    targetHost = '../VirtualApk/app' // 宿主的目录
    applyHostMapping = true 
}
```

在运行编译插件apk的任务后，产生的插件的资源id的PP字段都是`0x6f`。

`VirtualApk`hook了`ProcessAndroidResources`task。这个task是用来编译Android资源的。`VirtualApk`拿到这个task的输出结果，做了以下处理:

1. 根据编译产生的`R.txt`文件收集插件中所有的资源

2. 根据编译产生的`R.txt`文件收集宿主apk中的所有资源

3. 过滤插件资源:过滤掉在宿主中已经存在的资源

4. 重新设置插件资源的资源ID

5. 删除掉插件资源目录下前面已经被过滤掉的资源

6. 重新编排插件`resources.arsc`文件中插件资源ID为新设置的资源ID

7. 重新产生R.java文件

*下面呢我们就来看下具体代码。这块水很深。所以下面的代码就当伪代码看一下就好，我们的主要目的是理解大致的实现思路。*

## 粗略浏览具体实现代码

### 根据`R.txt`文件收集插件中所有的资源

`R.txt`文件是在编译资源过程中产生的资源ID记录文件，在`build/intermediates/symbols/xx/xx/R.txt`可以找到这个问题，它的格式如下:

```
int anim abc_fade_in 0x7f010000  
int anim abc_fade_out 0x7f010001
.....
```

看一下具体代码:

```
  private void parseResEntries(File RSymbolFile, ListMultimap allResources, List styleableList) {
        RSymbolFile.eachLine { line ->
            /**
             *  Line Content:
             *  Common Res:  int string abc_action_bar_home_description 0x7f090000
             *  Styleable:   int[] styleable TagLayout { 0x010100af, 0x7f0102b5, 0x7f0102b6 }
             *               or int styleable TagLayout_android_gravity 0
             */
            if (!line.empty) {
                def tokenizer = new StringTokenizer(line)
                def valueType = tokenizer.nextToken()     // value type (int or int[])
                def resType = tokenizer.nextToken()      // resource type (attr/string/color etc.)
                def resName = tokenizer.nextToken()
                def resId = tokenizer.nextToken('\r\n').trim()

                if (resType == 'styleable') {
                    styleableList.add(new StyleableEntry(resName, resId, valueType))
                } else {
                    allResources.put(resType, new ResourceEntry(resType, resName, Integer.decode(resId)))
                }
            }
        }
    }
```

即收集所有资源:`资源名称`、`资源ID`、`资源类型`等。然后保存在集合中:`allResources`和`styleableList`

### 根据编译产生的`R.txt`文件收集宿主apk中的所有资源

和第一步相同

### 过滤插件资源:过滤掉在宿主中已经存在的资源

```
    private void filterPluginResources() {
        allResources.values().each {  // allResources 就是前面解析出来的插件的所有资源
            def index = hostResources.get(it.resourceType).indexOf(it)   
            if(index >= 0){  //插件的资源在宿主中存在
                it.newResourceId = hostResources.get(it.resourceType).get(index).resourceId //把这个一样的插件资源的id设置成宿主的id
                hostResources.get(it.resourceType).set(index, it) //在宿主中更新这个资源
            } else { //插件的资源在宿主中不存在
                pluginResources.put(it.resourceType, it)  
            }
        }

        allStyleables.each {
            def index = hostStyleables.indexOf(it)
            if(index >= 0) {
                it.value = hostStyleables.get(index).value
                hostStyleables.set(index, it)
            } else {
                pluginStyleables.add(it)
            }
        }
    }
```

即经过上面的操作，`pluginResources`只含有插件的资源。这份资源和宿主的资源集合没有交集，即没有相同的资源。

### 重新设置插件的资源ID

这一步就是核心了，逻辑很简单，即基于自定义的`PP`字段的值，修改上面已经收集好的`pluginResources`中资源的资源ID:

```
 private void reassignPluginResourceId() {

        //先根据 typeId 把前面收集到的资源排序
        def resourceIdList = []
        pluginResources.keySet().each { String resType ->
            List<ResourceEntry> entryList = pluginResources.get(resType)
            resourceIdList.add([resType: resType, typeId: entryList.empty ? -100 : parseTypeIdFromResId(entryList.first().resourceId)])
        }

        resourceIdList.sort { t1, t2 ->
            t1.typeId - t2.typeId
        }

        //重新设置插件的资源id
        int lastType = 1
        resourceIdList.each {
            if (it.typeId < 0) {
                return
            }
            def typeId = 0
            def entryId = 0
            typeId = lastType++
            pluginResources.get(it.resType).each {
                it.setNewResourceId(virtualApk.packageId, typeId, entryId++)  // virtualApk.packageId 即我们在gradle中自定义的 packageId
            }
        }

        List<ResourceEntry> attrEntries = allResources.get('attr')

        pluginStyleables.findAll { it.valueType == 'int[]'}.each { StyleableEntry styleableEntry->
            List<String> values = styleableEntry.valueAsList
            values.eachWithIndex { hexResId, idx ->
                ResourceEntry resEntry = attrEntries.find { it.hexResourceId == hexResId }
                if (resEntry != null) {
                    values[idx] = resEntry.hexNewResourceId
                }
            }
            styleableEntry.value = values
        }
    }
```

ok，经过上面的处理，`pluginResources`中的资源的资源id都是重新设置的新的资源Id。

### 删除掉插件资源目录下前面已经被过滤掉的资源

我们前面经过和宿主的资源对比后，可能已经删除了插件中的一些资源id，但是对应的文件还没有删除，因此需要把文件也删除掉:

```
 void filterResources(final List<?> retainedTypes, final Set<String> outFilteredResources) {
        def resDir = new File(assetDir, 'res')   //遍历插件的资源目录
        resDir.listFiles().each { typeDir ->
            def type = retainedTypes.find { typeDir.name.startsWith(it.name) }
            if (type == null) {   //插件过滤后的资源已经不含有这个目录了，直接删除掉
                typeDir.listFiles().each {
                    outFilteredResources.add("res/$typeDir.name/$it.name")
                }
                typeDir.deleteDir()
                return  //这个return 是跳过这次循环
            }

            def entryFiles = typeDir.listFiles()
            def retainedEntryCount = entryFiles.size()

            entryFiles.each { entryFile ->
                def entry = type.entries.find { entryFile.name.startsWith("${it.name}.") }
                if (entry == null) {   //逻辑同上
                    outFilteredResources.add("res/$typeDir.name/$entryFile.name")
                    entryFile.delete()
                    retainedEntryCount--
                }
            }

            if (retainedEntryCount == 0) {
                typeDir.deleteDir()
            }
        }
    }
```

### 重新编排插件`resources.arsc`文件中插件资源ID为新设置的资源ID

这个代码就不列了，感兴趣可以查看`VirtualApk`源代码 : [ArscEditor.slice()](https://github.com/didi/VirtualAPK/blob/master/virtualapk-gradle-plugin/src/main/groovy/com.didi.virtualapk/aapt/ArscEditor.groovy)

### 重新产生R.java文件

```
 public static void generateRJava(File dest, String pkg, ListMultimap<String, ResourceEntry> resources, List<StyleableEntry> styleables) {
        if (!dest.parentFile.exists()) {
            dest.parentFile.mkdirs()
        }

        if (!dest.exists()) {
            dest.createNewFile()
        }

        dest.withPrintWriter { pw ->
            pw.println "package ${pkg};"
            pw.println "public final class R {"

            resources.keySet().each { type ->
                pw.println "    public static final class ${type} {"
                resources.get(type).each { entry ->
                    pw.println "        public static final int ${entry.resourceName} = ${entry.hexNewResourceId};"
                }
                pw.println "    }"
            }

            pw.println "    public static final class styleable {"
            styleables.each { styleable ->
                    pw.println "        public static final ${styleable.valueType} ${styleable.name} = ${styleable.value};"
            }
            pw.println "    }"
            pw.println "}"
        }
    }
```

>欢迎Star我的[Android进阶计划](https://github.com/SusionSuc/AdvancedAndroid),看更多干货。