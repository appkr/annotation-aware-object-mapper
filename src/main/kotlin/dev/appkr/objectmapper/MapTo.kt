package dev.appkr.objectmapper

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class MapTo(
    val value: String,
)
