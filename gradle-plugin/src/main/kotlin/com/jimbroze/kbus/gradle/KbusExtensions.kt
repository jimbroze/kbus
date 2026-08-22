package com.jimbroze.kbus.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/** The `kbus { }` block of a module belonging to one bounded context. */
abstract class KbusContextExtension {
    /** The package every module of this build writes its dependency index into. */
    abstract val indexPackage: Property<String>

    /** The bounded context this module's handlers belong to. Several modules share one identity. */
    abstract val boundedContext: Property<String>
}

/** The `kbus { }` block of the module that generates the bus. */
abstract class KbusBusExtension {
    /** The package every module of this build writes its dependency index into. */
    abstract val indexPackage: Property<String>

    /**
     * Modules to look for an index in on top of those the metadata classpath names — for a
     * submodule whose index is not named after its Gradle module, which nothing can derive.
     */
    abstract val additionalModulesToIndex: ListProperty<String>
}
