# AnnotationAwareObjectMapper

`AnnotationAwareObjectMapper` is a Kotlin utility for mapping properties from one object (`from` model) to another object (`to` model) using annotations, custom mappers, and built-in type conversions.

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
    @MapTo("age") val stringAge: String?
    // Other fields...
)

data class User(
    val name: String,
    val age: BigDecimal?
    // Other fields...
)
```

### 2. Register Custom Mappers

In case your models have completely different structures, you can register custom mappers in the CustomMapperRegistry.

```kotlin
class UserResourceToUserMapper : CustomMapper<UserResource, User> {
    override fun map(from: UserResource): User {
        return User(
            name = from.fullName,
            age = from.stringAge?.toBigDecimal(),
            // Other mappings...
        )
    }
}
```

```kotlin
val customMapperRegistry = CustomMapperRegistry().apply {
    registerMapper(UserResource::class, User::class, UserResourceToUserMapper())
}

val mapper = AnnotationAwareObjectMapper(customMapperRegistry)
```

### 3. Copy Properties

You can copy properties from the from object to the to object, and the registered custom mappers and type conversions will be applied automatically.

```kotlin
val userResource = UserResource(fullName = "John Doe", stringAge = "30")
val user = objectMapper.copyProperties(userResource, User::class)
```

## Installation

Since this library is not published to a Maven repository like Sonatype, you can add it to your project by directly including the GitHub repository in your build script.

### Gradle

To add the library to your Gradle project, you need to include the following in your `build.gradle.kts` file:

1. Add the GitHub repository to your `repositories` block:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/appkr/annotation-aware-object-mapper")
    }
}
```

2. Add the dependency to your dependencies block:

```kotlin
dependencies {
    implementation "com.github.appkr:AnnotationAwareObjectMapper:0.0.1"
}
```
