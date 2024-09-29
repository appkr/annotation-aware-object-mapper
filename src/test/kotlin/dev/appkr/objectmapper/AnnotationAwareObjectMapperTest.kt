package dev.appkr.objectmapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class AnnotationAwareObjectMapperTest : DescribeSpec() {
    init {
        describe("copyProperties") {
            val mapper =
                AnnotationAwareObjectMapper(customMapperRegistry = CustomMapperRegistry())

            context("when primary constructor does not exist") {
                it("should throws an exception") {
                    shouldThrow<IllegalStateException> {
                        mapper.copyProperties(PrimitiveSource("foo"), NoConstructorTarget::class)
                    }
                }
            }

            context("when matching property does not exist") {
                it("should throws an exception") {
                    shouldThrow<IllegalStateException> {
                        mapper.copyProperties(PrimitiveSource("foo"), NoMatchingPropertyTarget::class)
                    }
                }
            }

            context("when matching property provided by MapTo annotation does not exist") {
                it("should throws an exception") {
                    shouldThrow<IllegalStateException> {
                        mapper.copyProperties(PrimitiveSource("foo"), NoMatchingPropertyTarget::class)
                    }
                }
            }

            context("StringTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("foo"), StringTarget::class) shouldBe
                        StringTarget("foo")
                }
            }

            context("DifferentPropertyNameTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource(value = "foo"), DifferentPropertyNameTarget::class) shouldBe
                        DifferentPropertyNameTarget(name = "foo")
                }
            }

            context("NullableTarget") {
                it("when null is passed, copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource(null), NullableTarget::class) shouldBe
                        NullableTarget(null)
                }

                it("when value is passed, copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("foo"), NullableTarget::class) shouldBe
                        NullableTarget("foo")
                }
            }

            context("DefaultValueTarget") {
                it("when null is passed, throws exception") {
                    shouldThrow<IllegalStateException> {
                        mapper.copyProperties(PrimitiveSource(null), DefaultValueTarget::class)
                    }
                }

                it("when value is passed, copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("foo"), DefaultValueTarget::class) shouldBe
                        DefaultValueTarget("foo")
                }
            }

            context("NullableWithDefaultValueTarget") {
                it("when null is passed, throws exception") {
                    mapper.copyProperties(PrimitiveSource(null), NullableWithDefaultValueTarget::class) shouldBe
                        NullableWithDefaultValueTarget(null)
                }

                it("when value is passed, copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("foo"), NullableWithDefaultValueTarget::class) shouldBe
                        NullableWithDefaultValueTarget("foo")
                }
            }

            context("IntTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1"), IntTarget::class) shouldBe
                        IntTarget(1)
                }
            }

            context("LongTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1"), LongTarget::class) shouldBe
                        LongTarget(1L)
                }
            }

            context("DoubleTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1.00"), DoubleTarget::class) shouldBe
                        DoubleTarget(1.00)
                }
            }

            context("BigDecimalTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1.0"), BigDecimalTarget::class) shouldBe
                        BigDecimalTarget(1.00.toBigDecimal())
                }
            }

            context("BooleanTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("true"), BooleanTarget::class) shouldBe
                        BooleanTarget(true)
                }
            }

            context("YearTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1970"), YearTarget::class) shouldBe
                        YearTarget(Year.of(1970))
                }
            }

            context("YearMonthTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1970-01"), YearMonthTarget::class) shouldBe
                        YearMonthTarget(YearMonth.of(1970, 1))
                }
            }

            context("LocalDateTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1970-01-01"), LocalDateTarget::class) shouldBe
                        LocalDateTarget(LocalDate.of(1970, 1, 1))
                }
            }

            context("LocalDateTimeTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1970-01-01T00:00:00"), LocalDateTimeTarget::class) shouldBe
                        LocalDateTimeTarget(LocalDateTime.of(1970, 1, 1, 0, 0, 0))
                }
            }

            context("InstantTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1970-01-01T00:00:00Z"), InstantTarget::class) shouldBe
                        InstantTarget(Instant.ofEpochMilli(0L))
                }
            }

            context("ZonedDateTimeTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1970-01-01T00:00:00Z"), ZonedDateTimeTarget::class) shouldBe
                        ZonedDateTimeTarget(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("Z")))
                }
            }

            context("OffsetDateTimeTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(PrimitiveSource("1970-01-01T00:00:00Z"), OffsetDateTimeTarget::class) shouldBe
                        OffsetDateTimeTarget(OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.of("Z")))
                }
            }

            context("UnconvertableTarget") {
                it("throws exception") {
                    shouldThrow<IllegalStateException> {
                        mapper.copyProperties(PrimitiveSource("P23DT23H"), UnconvertableTarget::class)
                    }
                }
            }

            context("ListTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(ListSource(listOf("1", "2", "3")), ListTarget::class) shouldBe
                        ListTarget(listOf(1, 2, 3))
                }
            }

            context("SetTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(SetSource(setOf("1", "2", "3")), SetTarget::class) shouldBe
                        SetTarget(setOf(1L, 2L, 3L))
                }
            }

            context("MapTarget") {
                it("copy property value correctly") {
                    mapper.copyProperties(MapSource(mapOf("apple" to 2, "banana" to 1)), MapTarget::class) shouldBe
                        MapTarget(mapOf("apple" to "2".toBigDecimal(), "banana" to "1".toBigDecimal()))
                }
            }
        }

        describe("copyProperties with CustomMapper") {
            val mapper =
                AnnotationAwareObjectMapper(
                    customMapperRegistry = CustomMapperRegistry()
                        .apply {
                            registerMapper(
                                from = Address::class,
                                to = String::class,
                                mapper = AddressMapper(),
                            )
                            registerMapper(
                                from = Long::class,
                                to = Income::class,
                                mapper = IncomeMapper(),
                            )
                        }
                )

            context("when a CustomMapper was provided") {
                val address = Address(
                    city = "Washington DC",
                    street = "1600 Pennsylvania Avenue",
                )

                it("copy property value correctly") {
                    mapper.copyProperties(CustomSource("John Doe", address, 100_000_000L), CustomTarget::class) shouldBe
                        CustomTarget("John Doe", "Washington DC, 1600 Pennsylvania Avenue", Income(BigDecimal.valueOf(100_000_000)))
                }
            }
        }
    }
}

