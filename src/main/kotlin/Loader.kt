import kotlin.reflect.KClass

abstract class ClassLoader {
    abstract fun addDependency(dependency: Any)
    abstract fun <TClass : Any> instantiate(cls: KClass<TClass>): TClass

    fun <TClass : Any> load(cls: KClass<TClass>): TClass {
        return instantiate(cls)
    }

    fun <TClass : Any> load(cls: TClass): TClass {
        return cls
    }
}
