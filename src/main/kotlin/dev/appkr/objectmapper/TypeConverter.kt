package dev.appkr.objectmapper

import kotlin.reflect.KType

interface TypeConverter {
    /**
     * 원본 값을 목적 타입으로 변경한다
     *
     * @param value 원본 값
     * @param targetType 원본 값을 변경하여 얻을 목적 타입
     */
    fun <T> convert(value: Any, targetType: KType): T?
}
