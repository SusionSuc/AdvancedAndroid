
`Dagger`是一个依赖注入框架, 它的核心实现原理是在编译期产生依赖注入相关代码, 我们可以通过`Dagger`提供的注解来描述我们的依赖注入需求。

为了实现依赖注入,`Dagger`需要知道**对象的创建方式**, 开发者需要知道**怎么获取`Dagger`创建的对象**, 本文会围绕这两点介绍一下`Dagger`中的注解的功能以及实现原理。

> 依赖注入 : 就是非自己主动初始化依赖(对象)，而是通过外部传入依赖的方式。本文除了介绍`Dagger`的基本使用,也会分析一下`Dagger`在火山组件化中的使用。

# Dagger 基础

## @Inject

它既可以用来指明对象的依赖，也可以用来指明依赖对象的创建方式, 不同的用法`Dagger`会在编译期生成不同的辅助类来完成依赖实例的注入 :

### 声明在成员变量上

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

### 声明在构造函数上

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

## @Module 

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

## @Component

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

### 暴露依赖实例

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

## Dagger的简单使用

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

## @Binds

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

## Component依赖

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

## Subcomponent

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

## @Scope

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

### 单例的实现原理

其实看一下`Component`的注入实现就明白了:

```
private ClassroomActivity injectClassroomActivity(ClassroomActivity instance) {
  ClassroomActivity_MembersInjector.injectTeacher1(instance, provideTeacherProvider.get());
  ClassroomActivity_MembersInjector.injectTeacher2(instance, provideTeacherProvider.get());
  return instance;
}
```

即使用同一个`Provider<T>`来获取的对象

# Dagger in Android

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

## Activity的自动注入

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

## Activity自动注入实现原理

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


# 参考文档

https://developer.android.com/training/dependency-injection/dagger-android

https://dagger.dev/dev-guide/android.html





