allprojects {
    group = "heckerpowered.lethal"
}

subprojects {
    val versionPropertyName = "${name.replace("-", "_")}_version"
}