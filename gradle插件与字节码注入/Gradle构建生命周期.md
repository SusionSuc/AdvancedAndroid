了解Gradle的整个构建生命周期之前我们需要先了解一下这些概念:

## Gradle项目的组成

先来看一个常见的用Gradle构建的Android的项目(比如这个项目叫Search,主要包含一个搜索library和对library测试的Demo)

    Search 
        --demo
            --src
            --build.gradle
        --library
            --src
            --build.gradle
        --build.gradle
        --setting.gradle

相信这个目录对大部分android工程师来说都是挺熟悉的。我们来大致看一下Gradle是如何理解这个目录组织的。

### gradle中的工程

其实我们可以认为如果在一个目录下存在build.gradle文件，那么就可以认为这是一个gradle工程(即Project,工程名即目录的名字)。

`build.gradle`主要用于对工程进行配置。比如配置版本、依赖、使用的插件等等。

### 根工程与子工程

上面的Search项目有三个工程: Search、demo、library。从目录结构上看,demo、library好像是Search的子工程，但是如何在gradle中描述这种关系呢?

#### setting.gradle

`setting.gradle`文件是gradle用来初始化整个构建工程树的。默认情况下，如果不配置这个文件，只是在Search目录下执行构建，那么gradle是不会去构建demo和library这两个
项目的。只有在`setting.gradle`中配置了这两个工程，它们才算是Search的子工程。才会参与到Search项目的构建。如何配置呢？

如果demo和library项目的目录和`settings.gralde`是平级的，那么可以直接这样配置:
```
    include 'demo','library'
```
gradle会在当前根目录下查找 `demo`和`library`文件夹，并认为他们是一个工程，将他们加入到构建工程树中。

```
include 'services:api'   -> gradle会去查找 services/api
```

如果demo和library项目的目录与`settings.gradle`不是平级，或者在其他的地方,那么就需要指定这两个工程的目录:

```
    include 'demo','library'
    project(':demo').projectDir = new File('dir path')
```

### Task

举一个我们经常运行的命令 : gradle build

这里的`build`就是一个task，在gradle中task可以理解为一个操作，比如：打jar包、编译Java代码、上传jar包到maven。除了这些还有一些比较复杂的task，比如`build`,它就是由很多task组成的。
一个Project可以包含很多Task。

我们可以在一个工程根目录下运行`gradle tasks`查看，这个工程包含哪些task。

## Gradle的整个构建生命周期

gradle的构建时再依赖分析完成后才开始的,简单来说就是你配置的所有依赖下载完毕后才会开始构建。在gradle中，整个构建分为3个过程：Initialization、Configuration、Execution。下面就针对这3个阶段简单看一下

### Initialization

在初始化阶段，gradle要确定的一件比较重要的事是:这次构建是单工程构建还是多工程。即gradle会去寻找`settings.grale`文件。找的大致逻辑是：

1. 当前目录查找`settings.grale`文件。查找到后开始构建
2. 如果当前目录没有，则找当前目录的父目录有没有，如果有，则按照父目录的settings.grale开始构建
3. 如果没有，则如果当前目录存在build.grale,进行单工程构建

确定好是单工程构建还是多工程构建后, 每一个参与构建的工程都会生成一个`Project`对象。 

不过gradle这种查找`settings.gradle`来确定参与构建的工程的机制也存在一个问题：如果我们就是想构建这一个功能呢？ 我们可以使用 `-u` 来使gradle不去查找`settings.gradle`

```
gradle -u build
```

### Configuration

在确定了参与构建的工程后，在这个阶段会对每个工程的`build.gralde`进行逐行执行，会进行如下任务

1. 加载插件
2. 加载依赖
3. 加载task
4. 执行脚本，自定义的插件DSL代码，本身gradle支持的API等
....

### Execution

在这个阶段，gradle会根据参与构建的工程，创建这次任务执行的子任务，然后逐一执行这些任务。


> 关于gradle构建生命周期更细节的一些内容，可以参考官方文档 : https://docs.gradle.org/4.3/userguide/build_lifecycle.html





