import org.koin.core.Koin
import org.koin.core.error.NoDefinitionFoundException
import kotlin.reflect.KClass

class KoinLoader(private val container: Koin = Koin()) : ClassLoader() {
    private val instantiator = ClassInstantiator()

    override fun addDependency(dependency: Any) {
        instantiator.addDependency(dependency)
    }

    override fun <TClass : Any> instantiate(cls: KClass<TClass>): TClass {
//        return try {
        return container.get(cls)
//        } catch (e: NoDefinitionFoundException) {
//            instantiator.load(cls)
//        }
    }
}
