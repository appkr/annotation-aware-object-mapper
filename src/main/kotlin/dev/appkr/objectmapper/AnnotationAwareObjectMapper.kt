package dev.appkr.objectmapper

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZonedDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

class AnnotationAwareObjectMapper(
    private val customMapperRegistry: CustomMapperRegistry,
) {
    /**
     * Creates an instance of the 'to' object from the given 'from' object.
     */
    fun <T : Any> copyProperties(
        from: Any,
        to: KClass<T>,
    ): T {
        // Get the Primary Constructor of the 'to' class.
        val toConstructor: KFunction<T> =
            to.primaryConstructor
                ?: throw IllegalStateException("Unable to access primary constructor: class=${to.simpleName}")

        // Get the property map to be passed to the 'to' class constructor.
        val toConstructorArgs: Map<KParameter, Any?> =
            toConstructor.parameters
                .associateWith { findValueOf(constructorParam = it, sourceObject = from) }

        // Create the 'to' object.
        return toConstructor.callBy(toConstructorArgs)
    }

    /**
     * Finds the value to assign to the constructor parameter from the source object.
     *
     * Rules for finding the value:
     *   - If the name of the constructor parameter matches the property name of the source object.
     *   - If the name of the constructor parameter matches the [MapTo.value] declared on the property of the source object.
     *
     * @param constructorParam The constructor parameter of the class to be created.
     * @param sourceObject The object from which the parameter value will be found.
     * @throws IllegalStateException
     *   - When a value for the constructor parameter cannot be found in the source object
     *   - When the value retrieved from the source object is null, but the constructor parameter is declared as non-null
     *   - When a matching property is found in the source object, but cannot be converted to the correct type
     */
    private fun findValueOf(
        constructorParam: KParameter,
        sourceObject: Any,
    ): Any? {
        // Get the constructor parameter to assign the value.
        val propertyName =
            constructorParam.name
                ?: throw IllegalStateException("Constructor parameter name should not be null")

        // Get the property from the source object to retrieve the value.
        val fromProperty =
            sourceObject::class
                .memberProperties
                .find { it.findAnnotation<MapTo>()?.value == propertyName || it.name == propertyName }
                ?.apply { isAccessible = true }
                ?: throw IllegalStateException("Unable to find a value from the sourceObject: propertyName=$propertyName")

        // Extract the value from the matching property in the source object.
        val value = fromProperty.call(sourceObject)

        if (value == null) {
            if (constructorParam.isOptional || constructorParam.type.isMarkedNullable) {
                // If it's possible to assign null to the constructor parameter.
                return value
            }
            throw IllegalStateException("Null value cannot be assigned: paramName=$propertyName")
        }

        // If the extracted value's type doesn't match the constructor parameter's type, try type conversion.
        if (fromProperty.returnType != constructorParam.type) {
            val convertedValue =
                tryConvert(
                    value = value,
                    fromType = fromProperty.returnType,
                    toType = constructorParam.type,
                )
                    ?: throw IllegalStateException(
                        "Type conversion failed: paramName=${constructorParam.name}, paramType=${constructorParam.type}, " +
                            "sourceProperty=${fromProperty.name}, sourceType=${fromProperty.returnType}",
                    )

            return convertedValue
        }

        return value
    }

    /**
     * Attempts type conversion.
     *
     * @param value The original value.
     * @param fromType The type of the original value.
     * @param toType The type of the target value.
     */
    private fun tryConvert(
        value: Any?,
        fromType: KType,
        toType: KType,
    ): Any? {
        if (value == null) return null

        // Attempts convert @JvmInline classes.
        if (fromType.jvmErasure.isInlineClass() || toType.jvmErasure.isInlineClass()) {
            if (value::class == toType.jvmErasure) return value
        }

        // Identify the type parameter for Collection and recursively convert values.
        if (fromType.classifier == Collection::class && toType.classifier == Collection::class) {
            val fromCollection = value as Collection<*>
            val toElementType =
                toType.arguments.first().type
                    ?: throw IllegalStateException(
                        "Unable to identify target collection element type: " +
                            "fromType=$fromType, toType=$toType",
                    )

            return fromCollection.map {
                tryConvert(
                    value = it,
                    fromType = it?.javaClass?.kotlin?.starProjectedType ?: fromType,
                    toType = toElementType,
                )
                    ?: throw IllegalStateException("Unable to get collection element value: element=$it")
            }
        }

        // Identify the type parameters for Map keys and values, and recursively convert values.
        if (fromType.classifier == Map::class && toType.classifier == Map::class) {
            val fromMap = value as Map<*, *>
            val toKeyType =
                toType.arguments[0].type
                    ?: throw IllegalStateException("Unable to identify target map key type: toType=$toType")
            val toValueType =
                toType.arguments[1].type
                    ?: throw IllegalStateException("Unable to identify target map value type: toType=$toType")

            return fromMap
                .mapKeys { (key, _) ->
                    tryConvert(
                        value = key,
                        fromType = key?.javaClass?.kotlin?.starProjectedType ?: fromType,
                        toType = toKeyType,
                    )
                        ?: throw IllegalStateException("Unable to get map key's value: key=$key")
                }.mapValues { (_, mapValue) ->
                    tryConvert(
                        value = mapValue,
                        fromType = mapValue?.javaClass?.kotlin?.starProjectedType ?: fromType,
                        toType = toValueType,
                    )
                        ?: throw IllegalStateException("Unable to get map's value: value=$mapValue")
                }
        }

        /*
         * Check if the runtime object types are different.
         *   - Foo and Bar have jvmErasure of Foo::class and Bar::class respectively, so this condition is met.
         *   - List<String> and List<Int> both have jvmErasure of List::class, so this condition is not met.
         */
        if (fromType.jvmErasure != toType.jvmErasure) {
            // If object types are different, try CustomMapper.
            val mapper = customMapperRegistry.getMapper(fromType.jvmErasure, toType.jvmErasure)
            val mappedValue = (mapper as? CustomMapper<Any, Any>)?.map(value)
            if (mappedValue != null) return mappedValue
        }

        // Attempt built-in type conversion.
        val classConversion = builtInConvert<Any>(value, toType)
        if (classConversion != null) return classConversion

        // If reached here, conversion is not possible, return null to exit recursion.
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> builtInConvert(
        value: Any,
        toType: KType,
    ): T? =
        when (toType.classifier) {
            Int::class -> value.toString().toIntOrNull() as? T
            Long::class -> value.toString().toLongOrNull() as? T
            Double::class -> value.toString().toDoubleOrNull() as? T
            BigDecimal::class -> value.toString().toBigDecimalOrNull() as? T
            Boolean::class -> value.toString().toBooleanStrictOrNull() as? T
            Year::class -> Year.parse(value.toString()) as? T
            YearMonth::class -> YearMonth.parse(value.toString()) as? T
            LocalDate::class -> LocalDate.parse(value.toString()) as? T
            LocalDateTime::class -> LocalDateTime.parse(value.toString()) as? T
            Instant::class -> Instant.parse(value.toString()) as? T
            ZonedDateTime::class -> ZonedDateTime.parse(value.toString()) as? T
            OffsetDateTime::class -> OffsetDateTime.parse(value.toString()) as? T
            String::class -> value.toString() as? T
            else -> null
        }
}

private fun KClass<*>.isInlineClass(): Boolean = this.hasAnnotation<JvmInline>()
