# Aivo SDK KMP — Multiplatform Tools Guide

How to build tools in Kotlin Multiplatform that interact with platform capabilities (Android, iOS, JVM).

---

## Pattern 1: Pure Domain Tools in `commonMain`
Tools that require only business logic, math, or remote REST APIs can be written entirely in `commonMain`:

```kotlin
// commonMain
val currencyConverterTool = tool("convert_currency", "Converts between currency rates") {
    param("amount", "Amount to convert", type = ParamType.Double, required = true)
    param("from", "Source currency code (USD, EUR)", type = ParamType.String, required = true)
    param("to", "Target currency code", type = ParamType.String, required = true)

    execute { args ->
        val amount = args.double("amount")
        val from = args.string("from")
        val to = args.string("to")
        // Pure shared calculation or Ktor call
        val converted = amount * 1.08 // Example rate
        """{"convertedAmount": $converted, "currency": "$to"}"""
    }
}
```

---

## Pattern 2: Platform Capabilities via Interface Injection

When a tool requires native platform APIs (e.g. Geolocation, Clipboard, Camera, File System), define an interface in `commonMain` and inject the platform implementation:

```kotlin
// commonMain
interface PlatformLocationService {
    suspend fun getCurrentCoordinates(): Pair<Double, Double>?
}

fun createLocationTool(service: PlatformLocationService): Tool = tool(
    name = "get_current_location",
    description = "Retrieves user's current GPS latitude and longitude"
) {
    execute { _ ->
        val coords = service.getCurrentCoordinates()
        if (coords != null) {
            """{"latitude": ${coords.first}, "longitude": ${coords.second}}"""
        } else {
            """{"error": "Location permission denied or unavailable"}"""
        }
    }
}
```

```kotlin
// androidMain implementation
class AndroidLocationService(private val context: Context) : PlatformLocationService {
    override suspend fun getCurrentCoordinates(): Pair<Double, Double>? {
        // Use Google Play Services FusedLocationProviderClient
        return 37.7749 to -122.4194
    }
}

// iosMain implementation
class IosLocationService : PlatformLocationService {
    override suspend fun getCurrentCoordinates(): Pair<Double, Double>? {
        // Use CLLocationManager
        return 37.7749 to -122.4194
    }
}
```
