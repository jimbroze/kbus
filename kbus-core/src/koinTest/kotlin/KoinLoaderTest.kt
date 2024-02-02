import KoinLoader
import instantiator.CustomDeps
import instantiator.LayeredDeps
import instantiator.NoDeps
import instantiator.SimpleDeps
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.koin.core.Koin
import org.koin.core.error.NoDefinitionFoundException
import kotlin.test.assertEquals
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

        assertThrows<NoDefinitionFoundException> {
            loader.load(CustomDeps::class)
        }
    }
}
