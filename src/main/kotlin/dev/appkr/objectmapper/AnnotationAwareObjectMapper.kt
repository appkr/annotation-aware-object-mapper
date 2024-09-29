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
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

class AnnotationAwareObjectMapper(
    private val customMapperRegistry: CustomMapperRegistry,
) {
    /**
     * 주어진 from 객체로부터 to 객체를 생성한다
     */
    fun <T : Any> copyProperties(from: Any, to: KClass<T>): T {
        // to 클래스의 Primary Constructor를 구한다
        val toConstructor: KFunction<T> = to.primaryConstructor
            ?: throw IllegalStateException("${to.simpleName} 클래스의 생성자에 접근할 수 없습니다")

        // to 클래스의 생성자에 전달할 프로퍼티 맵을 구한다
        val toConstructorArgs: Map<KParameter, Any?> = toConstructor.parameters
            .associateWith { findValueOf(constructorParam = it, sourceObject = from) }

        // to 객체를 생성한다
        return toConstructor.callBy(toConstructorArgs)
    }

    /**
     * 원본 객체로부터 생성자의 파라미터에 할당할 값을 찾는다
     *
     * 값을 찾는 규칙:
     *   - 생성자 파라미터의 이름과 원본 객체의 프로퍼티 이름이 같으면
     *   - 생성자 파라미터의 이름과 원보 객체의 프로퍼티에 선언한 [MapTo.value] 값이 같으면
     *
     * @param constructorParam 생성할 클래스의 생성자 파라미터
     * @param sourceObject 파라미터의 값으로 할당할 값을 찾을 원본 객체
     * @throws IllegalStateException
     *   - 생성할 클래스의 생성자 파라미터의 값으로 할당할 값을 원본 객체에서 찾을 수 없는 경우
     *   - 원본 객체로부터 값을 구했으나, 그 값이 null인 반면, 생성할 클래스의 파라미터는 Non-null로 선언된 경우
     *   - 원본 객체에서 일치하는 프로퍼티를 찾았으나,
     *   [TypeConverter], [builtInConvert]를 이용해서 생성자 파라미터에 할당할 수 있는 적절한 값으로 변경하지 못하는 경우
     */
    private fun findValueOf(
        constructorParam: KParameter,
        sourceObject: Any,
    ): Any? {
        // 값을 할당할 생성자 파라미터를 구한다
        val propertyName = constructorParam.name
            ?: throw IllegalStateException("생성자 파라미터 이름이 null일 수 없습니다")

        // 데이터 원본 객체에서 값을 찾을 프로퍼티를 구한다
        val fromProperty = sourceObject::class.memberProperties
            .find { it.findAnnotation<MapTo>()?.value == propertyName || it.name == propertyName }
            ?.apply { isAccessible = true }
            ?: throw IllegalStateException("${propertyName}의 값을 구할 수 없습니다")

        // 맵핑 가능한 프로퍼티는 찾았고, 원본 객체에서 값을 꺼낸다
        val value = fromProperty.call(sourceObject)

        if (value == null) {
            if (constructorParam.type.isMarkedNullable) {
                // If it's possible to assign null to the constructor parameter.
                return value
            }
            throw IllegalStateException("${propertyName}의 값으로 null을 할당할 수 없습니다")
        }

        // 꺼낸 값의 타입과, 할당할 파라미터의 타입이 일치하지 않으면, 타입 변환을 시도한다
        if (fromProperty.returnType != constructorParam.type) {
            val convertedValue = tryConvert(
                value = value,
                fromType = fromProperty.returnType,
                toType = constructorParam.type,
            )
                ?: throw IllegalStateException(
                    "'${constructorParam.name}: ${constructorParam.type}'에" +
                        "'${fromProperty.name}: ${fromProperty.returnType}'을 할당할 수 없습니다",
                )

            return convertedValue
        }

        return value
    }

    /**
     * 타입 변환을 시도한다
     *
     * @param value 원본 값
     * @param fromType 원본 값의 타입
     * @param toType 대상 값의 타입
     */
    private fun tryConvert(value: Any?, fromType: KType, toType: KType): Any? {
        if (value == null) return null

        // Identify the type parameter for List and recursively convert values.
        if (fromType.isSubtypeOf(typeOf<List<*>>()) && toType.isSubtypeOf(typeOf<List<*>>())) {
            return tryConvertList(value, fromType, toType)
        }

        // Identify the type parameter for Set and recursively convert values.
        if (fromType.isSubtypeOf(typeOf<Set<*>>()) && toType.isSubtypeOf(typeOf<Set<*>>())) {
            return tryConvertSet(value, fromType, toType)
        }

        // Identify the type parameters for Map keys and values, and recursively convert them.
        if (fromType.classifier == Map::class && toType.classifier == Map::class) {
            return tryConvertMap(value, fromType, toType)
        }

        /*
         * Check if the runtime object types are different.
         *   - While List<String> and List<Int> both have jvmErasure of List::class, so this condition is not met.
         *   - Foo and Bar have jvmErasure of Foo::class and Bar::class respectively, so this condition is met.
         */
        if (fromType.jvmErasure != toType.jvmErasure) {
            // If object types are different, try CustomMapper.
            val mappedValue = tryConvertWithCustomMapper(value, fromType, toType)
            if (mappedValue != null) return mappedValue
        }

        // Attempt built-in type conversion.
        val primitiveValue = tryConvertPrimitive<Any>(value, toType)
        if (primitiveValue != null) return primitiveValue

        // If reached here, conversion is not possible, return null to exit recursion.
        return null
    }

    private fun tryConvertList(
        value: Any,
        fromType: KType,
        toType: KType,
    ): List<*> {
        val fromList = value as List<*>
        val toElementType =
            toType.arguments.first().type
                ?: throw IllegalStateException(
                    "Unable to identify target collection element type: " +
                        "fromType=$fromType, toType=$toType",
                )

        return fromList.map {
            tryConvert(
                value = it,
                fromType = it?.javaClass?.kotlin?.starProjectedType ?: fromType,
                toType = toElementType,
            )
                ?: throw IllegalStateException("Unable to get collection element value: element=$it")
        }
    }

    private fun tryConvertSet(
        value: Any,
        fromType: KType,
        toType: KType,
    ): Set<*> {
        val fromSet = value as Set<*>
        val toElementType =
            toType.arguments.first().type
                ?: throw IllegalStateException(
                    "Unable to identify target collection element type: " +
                        "fromType=$fromType, toType=$toType",
                )

        return fromSet
            .map {
                tryConvert(
                    value = it,
                    fromType = it?.javaClass?.kotlin?.starProjectedType ?: fromType,
                    toType = toElementType,
                )
                    ?: throw IllegalStateException("Unable to get collection element value: element=$it")
            }.toSet()
    }

    private fun tryConvertMap(
        value: Any,
        fromType: KType,
        toType: KType,
    ): Map<*, *> {
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

    private fun tryConvertWithCustomMapper(
        value: Any,
        fromType: KType,
        toType: KType,
    ): Any? {
        val mapper = customMapperRegistry.getMapper(fromType.jvmErasure, toType.jvmErasure)
        return (mapper as? CustomMapper<Any, Any>)?.map(value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> tryConvertPrimitive(
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
