package dev.appkr.objectmapper

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LibraryTest : FunSpec() {
    init {
        test("Test Setup") {
            Library().someLibraryMethod() shouldBe true
        }
    }
}
