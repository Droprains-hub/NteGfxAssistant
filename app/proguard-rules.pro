# 保留行号信息用于崩溃定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose / AndroidX 的 R8 规则由各库的 consumer-rules 自动合并，
# 此处只补充本项目自身需要的少量约束。

# 保留枚举的 values/valueOf（避免 name() 相关调用被裁剪后不一致）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Kotlin 元数据，Compose 运行时按反射读取组合函数信息
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
