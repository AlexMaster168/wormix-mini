plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":core")); testImplementation("junit:junit:4.13.2") }
application { mainClass.set("com.riftartillery.desktop.BridgeKt") }
tasks.register<Jar>("standaloneJar") {
    archiveFileName.set("rift-sim.jar")
    duplicatesStrategy=DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"]="com.riftartillery.desktop.BridgeKt" }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({configurations.runtimeClasspath.get().map {if(it.isDirectory) it else zipTree(it)}})
}
