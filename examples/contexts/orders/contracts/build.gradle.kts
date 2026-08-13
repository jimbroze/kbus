plugins { id("kbus.multiplatform") }

kotlin { sourceSets { commonMain.dependencies { api(projects.kbusApi) } } }
