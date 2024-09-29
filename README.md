# AnnotationAwareObjectMapper

This project provides an `AnnotationAwareObjectMapper` that enables property mapping between two classes based on annotations and custom mappers. It supports both simple types and complex collections and maps, with recursive conversion capabilities. The mapper is highly customizable via a `CustomMapperRegistry` that allows for registering and utilizing custom mappers for specific types.


## Features

- **Custom Annotations**: Use `@MapTo` to map properties between different field names.
- **Custom Mappers**: Register and use custom mappers for complex object transformations using the `CustomMapperRegistry`.
- **Recursive Collection and Map Conversion**: Supports recursive conversion for collections and maps, ensuring complex data structures are correctly mapped.
- **Type Conversion**: Handles basic type conversion between `from` and `to` models using either built-in converters or custom `TypeConverter` implementations.

## Usage

### 1. Define Models with Annotations

You can annotate your source model's properties with `@MapTo` to specify the target property name in the destination model.

```kotlin
data class UserResource(
    @MapTo("name") val fullName: String,
    val address: Address,
    val birthday: LocalDate?,
    val contacts: List<Contact>,
    val families: Map<Relationship, UserResource>,
    val friends: Set<UserResource>,
    // more properties
)

data class User(
    val name: String,
    val address: String,
    val birthday: LocalDate? = LocalDate.ofEpochDay(0),
    val height: BigDecimal,
    val contacts: List<Contact>,
    val families: Map<Relationship, User>,
    val friends: Set<User>,
)
```

### 2. Register Custom Mappers

In case your models have completely different structures, you can register custom mappers in the CustomMapperRegistry.

```kotlin
class AddressMapper : CustomMapper<Address, String> {
    override fun map(from: Address): String = "${from.city}, ${from.street}"
}
```

```kotlin
val customMapperRegistry = CustomMapperRegistry().apply {
    registerMapper(Address::class, String::class, AddressMapper())
}

val mapper = AnnotationAwareObjectMapper(customMapperRegistry)
```

### 3. Copy Properties

You can copy properties from the `from` object to the `to` object, and the registered custom mappers and type conversions will be applied automatically.

```kotlin
val userResource = UserResource(fullName = "John Doe", stringAge = "30")
val user = objectMapper.copyProperties(userResource, User::class)
```

## Installation

You can clone this repository and integrate it into your Kotlin project.

```shell
git clone https://github.com/appkr/annotation-aware-object-mapper.git
```

## Usage

### Basic Usage

The primary method for mapping properties is copyProperties. This method copies properties from a source object to a target class using the primary constructor of the target class. The `@MapTo` annotation can be used to define how properties from the source class map to the target class.

```kotlin
data class SourceClass(val firstName: String, val age: Int)
data class TargetClass(val name: String, val age: Int)

val source = SourceClass(firstName = "John", age = 25)
val mapper = AnnotationAwareObjectMapper(CustomMapperRegistry())
val target = mapper.copyProperties(source, TargetClass::class)
// TargetClass(name="John", age=25)
```

### Using @MapTo Annotation

You can specify custom mappings using the `@MapTo` annotation on properties of the source class.

```kotlin
data class AnnotatedSource(@MapTo("name") val firstName: String, val age: Int)
data class TargetClass(val name: String, val age: Int)

val source = AnnotatedSource(firstName = "John", age = 25)
val target = mapper.copyProperties(source, TargetClass::class)
// TargetClass(name="John", age=25)
```

### Custom Mappers

For types that require special handling, you can register a custom mapper via `CustomMapperRegistry`.

```kotlin
data class Address(val city: String, val street: String)
data class CustomSource(@MapTo("name") val value: String, val address: Address)
data class CustomTarget(val name: String, val fullAddress: String)

class AddressMapper : CustomMapper<Address, String> {
    override fun map(from: Address): String = "${from.city}, ${from.street}"
}

val customRegistry = CustomMapperRegistry()
customRegistry.registerMapper(Address::class, String::class, AddressMapper())

val customMapper = AnnotationAwareObjectMapper(customRegistry)
val source = CustomSource("John Doe", Address("Washington DC", "1600 Pennsylvania Avenue"))
val target = customMapper.copyProperties(source, CustomTarget::class)
// CustomTarget(name="John Doe", fullAddress="Washington DC, 1600 Pennsylvania Avenue")
```

### Handling Collections and Maps

The `AnnotationAwareObjectMapper` can recursively convert collections and maps.

```kotlin
data class ListSource(val values: List<String>)
data class ListTarget(val values: List<Int>)

val source = ListSource(listOf("1", "2", "3"))
val target = mapper.copyProperties(source, ListTarget::class)
// ListTarget(values=[1, 2, 3])
```

It can also handle nested maps:

```kotlin
data class MapSource(val values: Map<String, Int>)
data class MapTarget(val values: Map<String, BigDecimal>)

val source = MapSource(mapOf("apple" to 2, "banana" to 1))
val target = mapper.copyProperties(source, MapTarget::class)
// MapTarget(values={"apple"=2.00, "banana"=1.00})
```

### Built-in Type Conversion

The mapper supports built-in conversions for common types such as `String`, `Int`, `BigDecimal`, `LocalDate`, `LocalDateTime`, `ZonedDateTime`, `Instant`, and more.

```kotlin
data class PrimitiveSource(@MapTo("value") val value: String?)
data class IntTarget(val value: Int)

val source = PrimitiveSource("1")
val target = mapper.copyProperties(source, IntTarget::class)
// IntTarget(value=1)
```

## Supported Built-in Conversions

- **Primitives**: `Int`, `Long`, `Double`, `Boolean`
- **Date/Time**: `Year`, `YearMonth`, `LocalDate`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `OffsetDateTime`
- **Financial**: `BigDecimal`
- **Inline classes**: Supports Kotlin’s `@JvmInline` classes

## Testing

Unit tests are included in the project using Kotest. The tests cover various scenarios, including:

- Mapping properties between classes
- Handling `@MapTo` annotations
- Recursive collection and map conversions
- Custom mapper usage
- Built-in type conversion handling

To run the tests, you can execute the following command:

```shell
./gradlew test
```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.
