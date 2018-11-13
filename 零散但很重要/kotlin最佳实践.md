
>本文主要介绍一下个人工作中常用的kotlin的一些语法以及一些注意事项。文章我没有做分割，可能比较长。另外欢迎关注我的[Android进阶计划](https://github.com/SusionSuc/AdvancedAndroid)

# 类型的声明与使用

## val与var

val->不可变引用，var->可变引用。

*我们应该尽可能地使用val关键字来声明所有的kotlin变量*  为什么呢？

1. 首先一个变量在声明时是不可变的，那就代表你在使用的时候不需要考虑其他地方会对它重新赋值和改变(对于对象注意只是引用不可变)，直接使用。
2. val声明的类型由于必须初始化,它是线程安全的。
3. kotlin为了保证类型安全，所有变量声明的地方必须要做初始化，即显示赋一个初值。

## 空与非空

`kotlin`对于`可空类型`和`非空类型`认为是两个完全不同的类型，比如`Int`与`Int?`,他们俩就不是相同的类型。利用这个特点和编译时检查，`kotlin`基本可以避免`空指针异常`。

上面已经说了`kotlin的类型声明时必须要有初值`，所以`空与非空类型`和`val与var`一组合就会变成4种情况，下面我们对一个`Person`对象进行声明:

```
val p:Person = Person() 
val p:Person? = null    // 这个情况是没有意义的
var p:Person = Person()  // 如果这个对象是非空类型，那么初始化的时候必须赋一个非null初值
var p:Person? = null   //可以给一个null,也可以给一个对象实例
```

上面这段代码基本解释了`val与var`和`空与非空的`关系。

### 空与非空的使用

`kotlin`对`空与非空`做了严格的限制，那我们在使用时不都要对`可空类型`类型做判断吗？为了避免这个问题，kotlin提供了许多运算符来使开发人员对于`可空与非空`编码更加愉快。

- 安全调用运算符 "?."

比如我们声明了这样一个类型`var p:Person? = null`。如果我们直接使用`p.name`，kotlin编译器是会报错的无法编译通过，所有我们必须这么做:
```
if(p != null) p.name
```
这种代码写多了实在是太没有意义了，所有`kotlin`提供了`?.`。上面代码我们可以直接这样代替`p?.name`。它的实际执行过程是这样的:如果`p`为空则不操作，如果`p`不为空则调用`p.name`。

- Elvis 运算符 "?:"

在kotlin中我们会写这种代码:

```
val name = if(p != null) p.name else ""   //kotlin中的if是一个表达式
```

不过使用`?:`可以更简单实现上面的逻辑 : `val name = p?.name ?: ""` 。 它的实际执行逻辑是如果`p为null，p?.name就会为null`， `?:`会检查前面的结果，如果是null，那么则返回`""`。

- 安全转换 "as?"

在kotlin中类型转换关键字为`as`。不过类型转换会伴随着转换失败的风险。使用`as?`我们可以更优雅的写出转换无风险的代码:

```
//Person类中的方法
fun equals(o:Any?):Boolean{
    val otherPerson = o as? Person ?: return false
    ....
}
```
即`as?`在转换类型失败时会返回`null`

- 非空断言 "!!"

如果使用一个可空类型的方法，并且你不想做非空判断，那么你可以这样做: `person!!.getAge()`。 不过如果`person`为null，这里就会抛出`空指针异常`。

*其实还是在蛮多case下可以使用它，但是不建议使用, 因为你完全可以使用`?`、`?:`来做更优雅的处理, 你也可以使用`lateinit`来避免编译器的可空提示*

### val与bylazy、var与lateinit

- bylazy

它只能和`val`一块使用。

```
private val mResultView：View = bylazy{ 
    initResultView()
}
```

使用`bylazy`我们可以对一个变量延迟初始化，即懒加载。它是线程安全的。具体原理是:当我们使用`bylazy`声明的变量时，如果这个变量为null,那么就会调用`bylazy`代码块来初始化这个变量。

- lateinit

*它只能和`var`一块使用。并且不允许修饰可空类型*。那它的使用场景是什么呢？ 在有些case下，比如一个构造复杂的对象，我们就是想把变量声明为非空类型并且就是不想给他一个初值(代价太大了),这时候我们就可以使用`lateinit` :

```
lateinit var p : Person  //Person的构造函数太复杂了，不想在这里给一个初值

fun refreshUI(p2:Person){
    //p = p2
    val name = p.name  //注意这个地方是可能会抛p为初始化异常的！！！如果你没有初始化
}
```

*由于使用`lateinit`的时候我们要人工保证这个变量已经被初始化，并且`kotlin`在你每个使用这个变量的地方都会添加一个非null判断。所以`lateinit`尽量少用。*

# when 与 if

## if

`if`在`kotlin`中不只是一个控制结构它也是一个`表达式`,即它是有返回结果的，我们可以利用它来代替`java`中的三目运算符:

```
val background = if(isBlcak) R.drawable.black_image else R.drawable.white_image
```

## when

它的使用方法有多种:

- 代替`switch`的功能

```
when(color){
    "red","green" ->{ }
    "blue"->{ }
}
```

`kotlin`中的when可以用来判断任何对象，它会逐一检查每一个分支，如果满足这个分支的条件就执行。

- 多条件判断

可以使用`when`来避免`if..elseif..elseif..else`的写法:

```
val a = 1
val b = 2
when{
    a > 0 && b > 0 ->{}
    a < 0 && b > 0 ->{}
    a < 0 && b < 0 ->{}
    else ->{ }
}
```

- when是带有返回值的表达式

和`if`一样，`when`也是一个表达式:

```
val desColor = when(color){
        "red", "gren" -> "red&green"
        "blue" -> "blue"
        else -> "black" // when作为表达式时必须要有else分支。
    }
```

# 类

## 类的构造与主构造函数

- 简单的声明一个 javabean

在`kotlin`中我们可以这样简单的定义一个类: `class Person(val name:String = "", var age:Int = 0)`

这样就定义了一个`Person`类，这个类有两个属性:`name`和`age`。并且他有一个两个参数的构造函数来对这两个属性初始化。可以看出`kotlin`将一个类的声明变的十分方便。

- 主构造函数

普通的`java`构造函数是有代码块的，即可以做一些逻辑操作，那按照`kotlin`上面的方式，我们怎么做构造函数的逻辑操作呢? `kotlin`提供了`初始化代码块`:

```
class Person(val name:String = "", var age:Int = 0){
    init{
        name = "susion"
        age = 13
    }
}
``` 

*`init代码块`会在主构造函数之后运行，注意不是所有的构造函数*。

## 数据类 data class

更方便的定义一个javabean，我们可以使用数据类:

```
data class Person(val name:String = "", val age:Int = 0)
```

使用`data`定义的`Person`会默认生成`equals`、`hashCode`、`toString`方法。需要注意的是*数据类的属性我们应该尽量定义成val的*。这是因为在主构造函数中声明的这些属性都会纳入到`equals`和`hashCode`方法中。如果某个属性是可变的，
那么这个对象在被加入到容器后就会是一个无效的状态。

## object 和 companion object

在kotlin中没有静态方法，也没有静态类。不过kotlin提供了`object`与`companion object`

- object 单例类

使用`object`我们可以很轻松的创建一个单例类 :

```
object LoginStatus{
    var isLogin = false

    fun login(){
        isLogin = true
    }
    ...
}
```

我们可以这样直接使用`LoginStatus.isLogin()`。 那这个单例在kotlin中是怎么实现的呢？我们可以反编译看一下它生成的java代码:

```
public final class LoginStatus {
   private static boolean isLogin;
   public static final LoginStatus INSTANCE; // for java调用

   ..... //省略不重要的部分

   public final void login() {
      isLogin = true;
   }

   private LoginStatus() {
      INSTANCE = (LoginStatus)this;
   }

   static { //类加载的时候构造实例
      new LoginStatus();
   }
}
```

*即`kotlin object`实现的单例是线程安全的。它的对象是在类创建的时候就产生了。*

- object的静态方法的使用

上面我们已经知道`object`创建单例的原理了。这在某些`case`下就很棒，但是某些时候我们不是想要单例，我们只是想要一些静态方法呢？比如我们经常创建的一些工具类(UIUtils、StringUtils)等。我们可以直接使用`object`来完成:

```
public object UIUtils{
    ...
    ...
    ...
    ..很多方法
}
```

按照`kotlin`单例的设计，我们只要一旦使用这些方法，*那么一直有一个单例对象`UIUtils`存在于内存中。那么这样好吗?*  我们是否可以这样写呢 : 

```
public class UIUtils{
    ...
    ...
    ...
    ..很多方法
}
```

然后在使用的时候:`UIUtils().dp2Px(1)`。这样至少不会有一个对象一直在内存中。我想我们在某些case下可以这样使用我们的工具类。或者你可以使用kotlin的`扩展函数`或`顶层函数`来定义一些工具方法。所以对于kotlin的`object`的使用需要注意。

- companion object

`companion object`主要是为了方便我们可以在一个类中创建一些静态方法而存在的，比如:

```
class Person(val name: String) {
    companion object {
        fun isMeal(p: Person) = false
    }
    
}
```

依旧看一下它反编译后的java代码:

```
public final class Person {
   ...
   public static final Person.Companion Companion = new Person.Companion((DefaultConstructorMarker)null);
   ....
   public static final class Companion {
      public final boolean isMeal(@NotNull Person p) {
         Intrinsics.checkParameterIsNotNull(p, "p");
         return false;
      }

      private Companion() {
      }
      .....
   }
}
```

即它也是生成了一个单例类`Person.Companion`。不过这个单例类是一个静态类。不允许构造。不过它的实现机制几乎和`object`相同。

# lambda

kotlin中`lambda`的本质就是可以传递给其他函数的一小段代码。kotlin中`lambda`使用的最多的就是和集合一块使用。

- lambad与java接口

比如我们经常给`View`设置`onClickListener`,在kotlin中我们可以很方便的实现这段代码:

```
userView.setOnClickListener{

}  // 如果lambda是函数的最后一个参数，那么是可以放在括号外面的。
```

即你可以直接传递给它一个`lambda`。*kotlin在实际编译的时候会把这个`lambda`编译成一个匿名内部类。* 那么所有`java`传对象的地方都可以这样使用吗？ 当然不是, 只有java参数满足下面条件才可以使用:

*这个参数是一个接口，并且这个接口只有一个抽象方法。就可以这样使用，比如`Runnable`、`Callable`等。*

- with 与 apply

`with`个人感觉比较鸡肋，这里就不讲它了。不过`apply`是十分实用的。在`kotlin`中`apply`被实现为一个函数:

```
public inline fun <T> T.apply(block: T.() -> kotlin.Unit): T {  }    
```

*即它是一个扩展函数，接收一个lambda，并返回对象本身,并且他是一个内联的函数(下面会讲)*

我最常用的一个case是给view设置参数:

```
val tvName = TextView(context).apply{
    textSize = 10
    textColor = xx
    text = "susion"
    ...
}
```

# 常用的库函数

## 内联函数

kotlin集合库中很多函数可以接收lambda作为参数。但我们前面说了kotlin的lambda表达式会被编译为一个匿名内部类，即每一次lambda的调用都会创建一个匿名内部类，所以会带来运行时的开销。
kotlin集合库中函数时给我们使用的，如果有这种开销的话，这肯定是一个不好的设计，因此kotlin集合库中的大部分函数都是`内联函数`。

比如上面的`apply`声明 : `public inline fun <T> T.apply(block: T.() -> kotlin.Unit): T { .... } `

何为内联呢？: *当一个函数被声明为`inline`时，它的函数体是内联的，即函数体会被直接替换到函数被调用的地方。即不会存在运行时开销* 下面要说的`filter`和`map`都是内联函数。

- filter 与 map

`filter`接收一个返回`Boolean`的lambda，用于对一个集合做筛选工作,并返回一个新集合:

```
val wangList = userList.filter{it.firstName == "wang"}
```

`map`也是接收一个lambda，它的返回值是另一个类型的集合，即`map`可以把一个类型的集合变成另一个类型的集合。

```
val ageList = userList.map{ it.age }
```

这两个函数虽然好用，不过我们要注意他们的实现，以免带来不必要的性能损耗 :  `filter`和`map`函数都会创建一个新集合，因此如果你是下面这种用法就可能出现不必要的集合创建:

```
val wangList = userList.map{ it.name }.filter{it == "wang"}  
```

*在这种情况下，userList集合非常大的话，那么map操作之后生成的中间集合也可能非常大*  对于这种情况可以考虑使用 `kotlin序列`。

- count与find

`count`用于统计集合中满足某个条件的数量，可以对比下面这种写法

```
val wangCount= userList.filter{it.firstName == "wang"}.size

val wangCount2 = userList.count{it.firstName == "wang"} //很明显，这种写法远好于第一种
```

`find`用来寻找集合中满足某个case的元素。 具体用法就不写了。

# Kotlin的一些优秀的设计思想

kotlin是基于java的，不过它在设计摒弃了很多java不好的思想。下面简单列举一些:

- kotlin中并不区分受检查异常和未受检查异常。即不用指定函数抛出的异常，而且也可以不处理异常

- kotlin中类和类的方法默认是`final`的，即不允许继承和重写。这主要是为了减少继承，避免`脆弱基类的问题`

- kotlin中集合接口分为`访问集合数据`和`修改集合数据`。比如`kotlin.collections.Collection`中就没有修改集合的方法。

- kotlin中的函数支持默认参数，避免了多次重写一个函数的情况

- kotlin支持扩展函数。它实际上就是某个类的静态方法。

- .....

>本文参考自《kotlin实战》

>欢迎关注我的[Android进阶计划](https://github.com/SusionSuc/AdvancedAndroid)看更多干货









