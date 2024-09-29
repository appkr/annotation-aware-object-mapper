package dev.appkr.objectmapper

import kotlin.reflect.KClass

class CustomMapperRegistry {
    private val mappers = mutableMapOf<Pair<KClass<*>, KClass<*>>, CustomMapper<*, *>>()

    fun <F : Any, T : Any> registerMapper(
        from: KClass<F>,
        to: KClass<T>,
        mapper: CustomMapper<F, T>,
    ) {
        mappers[from to to] = mapper
    }

    @Suppress("UNCHECKED_CAST")
    fun <F : Any, T : Any> getMapper(
        from: KClass<F>,
        to: KClass<T>,
    ): CustomMapper<F, T>? {
        return mappers[from to to] as? CustomMapper<F, T>
    }
}
