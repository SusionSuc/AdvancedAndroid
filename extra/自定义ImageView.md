
- glide的transform不支持占位图圆角

- 对于不是固定尺寸的`image view`并不能很好的指出圆角展示

>当原始Bitmap宽高比=2:1，切成圆角后Bitmap宽高比仍然2:1，而显示到ImageView的宽高比是1:1，且设置android:scaleType="centerCrop"属性，可能会导致四个圆角基本看不到了

- 对center crop支持并不是很友好, 
>曾出现在瀑布流中，多次刷新图片导致被重复放大的问题

- 对于不定的view大小的case， 后台最好返回图片的大小，以便客户端优化
  


- 图片预加载

