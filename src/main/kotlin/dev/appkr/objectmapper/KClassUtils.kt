package dev.appkr.objectmapper

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

object KClassUtils {
    const val DUMMY_STRING = "dummy"

    /**
     * Creates a dummy instance of the given class.
     *
     * This method generates a default or dummy instance for the given KClass, filling in properties with
     * reasonable default values based on their types. For instance, String becomes [DUMMY_STRING], numeric types become 0, etc.
     *
     * @param kClass The class for which a dummy instance will be created.
     * @throws IllegalStateException If a constructor cannot be accessed or dummy values cannot be determined.
     */
    fun <T : Any> dummy(kClass: KClass<T>): T {
        // Get the primary constructor of the class
        val constructor: KFunction<T> =
            kClass.primaryConstructor
                ?: throw IllegalStateException("Unable to access primary constructor: class=${kClass.simpleName}")

        // Get default arguments to be passed to the constructor
        val args: Map<KParameter, Any?> =
            constructor.parameters.associateWith { parameter ->
                createDefaultInstance(parameter.type)
            }

        // Create and return the dummy object
        return constructor.callBy(args)
    }

    /**
     * Creates a default instance or dummy value for the given type.
     * If the type is a primitive or known type, a reasonable dummy value is returned.
     *
     * @param type The KType for which a default instance is required.
     * @return A default or dummy instance of the specified type.
     */
    private fun createDefaultInstance(type: KType): Any {
        val kClass =
            type.classifier as? KClass<*>
                ?: throw IllegalArgumentException("Unable to determine class from: type=$type")

        return when {
            kClass.isSubclassOf(String::class) -> DUMMY_STRING
            kClass.isSubclassOf(Int::class) -> 0
            kClass.isSubclassOf(Long::class) -> 0L
            kClass.isSubclassOf(Double::class) -> 0.0
            kClass.isSubclassOf(BigDecimal::class) -> BigDecimal.ZERO
            kClass.isSubclassOf(Boolean::class) -> false
            kClass.isSubclassOf(Year::class) -> Year.of(LocalDate.ofEpochDay(0).year)
            kClass.isSubclassOf(YearMonth::class) -> YearMonth.of(LocalDate.ofEpochDay(0).year, LocalDate.ofEpochDay(0).monthValue)
            kClass.isSubclassOf(LocalDate::class) -> LocalDate.ofEpochDay(0)
            kClass.isSubclassOf(LocalDateTime::class) -> LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault())
            kClass.isSubclassOf(Instant::class) -> Instant.EPOCH
            kClass.isSubclassOf(ZonedDateTime::class) -> ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault())
            kClass.isSubclassOf(OffsetDateTime::class) -> OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault())
            kClass.isSubclassOf(Collection::class) -> {
                val elementType =
                    type.arguments.firstOrNull()?.type
                        ?: throw IllegalArgumentException("Unable to determine collection type parameter from: type=$type")
                listOf(createDefaultInstance(elementType))
            }
            kClass.isSubclassOf(Map::class) -> {
                val keyType =
                    type.arguments[0].type
                        ?: throw IllegalArgumentException("Unable to determine map key type from: type=$type")
                val valueType =
                    type.arguments[1].type
                        ?: throw IllegalArgumentException("Unable to determine map value type from: type=$type")
                mapOf(createDefaultInstance(keyType) to createDefaultInstance(valueType))
            }
            kClass.isData -> createDataClassInstance(kClass)
            kClass.isSubclassOf(Enum::class) -> createEnumInstance(kClass)
            kClass.constructors.isNotEmpty() -> createClassInstanceWithConstructor(kClass)
            else -> throw IllegalArgumentException("Unable to create dummy instance: class=${kClass.simpleName}")
        }
    }

    /**
     * Creates a dummy instance for data classes.
     *
     * @param kClass The data class for which a dummy instance will be created.
     * @return A dummy instance of the given data class.
     */
    private fun createDataClassInstance(kClass: KClass<*>): Any {
        val primaryConstructor =
            kClass.primaryConstructor
                ?: throw IllegalArgumentException("Unable to find primary constructor: class=${kClass.simpleName}")

        val args =
            primaryConstructor.parameters.associateWith { param ->
                createDefaultInstance(param.type)
            }

        return primaryConstructor.callBy(args)
    }

    /**
     * Creates a dummy instance for enum classes.
     *
     * @param kClass The enum class for which a dummy instance will be created.
     * @return A dummy instance of the given enum class.
     */
    private fun createEnumInstance(kClass: KClass<*>): Any {
        val enumConstants = kClass.java.enumConstants
        return enumConstants.firstOrNull()
            ?: throw IllegalArgumentException("Unable to select enum constant: class=${kClass.simpleName}")
    }

    /**
     * Creates a dummy instance for regular classes by invoking their constructors.
     *
     * @param kClass The class for which a dummy instance will be created.
     * @return A dummy instance of the given class.
     */
    private fun createClassInstanceWithConstructor(kClass: KClass<*>): Any {
        val primaryConstructor =
            kClass.constructors.firstOrNull()
                ?: throw IllegalArgumentException("Unable to find constructor: class=${kClass.simpleName}")

        val args =
            primaryConstructor.parameters.associateWith { param ->
                createDefaultInstance(param.type)
            }

        return primaryConstructor.callBy(args)
    }
}
