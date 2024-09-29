package dev.appkr.objectmapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.reflect.KType

class AnnotationAwareObjectMapperTest : DescribeSpec() {
    init {
        describe("map") {
            val customMapper = AnnotationAwareObjectMapper(
                customTypeConverter = object : TypeConverter {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T> convert(value: Any, targetType: KType): T? {
                        return when (targetType.classifier) {
                            BigDecimal::class -> (value as? String)?.toBigDecimal() as? T
                            Int::class -> (value as? String)?.toIntOrNull() as? T
                            else -> null
                        }
                    }
                }
            )

            context("when called correctly") {
                it("should map correctly") {
                    customMapper.copyProperties(johnDoe, User::class) shouldBe
                            User(
                                name = "John Doe",
                                address = Address("San", "NewYork"),
                                mbti = Mbti(MbtiEnum.Y, MbtiEnum.Y, MbtiEnum.Y, MbtiEnum.Y),
                                age = BigDecimal.valueOf(40),
                                birthday = LocalDate.ofEpochDay(0),
                                height = BigDecimal.valueOf(180),
                                contacts = listOf(Contact("010-1234-5678"), Contact("02-1234-5678")),
                                families = mapOf(
                                    Relationship("Dad") to User(
                                        name = "William Doe",
                                        address = Address("San", "NewYork"),
                                        mbti = Mbti(MbtiEnum.N, MbtiEnum.N, MbtiEnum.N, MbtiEnum.N),
                                        age = BigDecimal.valueOf(80),
                                        birthday = null,
                                        height = BigDecimal.valueOf(170),
                                        contacts = emptyList(),
                                        families = emptyMap(),
                                        friends = emptyList()
                                    ),
                                ),
                                friends = listOf(
                                    User(
                                        name = "John Smith",
                                        address = Address("San", "NewYork"),
                                        mbti = Mbti(MbtiEnum.Y, MbtiEnum.N, MbtiEnum.Y, MbtiEnum.N),
                                        age = BigDecimal.valueOf(38),
                                        birthday = null,
                                        height = BigDecimal.valueOf(175),
                                        contacts = emptyList(),
                                        families = emptyMap(),
                                        friends = emptyList()
                                    ),
                                )
                            )
                }
            }
        }

        describe("map fail cases") {
            val mapper = AnnotationAwareObjectMapper()

            withData(
                nameFn = { "$it" },
                ts = listOf(
                    UserWithTypeMismatch::class,
                    UserWithNonNullProperty::class,
                    UserWithoutConstructor::class,
                )
            ) {
                shouldThrow<IllegalStateException> {
                    mapper.copyProperties(invalid, it)
                }
            }
        }
    }

    private val invalid = UserResource(
        fullName = "Anonymous",
        address = Address("Unknown", "Unknown"),
        mbti = Mbti(MbtiEnum.N, MbtiEnum.N, MbtiEnum.N, MbtiEnum.N),
        stringAge = null,
        birthday = null,
        height = "0",
        contacts = listOf(),
        families = emptyMap(),
        friends = emptySet()
    )
    private val johnSmith = UserResource(
        fullName = "John Smith",
        address = Address("San", "NewYork"),
        mbti = Mbti(MbtiEnum.Y, MbtiEnum.N, MbtiEnum.Y, MbtiEnum.N),
        stringAge = "38",
        birthday = null,
        height = "175",
        contacts = listOf(),
        families = emptyMap(),
        friends = emptySet()
    )
    private val williamDoe = UserResource(
        fullName = "William Doe",
        address = Address("San", "NewYork"),
        mbti = Mbti(MbtiEnum.N, MbtiEnum.N, MbtiEnum.N, MbtiEnum.N),
        stringAge = "80",
        birthday = null,
        height = "170",
        contacts = emptyList(),
        families = emptyMap(),
        friends = emptySet()
    )
    private val johnDoe = UserResource(
        fullName = "John Doe",
        address = Address("San", "NewYork"),
        mbti = Mbti(MbtiEnum.Y, MbtiEnum.Y, MbtiEnum.Y, MbtiEnum.Y),
        stringAge = "40",
        birthday = LocalDate.ofEpochDay(0),
        height = "180",
        contacts = listOf(Contact("010-1234-5678"), Contact("02-1234-5678")),
        families = mapOf(Relationship("Dad") to williamDoe),
        friends = setOf(johnSmith)
    )
}

internal data class Address(val city: String, val state: String)
internal data class Mbti(val m: MbtiEnum, val b: MbtiEnum, val t: MbtiEnum, val i: MbtiEnum)
internal enum class MbtiEnum { Y, N; }

@JvmInline internal value class Contact(val value: String)

@JvmInline internal value class Relationship(val value: String)

internal data class UserResource(
    @MapTo("name") val fullName: String,
    val address: Address,
    val mbti: Mbti,
    @MapTo("age") val stringAge: String?,
    val birthday: LocalDate?,
    val height: String,
    val contacts: Collection<Contact>,
    val families: Map<Relationship, UserResource>,
    val friends: Collection<UserResource>,
)

internal data class User(
    val name: String,
    val address: Address,
    val mbti: Mbti,
    val age: BigDecimal?,
    val birthday: LocalDate? = LocalDate.ofEpochDay(0),
    val height: BigDecimal,
    val contacts: Collection<Contact>,
    val families: Map<Relationship, User>,
    val friends: Collection<User>,
)

internal data class UserWithTypeMismatch(val age: Short)
internal data class UserWithNonNullProperty(val age: Int)
internal class UserWithoutConstructor {
    constructor(name: String)
}
