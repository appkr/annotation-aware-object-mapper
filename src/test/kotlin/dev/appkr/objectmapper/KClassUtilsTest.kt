@file:Suppress("ktlint")
package dev.appkr.objectmapper

import dev.appkr.objectmapper.KClassUtils.DUMMY_STRING
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class KClassUtilsTest  : DescribeSpec() {
    init {
        describe("create") {
            context("when StoreCollection::class was given") {
                it("can generate dummy object of StoreCollection") {
                    val dummyObject = KClassUtils.dummy(StoreCollection::class)

                    dummyObject shouldBe StoreCollection(
                        store = Store(
                            book = listOf(
                                Book(
                                    category = DUMMY_STRING,
                                    author = DUMMY_STRING,
                                    title = DUMMY_STRING,
                                    price = 0.0,
                                    isbn = DUMMY_STRING,
                                )
                            ),
                            bicycle = Bicycle(
                                color = DUMMY_STRING,
                                price = 0.0,
                            )
                        ),
                        meta = mapOf(
                            Code.S to Message(DUMMY_STRING),
                        )
                    )
                }
            }
        }
    }
}

internal data class Book(val category: String, val author: String, val title: String, val price: Double, val isbn: String? = null)
internal data class Bicycle(val color: String, val price: Double)
internal data class Store(val book: List<Book>, val bicycle: Bicycle)
internal data class StoreCollection(val store: Store, val meta: Map<Code, Message>)
internal enum class Code { S, F }
@JvmInline internal value class Message(val value: String)
