// Geliştirme yardımcısı: modüllerin derleme sınıf yolundaki kitaplıkları tek klasöre kopyalar.
// Kullanım: ./gradlew -I ci/dump-classpath.init.gradle.kts :app:dumpClasspath :desktop:dumpClasspath
allprojects {
    afterEvaluate {
        val projectName = name
        val configurationName = when (projectName) {
            "app" -> "debugCompileClasspath"
            "desktop" -> "runtimeClasspath"
            else -> return@afterEvaluate
        }
        tasks.register("dumpClasspath") {
            doLast {
                val out = File(rootDir, "build/deps/$projectName").apply { deleteRecursively(); mkdirs() }
                val configuration = configurations.getByName(configurationName)
                val files = if (projectName == "app") {
                    configuration.incoming.artifactView {
                        attributes { attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar") }
                    }.files
                } else {
                    configuration
                }
                var index = 0
                for (file in files) {
                    if (!file.exists() || file.startsWith(rootDir)) continue
                    file.copyTo(File(out, "%03d-%s-%s".format(index++, file.parentFile.parentFile.name.take(40), file.name)))
                }
                println("$projectName: $index dosya")
            }
        }
    }
}
