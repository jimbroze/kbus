import com.jimbroze.kbus.koin.KoinLoader
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.koin.core.Koin
import org.koin.core.error.NoDefinitionFoundException
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KoinLoaderTest {
    @Test
    fun load_returns_instance_if_provided() {
    }

    @Test
    fun load_loads_class_if_registered() {
        val koin = Koin()
        koin.declare(LayeredDeps(SimpleDeps(NoDeps(), NoDeps()), NoDeps()))
        val loader = KoinLoader(koin)

        val loadedClass = loader.load(LayeredDeps::class)
        assertIs<LayeredDeps>(loadedClass)
        assertEquals("Layered dependencies loaded", loadedClass.output)
    }

    @Test
    fun load_throws_definition_not_found_exception_if_cannot_find_dependencies() {
        val loader = KoinLoader()

        assertFailsWith<NoDefinitionFoundException> {
            loader.load(CustomDeps::class)
        }
    }
}