// Data Class
internal data class Address(val city: String, val street: String)
internal class AddressMapper : CustomMapper<Address, String> {
    override fun map(from: Address): String = "${from.city}, ${from.street}"
}

@JvmInline internal value class Income(val value: BigDecimal)
internal class IncomeMapper : CustomMapper<Long, Income> {
    override fun map(from: Long): Income = Income(BigDecimal.valueOf(from))
}

// Source Class
internal data class PrimitiveSource(@MapTo("name") val value: String?)
internal data class ListSource(val values: List<String>)
internal data class SetSource(val values: Set<String>)
internal data class MapSource(val values: Map<String, Int>)
internal data class InlineSource(val value: String)
internal data class CustomSource(@MapTo("name") val value: String, val address: Address, val income: Long)

// Target Class
internal class NoConstructorTarget { constructor() }
internal data class NoMatchingPropertyTarget(val amount: String)
internal data class StringTarget(val value: String)
internal data class DifferentPropertyNameTarget(val name: String)
internal data class NullableTarget(val value: String?)
internal data class DefaultValueTarget(val value: String = "bar")
internal data class NullableWithDefaultValueTarget(val value: String? = "bar")
internal data class IntTarget(val value: Int)
internal data class LongTarget(val value: Long)
internal data class DoubleTarget(val value: Double)
internal data class BigDecimalTarget(val value: BigDecimal)
internal data class BooleanTarget(val value: Boolean)
internal data class YearTarget(val value: Year)
internal data class YearMonthTarget(val value: YearMonth)
internal data class LocalDateTarget(val value: LocalDate)
internal data class LocalDateTimeTarget(val value: LocalDateTime)
internal data class InstantTarget(val value: Instant)
internal data class ZonedDateTimeTarget(val value: ZonedDateTime)
internal data class OffsetDateTimeTarget(val value: OffsetDateTime)
internal data class UnconvertableTarget(val value: Duration)
internal data class ListTarget(val values: List<Int>)
internal data class SetTarget(val values: Set<Long>)
internal data class MapTarget(val values: Map<String, BigDecimal>)
internal data class CustomTarget(val name: String, val address: String, val income: Income)
