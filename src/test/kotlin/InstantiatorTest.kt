import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertIs


class NoDeps {
    val output: String = "No dependencies loaded"
}

class SimpleDeps(val depOne: NoDeps, val depTwo: NoDeps) {
    val output: String = "One dependency loaded"
}

class LayeredDeps(val depOne: SimpleDeps, val depTwo: NoDeps) {
    val output: String = "Layered dependencies loaded"
}

class CustomDeps(val depOne: String, val depTwo: SimpleDeps) {
    val output: String = depOne
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstantiatorTest {
    @Test
    fun load_returns_instance_if_provided() {

    }

    @Test
    fun load_instantiates_class_with_no_dependencies() {
        val loader = ClassInstantiator()

        val loadedClass = loader.load(NoDeps::class)
        assertIs<NoDeps>(loadedClass)
        assertEquals("No dependencies loaded", loadedClass.output)
    }

    @Test
    fun load_instantiates_class_with_one_layer_of_simple_dependencies() {
        val loader = ClassInstantiator()

        val loadedClass = loader.load(SimpleDeps::class)
        assertIs<SimpleDeps>(loadedClass)
        assertEquals("One dependency loaded", loadedClass.output)
    }

    @Test
    fun load_instantiates_class_with_multiple_layers_of_simple_dependencies() {
        val loader = ClassInstantiator()

        val loadedClass = loader.load(LayeredDeps::class)
        assertIs<LayeredDeps>(loadedClass)
        assertEquals("Layered dependencies loaded", loadedClass.output)
    }
}
