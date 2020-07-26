
- [1. 依赖注入 : Dependency Injection，DI](#1-依赖注入--dependency-injectiondi)
    - [1.1. 什么是依赖?](#11-什么是依赖)
    - [1.2. 依赖存在的问题](#12-依赖存在的问题)
    - [1.3. 什么是依赖注入](#13-什么是依赖注入)
    - [1.4. Dagger](#14-dagger)
        - [1.4.1. 基本实现概述](#141-基本实现概述)
        - [1.4.2. 其他特性](#142-其他特性)
    - [1.5. Koin](#15-koin)
- [2. Dagger](#2-dagger)
    - [2.1. Dagger 基础](#21-dagger-基础)
        - [2.1.1. @Inject](#211-inject)
            - [2.1.1.1. 声明在成员变量上](#2111-声明在成员变量上)
            - [2.1.1.2. 声明在构造函数上](#2112-声明在构造函数上)
        - [2.1.2. @Module](#212-module)
        - [2.1.3. @Component](#213-component)
            - [2.1.3.1. 暴露依赖实例](#2131-暴露依赖实例)
        - [2.1.4. Dagger的简单使用](#214-dagger的简单使用)
        - [2.1.5. @Binds](#215-binds)
        - [2.1.6. Component依赖](#216-component依赖)
        - [2.1.7. Subcomponent](#217-subcomponent)
        - [2.1.8. @Scope](#218-scope)
            - [2.1.8.1. 单例的实现原理](#2181-单例的实现原理)
        - [2.1.9. @Named](#219-named)
        - [2.1.10. Dagger in Android](#2110-dagger-in-android)
        - [2.1.11. Activity的自动注入](#2111-activity的自动注入)
        - [2.1.12. Activity自动注入实现原理](#2112-activity自动注入实现原理)
- [3. 参考文档](#3-参考文档)


# 1. 依赖注入 : Dependency Injection，DI

## 1.1. 什么是依赖?

比如类`ClassroomActivity`中用到了`Teacher`类的实例:

```
class ClassroomActivity {
  val teacher = Teacher();
}
```

这里`Teacher`就被称为`ClassroomActivity`的依赖

## 1.2. 依赖存在的问题

想象我们程序中有多个地方需要`Teacher`对象,那就要在很多地方手动构造`Teacher`, 万一`Teacher`的构造方式发生了变化, 那你就要改动多处来实现`Teacher`新的构造方式

导致上面这个问题的原因是 : **依赖的创建方式与使用方耦合与业务逻辑** , `依赖注入(DI)`就是为了解决这个问题

## 1.3. 什么是依赖注入

依赖注入是这样的一种行为,在类`ClassroomActivity`中不主动创建`Teacher`的对象,而是通过外部传入`Teacher`对象形式来设置依赖, 即**非自己主动初始化依赖(对象)，而是通过外部传入依赖的方式**

下面就是一种外部注入依赖的方式(外部set):

```
class ClassroomActivity {
  Teacher mTeacher;
      
  public void setEnergy(Teacher teacher) {
      mTeacher  = teacher;
  }
}
```

目前`Android`中有许多`依赖注入(DI)`框架, **本文主要介绍一下`Dagger`和`Koin`,了解一下它们的实现原理**

## 1.4. Dagger

### 1.4.1. 基本实现概述

`Dagger`的核心实现原理是:**基于注解在编译期产生依赖注入相关代码,然后开发者调用生成的代码进行依赖注入**, 以一个简单的使用场景为例:

>使用`@Inject`描述你需要的依赖:

```
class ClassroomActivity : Activity() {
    @Inject
    lateinit var teacher: Teacher
}
```

>使用`@Provides`描述依赖的创建方式:

```
@Module
class ClassroomModule {
    @Provides
    fun provideTeacher() = Teacher()
}
```

>`Dagger`会在编译时产生下面代码:

```
public final class ClassroomModule_ProvideTeacherFactory implements Factory<Teacher> {
  ....
  public static Teacher provideTeacher(ClassroomModule instance) {
    return instance.provideTeacher();
  }
}

public final class ClassroomActivity_MembersInjector implements MembersInjector<ClassroomActivity> {
    public static void injectTeacher(ClassroomActivity instance, Teacher teacher) {
      instance.teacher = teacher;
    }
}
```

>在使用时调用下面方法来实现依赖注入:

```
ClassroomActivity_MembersInjector.(activity, ClassroomModule_ProvideTeacherFactory.provideTeacher(new ClassModule()))
```

即`Dagger`会生成**依赖实例构造的工厂方法**和**依赖注入相关模板方法**

### 1.4.2. 其他特性

除了基本的依赖注入外`Dagger`还支持 :

1. 管理依赖的生命周期
2. 支持跨`Module(androidstudio中的)`的依赖注入

`Dagger`有完善、强大的依赖注入功能,不过`Dagger`的学习难度比较高,不是那么容易上手, 而`Koin`相较于`Dagger`在上手程度上则容易的多:

## 1.5. Koin

`Koin`是为`Kotlin`开发者提供的一个实用型轻量级依赖注入框架，采用纯`Kotlin`语言编写而成，仅使用功能解析，无代理、无代码生成、无反射,它的实现依赖于`kotlin`强大的语法糖（例如 Inline、Reified 等等）和函数式编程。

它的核心实现原理很简单: **利用函数类型保存依赖实例的构造方式,在运行时动态查找并完成依赖实例的创建**, 它的实现原理如下 :

在`koin`中`Definition<T>`是用来描述依赖实例的创建的函数类型:

```
typealias Definition<T> = Scope.(DefinitionParameters) -> T
```

`koin`提供了一些函数来创建`Definition`,比如:

```
val appModule = module {
    factory { RandomId() }
}
```

`module`是一个函数,在App启动时`koin`会调用它, 这是就会把依赖的创建方式存储起来:

```
inline fun <reified T> factory(
        qualifier: Qualifier? = null,
        override: Boolean = false,
        noinline definition: Definition<T>
): BeanDefinition<T> {
    return Definitions.saveFactory(qualifier, definition, rootScope, makeOptions(override))
}
```

**这里使用内联函数可以提高性能,具体化参数reified使编码更加简洁**

在使用时,`koin`提供下面函数来构建依赖实例:

```
val randomId: RandomId by inject()
```

`inject()`函数最终会去`koin`的全局集合中寻找`RandomId`的`Definition<T>`:

```
private val _instances = HashMap<IndexKey, InstanceFactory<*>>()

internal fun <T> resolveInstance(indexKey: IndexKey, parameters: ParametersDefinition?): T? {
    return _instances[indexKey]?.get(defaultInstanceContext(parameters)) as? T
}
```

`InstanceFactory`会调用函数类型来创建对应的实例:

```
 open fun create(context: InstanceContext): T {
    val parameters: DefinitionParameters = context.parameters
    return beanDefinition.definition.invoke(
        context.scope,
        parameters
    )
}
```

`koin`的实现并不复杂,`dagger`中有的一些语法在`koin`中基本也存在,`koin`相较于`dagger`,它把依赖的创建方式以函数对象的形式保存在了内存中简化了用法,不过也引入了一定的内存开销,
并且`koin`目前的实现不能实现**接口和实现分离**

dagger的语法比较复杂, 下面就简单学习和理解一下`dagger`中的各种用法:

# 2. Dagger

## 2.1. Dagger 基础

### 2.1.1. @Inject

它既可以用来指明对象的依赖，也可以用来指明依赖对象的创建方式, 不同的用法`Dagger`会在编译期生成不同的辅助类来完成依赖实例的注入 :

#### 2.1.1.1. 声明在成员变量上

```
class StudentTest {
    @Inject
    lateinit var nameInfo: NameInfo
}
```

`Dagger`会在编译期生成对应的依赖对象注入类(`StudentTest_MembersInjector`),在运行时它用来给`StudentTest`对象的`nameInfo`注入`NameInfo`实例:

```
public final class StudentTest_MembersInjector implements MembersInjector<StudentTest> {
  private final Provider<NameInfo> nameInfoProvider;

  public StudentTest_MembersInjector(Provider<NameInfo> nameInfoProvider) {
    this.nameInfoProvider = nameInfoProvider;
  }

  public static MembersInjector<StudentTest> create(Provider<NameInfo> nameInfoProvider) {
    return new StudentTest_MembersInjector(nameInfoProvider);}

  @Override
  public void injectMembers(StudentTest instance) {
    injectNameInfo(instance, nameInfoProvider.get());
  }

  public static void injectNameInfo(StudentTest instance, NameInfo nameInfo) {
    instance.nameInfo = nameInfo;
  }
}
```

>`Provider<NameInfo>`创建`NameInfo`的模板接口

#### 2.1.1.2. 声明在构造函数上

```
class Student @Inject constructor(val nameInfo: NameInfo) : IPeople
```

`Dagger`会在编译期生成`Student_Factory`类, 这个类会依赖构造参数来构造`Student`对象:

```
public final class Student_Factory implements Factory<Student> {
  private final Provider<NameInfo> nameInfoProvider;

  ...

  public static Student_Factory create(Provider<NameInfo> nameInfoProvider) {
    return new Student_Factory(nameInfoProvider);
  }

  public static Student newInstance(NameInfo nameInfo) {
    return new Student(nameInfo);
  }
}
```

>如果构造参数上标记了`@Inject`,那么`Dagger`会先寻找这个参数的`XX_Factory`,创建这个参数对象，然后再创建目前对象

### 2.1.2. @Module 

它用来封装创建对象实例的方法:

>`@Inject`的方式散落在各处不好管理

```
@Module
class StudentModule {
    @Provides
    fun provideNameInfo() = NameInfo("wang", "pengcheng")
}
```

`@Provides`需要声明在`@Module`标注的类中,它用来指明依赖实例的创建方式,对于每一个`@Provides`标注的方法, `Dagger`会在编译期生成对应的`Factory`:

>StudentModule_ProvideNameInfoFactory
```
public final class StudentModule_ProvideNameInfoFactory implements Factory<NameInfo> {
  private final StudentModule module;

  ...

  public static NameInfo provideNameInfo(StudentModule instance) {
    return Preconditions.checkNotNull(instance.provideNameInfo(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
```

即通过`StudentModule().provideNameInfo()`创建对应的`NameInfo`实例。

### 2.1.3. @Component

管理依赖实例, 链接`@Inject`和`@Module`, 可以为对象注入依赖实例 :

```
@Component(modules = [StudentModule::class])
interface StudentComponent {
    fun inject(studentTest: StudentTest)
}
```

`StudentComponent`会收集`modules = [StudentModule::class]`中依赖的创建方式,并通过这些方式创建对象实例赋值给`StudentTest`需要的成员变量。

>`Dagger`会在编译期为这个接口生成对应的实现类`DaggerStudentComponent`,这个类实现了`StudentTest`的依赖注入:

```
public final class DaggerStudentComponent implements StudentComponent {
  private final StudentModule studentModule;

  private DaggerStudentComponent(StudentModule studentModuleParam) {
    this.studentModule = studentModuleParam;
  }

  ...
  
  @Override
  public void inject(StudentTest studentTest) {
    injectStudentTest(studentTest);}

  @Override
  public NameInfo provideNameInfo() {
    return StudentModule_ProvideNameInfoFactory.provideNameInfo(studentModule);}

  private StudentTest injectStudentTest(StudentTest instance) {
    StudentTest_MembersInjector.injectNameInfo(instance, StudentModule_ProvideNameInfoFactory.provideNameInfo(studentModule));
    return instance;
  }

  public static final class Builder {
    ...
  }
}
```

>`DaggerStudentComponent`私有化构造函数,通过`Builder`来创建, 创建时需要传入`StudentModule`对象

#### 2.1.3.1. 暴露依赖实例

可以在`@Component`添加方法来暴露依赖实例:

```
@Component(modules = [ClassroomModule::class])
interface ClassroomComponent {
    ...
    fun getTeacher():Teacher
}
```

`Dagger`会生成工厂方法创建`Teacher`实例, 这样在代码中就可以直接获取:

```
val teacher = DaggerClassroomComponent.builder().classroomModule(ClassroomModule()).build().getTeacher()
```

### 2.1.4. Dagger的简单使用

通过对上面三大金刚的介绍, 我们了解了`Dagger`的基本使用与实现原理, 在编码时就可以这样使用:

```
class StudentTest {
    @Inject
    lateinit var nameInfo: NameInfo

    constructor() {
        DaggerStudentComponent.builder().studentModule(StudentModule()).build().inject(this)
        Log.d("dagger-test", "studentName : ${nameInfo.first} ${nameInfo.last}")
    }
}
```

logcat输出:

```
 D/dagger-test: wang pengcheng
```

### 2.1.5. @Binds

它也可以像`@Provides`指明一个依赖实例的提供方式, 不过它只能声明在抽象方法上, 它用来告诉`Dagger`接口应采用哪种实现:

```
@Module(includes = [StudentModule::class])
abstract class ClassroomModule {
    @Binds
    abstract fun bindPeopleWithStudent(test: Student): IPeople
}
```

>`@Module(includes = [StudentModule::class])`可以使`ClassroomModule`拥有`StudentModule`创建依赖实例的能力

`fun bindPeopleWithStudent(test: Student): IPeople` 定义 **`IPeople`的实现类为`Student`**

> 对于`Student`实例的提供可以使用`@Provides`,也可以使用`@Inject`标注在构造方法上 :

```
class Student @Inject constructor(val nameInfo: NameInfo) : IPeople
```

**`@Binds`解决了面向接口编程的需求, 即指明了依赖接口的实现类。当然这种情况也可以用`@Provides`实现(方法实体是类型的强转), 但@Binds明显更加清晰** : 

```
  @Provides
  fun providePeopleWithStudent() = Student(provideNameInfo()) as IPeople
```

### 2.1.6. Component依赖

如果`ClassroomComponent`需要使用`StudentComponent`的依赖实例, 则可以这样写:

```
@Component(modules = [ClassroomModule::class], dependencies = [StudentComponent::class])
interface ClassroomComponent {
    fun inject(test: ClassroomTest)
}

@Component(modules = [StudentModule::class])
interface StudentComponent {
    fun provideNameInfo(): NameInfo  //传递到依赖他的Component
}
```

`StudentComponent`可以通过`StudentModule`提供`NameInfo`, `ClassroomComponent`通过`dependencies = [StudentComponent::class]`来使用`NameInfo`, **除了`dependencies = [StudentComponent::class]`外, 还需要`StudentComponent`暴露对应的依赖实例方法` fun provideNameInfo(): NameInfo`**

上面经过`Dagger`编译会生成:

```
public final class DaggerClassroomComponent implements TestComponent {
  private final StudentComponent studentComponent;
  
  private ClassroomTest injectClassroomTest(ClassroomTest instance) {
    ClassroomTest_MembersInjector.injectStudent(instance, getStudent());
    return instance;
  }

}

public final class DaggerStudentComponent implements StudentComponent {
  @Override
  public NameInfo provideNameInfo() {
    return StudentModule_ProvideNameInfoFactory.provideNameInfo(studentModule);
  }
}
```

即`StudentComponent`变成了`DaggerTestComponent`的成员变量,这样就可以为`Test`注入`NameInfo`依赖, 使用时:

```
class ClassroomTest {
    @Inject
    lateinit var student: IPeople

    constructor() {
        DaggerClassroomComponent.builder().studentComponent(DaggerStudentComponent.builder().studentModule(StudentModule()).build()).build().inject(this)
        Log.d("dagger-test", "studentName : ${student.getName()}")
    }
}
```

### 2.1.7. Subcomponent

>上面`dependencies = [XXXComponent::class]`可以简单的理解为: **`AComponent`把`BComponent`变成成员变量, 然后使用`BComponent`其依赖注入的能力**

**`@Subcomponent`则可以使`BComponent`变为`AComponent`的内部类,然后使用`AComponent`的依赖注入能力(`@Module`):**

>AComponent:
```
@Component(modules = [ClassroomModule::class])
interface ClassroomComponent {

    fun inject(test: ClassroomTest)

    fun studentComponent(): StudentComponent.Builder  //用来构造StudentComponent

}
```

```
@Module(subcomponents = [StudentComponent::class])
class ClassroomModule {
    @Provides
    fun provideTeacher() = Teacher()
}
```

`subcomponents = [StudentComponent::class]`表示`StudentComponent`可以看到`ClassroomModule`提供的依赖实例 :

>BComponent:
```
@Subcomponent(modules = [StudentModule::class])
interface StudentComponent {

    fun inject(studentTest: StudentTest) //StudentTest对象依赖Teacher实例

    @Subcomponent.Builder
    interface Builder {
        fun build(): StudentComponent
    }
}
```

**使用`@Subcomponent`声明子`Component`, 还需要显示声明`Builder`, 这样父组件才知道如何创建子组件**

上面经过`Dagger`编译后不会生成`DaggerStudentComponent`, 只会生成`DaggerClassroomComponent` :

```
public final class DaggerClassroomComponent implements ClassroomComponent {

  private final class StudentComponentBuilder implements StudentComponent.Builder {
    @Override
    public StudentComponent build() {
      return new StudentComponentImpl(new StudentModule());
    }
  }

  private final class StudentComponentImpl implements StudentComponent {
    private final StudentModule studentModule;

    private StudentComponentImpl(StudentModule studentModuleParam) {
      this.studentModule = studentModuleParam;
    }

    @Override
    public void inject(StudentTest studentTest) {
      injectStudentTest(studentTest);}

    private StudentTest injectStudentTest(StudentTest instance) {
      StudentTest_MembersInjector.injectNameInfo(instance, StudentModule_ProvideNameInfoFactory.provideNameInfo(studentModule));
      StudentTest_MembersInjector.injectTeacher(instance, ClassroomModule_ProvideTeacherFactory.provideTeacher(DaggerClassroomComponent.this.classroomModule));
      return instance;
    }
  }
}
```

可以看到`StudentTest injectStudentTest(StudentTest instance)`方法中使用了`ClassroomModule`提供的依赖实例方法。

>因为没有生成`DaggerStudentComponent`,所以对于`DaggerStudentComponent`的构建必须这样做 :

```
class StudentTest {
    
    @Inject lateinit var nameInfo: NameInfo
    
    @Inject lateinit var teacher: Teacher

    constructor() {
        DaggerClassroomComponent.builder().classroomModule(ClassroomModule()).build().studentComponentBuilder().build().inject(this)
        Log.d("dagger-test", "teacher name : ${teacher.name}")
    }
}
```

### 2.1.8. @Scope

`@Scope`在`Dagger`中和`@Component`紧紧相连 : **如果一个`@Module`提供的依赖实例声明了和`@Component`相同的`@Scope`,那么这个`@Component`会使用同一个依赖实例来做依赖注入** : 

```
@Singleton
@Component(modules = [SingletonModule::class])
interface AppComponent : AndroidInjector<TestApplication> {
    fun inject(app: Application)

    fun getClassroom():Classroom
}
```

>你也可以直接手动获取单例`Classroom`

```
@Module
class SingletonModule {
    @Singleton
    @Provides
    fun provideClassRoom() = Classroom()
}
```

>@Singleton是`Dagger`内置的`@Scope`,一般用来定义一个`@Component`内唯一的依赖实例

```
class Test {
    ...
    @Inject lateinit var room1: Classroom
    @Inject lateinit var room2: Classroom
    ...    
}
```

上面这个`Test`的两个成员变量其实引用的是同一个`Classroom`实例, 不过在使用`@Scope`是需要注意 : **`@Subcomponent`不能和`@Component`声明相同的`@Scope`**

#### 2.1.8.1. 单例的实现原理

其实看一下`Component`的注入实现就明白了:

```
private ClassroomActivity injectClassroomActivity(ClassroomActivity instance) {
  ClassroomActivity_MembersInjector.injectTeacher1(instance, provideTeacherProvider.get());
  ClassroomActivity_MembersInjector.injectTeacher2(instance, provideTeacherProvider.get());
  return instance;
}
```

即使用同一个`Provider<T>`来获取的对象

### 2.1.9. @Named

在`Dagger`中提供依赖实例的方式一种有两种 :

1. `@Inject`标注在构造函数上
2. `@Provider`定义在`@Module`中

那如果使用这两种方式定义了同一个依赖实例呢? 这种情况下`Dagger`在选择依赖实例时就会迷失,会发生编译报错, 可以使用`@Named`来解决依赖迷失的问题 :

```
@Module
class ClassroomModule {
    @Provides
    @Named("teacher1")
    fun provideTeacher1() = Teacher()


    @Provides
    @Named("teacher2")
    fun provideTeacher2() = Teacher()
}

class ClassroomActivity : Activity() {

    @Inject
    @field:Named("teacher1")
    lateinit var teacher1: Teacher

}
```


### 2.1.10. Dagger in Android

上面介绍了`Dagger`的基本原理与使用方法, 不过在Android中如何使用`Dagger`呢？

如果按照上面的基本用法使用`Dagger`则会遇到一些列的问题, 比如像`Activity/Fragment`一般是由系统创建的, 所以我们不能把它变成依赖实例, 也不能完成自动依赖注入, 因此我们需要写出类似下面这种代码 :

```
class ClassroomActivity : Activity() {
  
    @Inject lateinit var teacher: Teacher

    override fun onCreate(savedInstanceState: Bundle?) {
      ClassroomActivityComponent.builder().build().inject(this);
    }
}
```

这样写有两点不好:

1. 太多类似的代码
2. 每个`Activity/Fragment`在注入是都需要知道其对应的`DaggerXXXComponent`

怎么解决呢？`Dagger`官方给出的实现步骤如下:

### 2.1.11. Activity的自动注入

1. 顶层Component绑定AndroidInjectionModule

2. 定义一个`@Subcomponent`并继承自`AndroidInjector<T>`

```
@Subcomponent(modules = [ClassroomModule::class])
interface ClassroomActivitySubcomponent : AndroidInjector<ClassroomActivity> {

    @Subcomponent.Factory
    interface Factory : AndroidInjector.Factory<ClassroomActivity>

}
```

3. 定义一个`@Module`并绑定先前定义的`@Subcomponent`

```
@Module(subcomponents = [ClassroomActivitySubcomponent::class])
abstract class ClassroomActivityModule {

    @Binds
    @IntoMap
    @ClassKey(ClassroomActivity::class)
    abstract fun bindClassroomActivityFactory(factory: ClassroomActivitySubcomponent.Factory): AndroidInjector.Factory<*>

}
```

4. 把上面定义的`@Module`绑定到`@Component`

```
@Component(modules = [AndroidInjectionModule::class, ClassroomActivityModule::class])
interface AppComponent : AndroidInjector<TestApplication> {

    fun inject(app: Application)

}
```

5. Application初始化`DispatchingAndroidInjector`

```
class TestApplication : Application(), HasAndroidInjector {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    override fun onCreate() {
        super.onCreate()
        DaggerAppComponent.create().inject(this)
    }

    override fun androidInjector() = dispatchingAndroidInjector
}
```

6. 在Activity中注入依赖

```
class ClassroomActivity : Activity() {

    @Inject
    lateinit var teacher: Teacher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidInjection.inject(this)
        setContentView(TextView(this).apply {
            text = "${teacher.nameInfo.getName()} "
        })
    }
}
```
>如果你不想每次都写`AndroidInjection.inject(this)`, 你可以直接让你的`Activity`继承自`DaggerAcitivity`

`AndroidInjection.inject(this)`会自动寻找`AppComponent`中的依赖实例并注入到`ClassroomActivity`中。

**对于上面2、3两步，其实可以使用`@ContributesAndroidInjector`合为一步**:

```
@Module
abstract class ClassroomActivityModule {

    @ContributesAndroidInjector(modules = [ClassroomModule::class])
    abstract fun contributeInjectorClassroomActivity(): ClassroomActivity

}
```

>`Dagger`在编译时会根据`@ContributesAndroidInjector`自动生成上面2、3步的代码。

那上面实现原理是什么呢？

### 2.1.12. Activity自动注入实现原理

来看一下`DaggerAppComponent`中生成的依赖注入代码:

```
public final class DaggerAppComponent implements AppComponent {
  ...

  private Map<Class<?>, Provider<AndroidInjector.Factory<?>>> getMapOfClassOfAndProviderOfAndroidInjectorFactoryOf() {
    return Collections.<Class<?>, Provider<AndroidInjector.Factory<?>>>singletonMap(ClassroomActivity.class, (Provider) classroomActivitySubcomponentFactoryProvider);
  }

  @SuppressWarnings("unchecked")
  private void initialize() {
    this.classroomActivitySubcomponentFactoryProvider = new Provider<ClassroomActivityModule_ContributeInjectorClassroomActivity.ClassroomActivitySubcomponent.Factory>() {
      @Override
      public ClassroomActivityModule_ContributeInjectorClassroomActivity.ClassroomActivitySubcomponent.Factory get(
          ) {
        return new ClassroomActivitySubcomponentFactory();}
    };
  }
  
  ...

  private final class ClassroomActivitySubcomponentImpl implements ClassroomActivityModule_ContributeInjectorClassroomActivity.ClassroomActivitySubcomponent {
    
    ...

    @Override
    public void inject(ClassroomActivity arg0) {injectClassroomActivity(arg0);}

    private ClassroomActivity injectClassroomActivity(ClassroomActivity instance) {
      ClassroomActivity_MembersInjector.injectTeacher(instance, getTeacher());
      return instance;
    }
  }
}
```

上面这段逻辑核心点有两个:

1. `ClassroomActivity`是利用`ClassroomActivitySubcomponentImpl`完成依赖注入的
2. `ClassroomActivitySubcomponentImpl`保存在了`DaggerAppComponent.getMapOfClassOfAndProviderOfAndroidInjectorFactoryOf`的map中


然后继续看一下`AndroidInjection.inject(this)`发生了什么:

```
public final class AndroidInjection {

    public static void inject(Activity activity) {
      Application application = activity.getApplication();
      ...
      injector = ((HasAndroidInjector) application).androidInjector();
      ...
      injector.inject(activity);
    }
}
```

即它最终调用了`activity.getApplication().androidInjector().injector.inject(activity)`,其实就是调用到了`DispatchingAndroidInjector<Any>.inject()`,而它最终会调用到:

```
public boolean maybeInject(T instance) {
  Provider<AndroidInjector.Factory<?>> factoryProvider =
        injectorFactories.get(instance.getClass().getName());
  AndroidInjector.Factory<T> factory = (AndroidInjector.Factory<T>) factoryProvider.get();
  ...
  factory.create(instance).inject(instance);
}
```

其实上面`injectorFactories`就是`DaggerAppComponent`中的那个`Factory Map`, 最终调用到`ClassroomActivitySubcomponentImpl.inject()`

# 3. 参考文档

[google dagger](https://developer.android.com/training/dependency-injection/dagger-android)

[dagger android](https://dagger.dev/dev-guide/android.html)

[kotlin-内联函数](https://www.kotlincn.net/docs/reference/inline-functions.html)

[kotlin-lambda](https://www.kotlincn.net/docs/reference/lambdas.html)


