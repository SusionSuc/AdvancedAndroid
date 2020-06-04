
>文章来源自作者的Android进阶计划(https://github.com/SusionSuc/AdvancedAndroid)

本文不会太具体讲编写Gradle插件中用到的API,只是大致梳理一下如何编写一个Gradle插件。

这和是官方对于插件编写的介绍:https://docs.gradle.org/4.3/userguide/custom_plugins.html 。 本文的内容基本是对官方文档的翻译。

# 编写自定义插件

在Gradle中，插件是用来模块化和重用的组件。我们可以在插件中定义一些常用的方法，以及一些自定义`Task`。在`build.gradle`中可以使用`apply plugin : 'xxx'`来引入一个插件。
我们用的最多的就是 android gradle 插件。

```
buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:3.1.3'
    }
}
apply plugin: 'com.android.application'
```

## 编写一个简单的插件

我们知道在`build.gradle`文件中是可以直接写groovy代码的。如果一个插件的功能很简单，我们可以直接把这个插件定义在一个`xx.gradle`文件中:

```
//GreetingPlugin.gradle
class GreetingPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.task('hello') {
            doLast {
                println 'Hello from the GreetingPlugin'
            }
        }
    }
}
```

这个插件很简单，在当前工程下创建一个名为`hello`的task。这个任务就是打印一个简单的问候。在工程的`build.gradle`我们就可以引用这个插件:

```
apply from : 'GreetingPlugin.gradle'
```

运行这个任务:`gradle -q hello`。 输出为:`Hello from the GreetingPlugin`

*即定义一个自定义插件我们只需要实现`Plugin<Project>`接口。*

### 获取插件的配置

什么是插件的配置呢？比如我们常使用的 android gradle插件:

```
android {
    compileSdkVersion 27
}
```

这里`compileSdkVersion`就是android gradle为`android`这个域提供的一些可配置的属性。那么自定义一个插件如何可配置呢?

其实Gradle的`Project`关联了一个`ExtensionContainer`,`ExtensionContainer`中包含所有的插件的设置和属性，我们可以通过`Project`的API来添加一个`extension object`到`ExtensionContainer`中。这样我们就可以在`build.gralde`中配置这个`extension object`了。如下:

```
class GreetingPluginExtension {  //一个简单的 java bean
    String message = 'Hello from GreetingPlugin'
    //..当然可以添加更多属性
}

class GreetingPlugin implements Plugin<Project> {
    void apply(Project project) {
        //添加 greeting extension, 在apply插件, 如果ExtensionContainer中就会有greeting这个extension
        def extension = project.extensions.create('greeting', GreetingPluginExtension)
        project.task('hello') {
            doLast {
                println extension.message  //可以访问到在 build.gradle中配
            }
        }
    }
}

apply plugin: GreetingPlugin

//配置 greeting这个extension
greeting.message = 'Hi from Gradle'
```

有个疑问: 我们创建的`project.extensions.create('greeting', GreetingPluginExtension)`是什么时候放入到`ExtensionContainer`？

Gradle的整个构建分为3个阶段:  初始化阶段、配置阶段、执行阶段。  https://www.jianshu.com/p/a45286b08db0

我们自定义的`greeting`就是在配置阶段放入到`ExtensionContainer`中的。

# 复杂的自定义配置

当我们想将我们的插件共享给其他人或者我们的插件代码越来越多，我们希望把代码放在一个单独的工程时。我们可以创建一个单独的工程来管理我们的自定义插件。我们可以编译出一个`jar`包，或把这个`jar`包上传到maven给其他人使用。

步骤也非常简单:

### 使用gradle构建一个groovy工程，然后依赖gradle api

```
//build.gradle
apply plugin: 'groovy'

dependencies {
    compile gradleApi()
    compile localGroovy()
}
```
包含这两个依赖后，我们就可以用它们提供的API来编写我们自定义的gradle插件了。

### 声明插件的实现类

对于自定义的插件，Gradle有一个约定，我们需要在`META-INF/gradle-plugins`提供一个我们插件实现类的声明:

比如我们在`src/main/resources/META-INF/gradle-plugins/org.samples.greeting.properties`下定义了我们自定义插件的实现类为`org.gradle.GreetingPlugin`
``
implementation-class=org.gradle.GreetingPlugin
``

需要注意: *文件的名字必须是插件的id* ，并且官方文档指明，插件的id应该与包名相同。这样可以避免冲突。

### 编写插件实现

实现很简单，就是把我们上面编写的简单的插件代码，以groovy文件的形式放在我们这个单独的工程中就可以了。

### 发布插件到maven仓库

我们可以使用`maven`插件提供的`uploadArchives`来把我们的插件上传到`maven`,比如:

```
apply plugin : 'maven'

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: 'xxx') {
                authentication(userName: 'xx', password: 'xxx')
            }
            snapshotRepository(url: 'xxx') {
                authentication(userName: 'xx', password: 'xxx')
            }
            pom.project {
                artifactId = libArtifactId
                version = libVersion
                name = libArtifactId
                groupId = 'cxxxx'
            }
        }
    }
}
```

### 发布插件到本地

如果我们没有maven仓库的话，我们也可以先把插件放到本地，然后依赖本地maven仓库

```
//plugin.gradle


//定义上传的坐标
group 'com.susion.plugin'
version '0.0.4'

//artifactId 默认为工程名

//把这个插件上传的 “localRepository/libs” 目录下
uploadArchives {
    repositories {
        flatDir {
            name "localRepository"
            dir "localRepository/libs"
        }
    }
}
```

```
//主工程的build.gradle

buildscript {
    repositories {
        flatDir { // 本地的maven仓库
            name 'localRepository'
            dir "library/localRepository/libs"
        }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:2.3.0'
        classpath 'com.susion.plugin:library:0.0.4'     //插件的上传时的坐标         
    }
}
```

这样我们就可以在其他的工程中 apply 我们的插件了:

```
apply plugin: 'plugin id'
```

### 使用自定义的插件

将插件发布到maven后，就可以使用自定的插件了。

```
buildscript {
    ...
    dependencies {
        classpath group: 'org.gradle', name: 'customPlugin', version: '1.0-SNAPSHOT'
    }
}
apply plugin: 'org.samples.greeting'

```

# 为插件提供一个可以配置的DSL

## 内嵌一个 java bean

前面我们已经知道，我们可以创建一个包含一些简单属性的`extension object`(java bean)到`ExtensionContainer`。可是如果我们这个`java bean`中内嵌一个其他的java bean呢？ 那么我们还可以这么简单的在`build.gradle`中简单访问内嵌java bean 的属性呢?

答案是不能的，我们需要使用其他一些API来完成这个事情:`ObjectFactory`。我么还是直接看官方demo的使用吧:

```
class Person {
    String name
}

class GreetingPluginExtension {
    String message
    final Person greeter

    @javax.inject.Inject
    GreetingPluginExtension(ObjectFactory objectFactory) {
        greeter = objectFactory.newInstance(Person)
    }

    void greeter(Action<? super Person> action) {
        action.execute(greeter)
    }
}

class GreetingPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Create the extension, passing in an ObjectFactory for it to use
        def extension = project.extensions.create('greeting', GreetingPluginExtension, project.objects)
        project.task('hello') {
            doLast {
                println "${extension.message} from ${extension.greeter.name}"
            }
        }
    }
}

apply plugin: GreetingPlugin

greeting {
    message = 'Hi'
    greeter {
        name = 'Gradle'
    }
}
```

## 内嵌一个java bean集合

`Project.container(java.lang.Class)`可以实例化一个`NamedDomainObjectContainer`。传入的这个`Class`必须要有一个`name`属性，并且必须提供一个含义`name`参数的构造函数。
`NamedDomainObjectContainer`实际上实现了一个`Set`。可以理解为一个set集合。

```
class Book {
    final String name
    File sourceFile

    Book(String name) {
        this.name = name
    }
}

class DocumentationPlugin implements Plugin<Project> {
    void apply(Project project) {
        def books = project.container(Book) // 传入book class,返回 NamedDomainObjectContainer, 它是一个set
        books.all {  
            //遍历books中的每一个 Book， 并修改 sourceFile
            sourceFile = project.file("src/docs/$name")
        }
        //将容器添加为extension
        project.extensions.add('books', books)
        //project.extensions.books = books   向  ExtensionContainer 中添加一个`books`. 它是`NamedDomainObjectContainer<Book>`
    }
}

apply plugin: DocumentationPlugin

// Configure the container,  books是在插件被apply的时候添加到 ExtensionContainer中的
books {
    quickStart {  // quickStart作为 Book 的构造函数 name的参数
        sourceFile = file('src/docs/quick-start')
    }
    userGuide {

    }
    developerGuide {

    }
}

task books {
    doLast {
        books.each { book ->
            println "$book.name -> $book.sourceFile"
        }
    }
}
```

上面代码可能会对`books.all`这个方法有疑问: 看task的执行结果是对每一个 Book都修改了 `sourceFile`。 可是刚创建的`NamedDomainObjectContainer`,里面并没有对象呀？

我们可以看一下`all`这个API，其实它是`DomainObjectCollection`的API. `NamedDomainObjectContainer`为其子类:

```
all(Closure action) : Executes the given closure against all objects in this collection, and any objects subsequently added to this collection.
```

即: 对此集合中的所有对象以及随后添加到此集合的所有对象执行给定的闭包。








