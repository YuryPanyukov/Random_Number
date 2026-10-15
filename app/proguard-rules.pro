# Правила R8 для release-сборки.

# kotlinx.serialization: генерирует сериализаторы через рефлексию,
# поэтому классы @Serializable и их компаньоны нужно сохранить.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class CodeSyS.Random_Number.data.Session$$serializer { *; }
-keepclassmembers class CodeSyS.Random_Number.data.Session {
    *** Companion;
}
-keepclasseswithmembers class CodeSyS.Random_Number.data.Session {
    kotlinx.serialization.KSerializer serializer(...);
}

# Точки входа, указанные в манифесте.
-keep class CodeSyS.Random_Number.App { *; }
-keep class CodeSyS.Random_Number.MainActivity { *; }

# ViewModel-фабрики создаются рефлексивно по классу из CreationExtras.
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
