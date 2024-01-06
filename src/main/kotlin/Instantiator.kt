import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor

class ClassInstantiator(dependencies: List<Any> = emptyList()) : ClassLoader() {
    private val savedDependencies = dependencies.toMutableList()

    override fun addDependency(dependency: Any) {
        savedDependencies.add(dependency)
    }

    override fun <TClass : Any> instantiate(cls: KClass<TClass>): TClass {
        for (dependency in savedDependencies) {
            if (cls.isInstance(dependency)) {
                @Suppress("UNCHECKED_CAST")
                return dependency as TClass
            }
        }

        val loadedDependencies = mutableMapOf<KParameter, Any>()

        cls.primaryConstructor?.let { primaryConstructor ->
            for (dependency in primaryConstructor.parameters) {
                val dependencyType: KClass<*> = dependency.type.classifier as KClass<*>
                val loadedDependency = instantiate(dependencyType)
                loadedDependencies[dependency] = loadedDependency
            }
            return primaryConstructor.callBy(loadedDependencies)
        } ?: return cls.createInstance()
    }
}
