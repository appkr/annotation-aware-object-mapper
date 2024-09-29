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
            if (constructorParam.isOptional || constructorParam.type.isMarkedNullable) {
                // 생성자 파라미터에 null 할당을 할 수 있는 상황이라면
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

        // @JvmInline 클래스는 TypeConverter를 사용할 것을 기대한다
        if (fromType.jvmErasure.isInlineClass() || toType.jvmErasure.isInlineClass()) {
            // 이미 변환된 @JvmInline 클래스에 대한 중복 변환 방지
            if (value::class == toType.jvmErasure) return value
        }

        // Collection의 타입파라미터 타입을 식별하고, 재귀적으로 값을 변환한다
        if (fromType.classifier == Collection::class && toType.classifier == Collection::class) {
            val fromCollection = value as Collection<*>
            val toElementType = toType.arguments.first().type
                ?: throw IllegalStateException("목표 콜렉션의 타입을 확인할 수 없습니다")

            return fromCollection.map {
                tryConvert(
                    value = it,
                    fromType = it?.javaClass?.kotlin?.starProjectedType ?: fromType,
                    toType = toElementType,
                )
                    ?: throw IllegalStateException("콜렉션 엘리먼트의 값을 얻지 못했습니다: $it")
            }
        }

        // Map의 키, 값 각각의 타입파라미터 타입을 식별하고, 재귀적으로 값을 변환한다
        if (fromType.classifier == Map::class && toType.classifier == Map::class) {
            val fromMap = value as Map<*, *>
            val toKeyType = toType.arguments[0].type
                ?: throw IllegalStateException("목표 맵의 키 타입을 확인할 수 없습니다")
            val toValueType = toType.arguments[1].type
                ?: throw IllegalStateException("목표 맵의 값 타입을 확인할 수 없습니다")

            return fromMap
                .mapKeys { (key, _) ->
                    tryConvert(
                        value = key,
                        fromType = key?.javaClass?.kotlin?.starProjectedType ?: fromType,
                        toType = toKeyType
                    )
                        ?: throw IllegalStateException("맵의 키를 얻지 못했습니다: $key")
                }
                .mapValues { (_, mapValue) ->
                    tryConvert(
                        value = mapValue,
                        fromType = mapValue?.javaClass?.kotlin?.starProjectedType ?: fromType,
                        toType = toValueType,
                    )
                        ?: throw IllegalStateException("맵의 값을 얻지 못했습니다: $mapValue")
                }
        }

        /*
         * 런타임시 객체 타입이 서로 다른지 확인한다
         *   - Foo와 Bar의 jvmErasure는 각각 Foo::class와 Bar::class이므로 아래 조건물을 충족한다
         *   - List<String>과 List<Int>의 jvmErasure는 List::class로 같으므로 아래 조건문을 충족하지 않는다
         */
        if (fromType.jvmErasure != toType.jvmErasure) {
            // 객체 타입이 다른 경우에는 CustomMapper를 시도해본다
            val mapper = customMapperRegistry.getMapper(fromType.jvmErasure, toType.jvmErasure)
            val mappedValue = (mapper as? CustomMapper<Any, Any>)?.map(value)
            if (mappedValue != null) return mappedValue
        }

        // 내장된 타입 변환을 시도한다
        val classConversion = builtInConvert<Any>(value, toType)
        if (classConversion != null) return classConversion

        // 여기까지 도달했다면 변환이 불가한 상황이라, null을 반환해서 재귀를 탈출하도록 한다
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> builtInConvert(value: Any, toType: KType): T? {
        return when (toType.classifier) {
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
}

private fun KClass<*>.isInlineClass(): Boolean {
    return this.hasAnnotation<JvmInline>()
}
