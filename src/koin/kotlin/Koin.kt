import org.koin.core.Koin
import kotlin.reflect.KClass

class KoinLoader(private val container: Koin = Koin()) : DependencyLoader() {
    override fun <TClass : Any> instantiate(cls: KClass<TClass>): TClass {
        return container.get(cls)
    }
}
