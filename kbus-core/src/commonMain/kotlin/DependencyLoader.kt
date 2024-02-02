import kotlin.reflect.KClass

abstract class DependencyLoader {
    abstract fun <TClass : Any> instantiate(cls: KClass<TClass>): TClass

    fun <TClass : Any> load(cls: KClass<TClass>): TClass {
        return instantiate(cls)
    }
}
