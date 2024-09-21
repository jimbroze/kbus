plugins {
    id("com.ncorti.ktfmt.gradle") version("0.20.1")
}

allprojects {
    group = "com.jimbroze"
    apply(plugin = "com.ncorti.ktfmt.gradle")
    ktfmt {
        kotlinLangStyle()
    }
}
