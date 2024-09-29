@file:Suppress("ktlint")
package dev.appkr.objectmapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

class AnnotationAwareObjectMapperTest : DescribeSpec() {
    init {
        describe("copyProperties") {
            val mapper =
                AnnotationAwareObjectMapper(
                    customMapperRegistry =
                        CustomMapperRegistry()
                            .apply {
                                registerMapper(
                                    from = UserResource::class,
                                    to = User::class,
                                    mapper = UserResourceToUserMapper(),
                                )
                            },
                )

            context("when called correctly") {
                val expected =
                    User(
                        name = "John Doe",
                        address = Address("San", "NewYork"),
                        mbti = Mbti(MbtiEnum.Y, MbtiEnum.Y, MbtiEnum.Y, MbtiEnum.Y),
                        age = BigDecimal.valueOf(40),
                        birthday = LocalDate.ofEpochDay(0),
                        height = BigDecimal.valueOf(180),
                        contacts =
                            listOf(
                                Contact("010-1234-5678"),
                                Contact("02-1234-5678"),
                            ),
                        families =
                            mapOf(
                                Relationship("Dad") to
                                    User(
                                        name = "William Doe",
                                        address = Address("San", "NewYork"),
                                        mbti = Mbti(MbtiEnum.N, MbtiEnum.N, MbtiEnum.N, MbtiEnum.N),
                                        age = BigDecimal.valueOf(80),
                                        birthday = null,
                                        height = BigDecimal.valueOf(170),
                                        contacts = emptyList(),
                                        families = emptyMap(),
                                        friends = emptyList(),
                                    ),
                            ),
                        friends =
                            listOf(
                                User(
                                    name = "John Smith",
                                    address = Address("San", "NewYork"),
                                    mbti = Mbti(MbtiEnum.Y, MbtiEnum.N, MbtiEnum.Y, MbtiEnum.N),
                                    age = BigDecimal.valueOf(38),
                                    birthday = null,
                                    height = BigDecimal.valueOf(175),
                                    contacts = emptyList(),
                                    families = emptyMap(),
                                    friends = emptyList(),
                                ),
                            ),
                    )

                it("should copy all properties from source object and create a target instance correctly") {
                    val actual = mapper.copyProperties(johnDoe, User::class)
                    actual shouldBe expected
                }
            }
        }

        describe("failing cases") {
            val mapper = AnnotationAwareObjectMapper(customMapperRegistry = CustomMapperRegistry())

            withData(
                nameFn = { "${it.simpleName}" },
                ts =
                    listOf(
                        UserWithTypeMismatch::class,
                        UserWithNonNullProperty::class,
                        UserWithoutConstructor::class,
                    ),
            ) {
                shouldThrow<IllegalStateException> {
                    mapper.copyProperties(invalid, it)
                }
            }
        }
    }

    private val invalid =
        UserResource(
            fullName = "Anonymous",
            address = Address("Unknown", "Unknown"),
            mbti = Mbti(MbtiEnum.N, MbtiEnum.N, MbtiEnum.N, MbtiEnum.N),
            stringAge = null,
            birthday = null,
            height = "0",
            contacts = listOf(),
            families = emptyMap(),
            friends = emptyList(),
        )

    private val johnSmith =
        UserResource(
            fullName = "John Smith",
            address = Address("San", "NewYork"),
            mbti = Mbti(MbtiEnum.Y, MbtiEnum.N, MbtiEnum.Y, MbtiEnum.N),
            stringAge = "38",
            birthday = null,
            height = "175",
            contacts = listOf(),
            families = emptyMap(),
            friends = emptyList(),
        )

    private val williamDoe =
        UserResource(
            fullName = "William Doe",
            address = Address("San", "NewYork"),
            mbti = Mbti(MbtiEnum.N, MbtiEnum.N, MbtiEnum.N, MbtiEnum.N),
            stringAge = "80",
            birthday = null,
            height = "170",
            contacts = emptyList(),
            families = emptyMap(),
            friends = emptyList(),
        )

    private val johnDoe =
        UserResource(
            fullName = "John Doe",
            address = Address("San", "NewYork"),
            mbti = Mbti(MbtiEnum.Y, MbtiEnum.Y, MbtiEnum.Y, MbtiEnum.Y),
            stringAge = "40",
            birthday = LocalDate.ofEpochDay(0),
            height = "180",
            contacts =
                listOf(
                    Contact("010-1234-5678"),
                    Contact("02-1234-5678"),
                ),
            families = mapOf(Relationship("Dad") to williamDoe),
            friends = listOf(johnSmith),
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

internal class UserResourceToUserMapper : CustomMapper<UserResource, User> {
    override fun map(from: UserResource): User {
        return User(
            name = from.fullName,
            address = from.address,
            mbti = from.mbti,
            age = from.stringAge?.toBigDecimal(),
            birthday = from.birthday,
            height = from.height.toBigDecimal(),
            contacts = from.contacts,
            families = from.families.mapValues { this.map(it.value) },
            friends = from.friends.map { this.map(it) },
        )
    }
}
