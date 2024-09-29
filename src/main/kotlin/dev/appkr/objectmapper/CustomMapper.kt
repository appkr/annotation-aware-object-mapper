package dev.appkr.objectmapper

interface CustomMapper<F, T> {
    fun map(from: F): T
}
