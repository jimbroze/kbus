plugins { id("kbus.multiplatform") }

kotlin { sourceSets { commonMain.dependencies { implementation(libs.kotlinx.coroutines.test) } } }
