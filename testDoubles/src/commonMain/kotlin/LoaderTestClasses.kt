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
