>Android中所有的视图都是通过`Window`来呈现的，不管是`Activity`、`Dialog`还是`Toast`,它们的视图实际上都是附加在`Window`上的，因此`Window`实际是`View`的直接管理者。本文就从源码来分析一下`Window`是如何承载视图(`View`)的显示。

# Window的基本操作

