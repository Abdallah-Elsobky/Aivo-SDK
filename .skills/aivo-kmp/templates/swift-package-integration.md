# iOS Xcode Integration for Aivo SDK KMP

Follow these steps to integrate the compiled KMP framework containing **Aivo SDK** into your iOS Xcode project.

---

## Step 1: Framework Export in Gradle
Ensure your `shared/build.gradle.kts` has exported the SDK:
```kotlin
iosTarget.binaries.framework {
    baseName = "SharedAiKit"
    isStatic = true
    export("io.github.abdallah-elsobky:aivo-sdk:1.0.1")
}
```

## Step 2: Compile Framework
Run in terminal:
```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
Or for local simulator testing:
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## Step 3: Link in Xcode
1. Open your Xcode project (`iosApp.xcodeproj` or `.xcworkspace`).
2. Select your App target → **General** tab.
3. Under **Frameworks, Libraries, and Embedded Content**, ensure `SharedAiKit.framework` is listed with **Embed & Sign**.
4. In Build Settings, ensure **Framework Search Paths** includes the build output folder:
   `$(SRCROOT)/../shared/build/bin/iosSimulatorArm64/debugFramework`

## Step 4: Import in Swift
```swift
import SwiftUI
import SharedAiKit

struct ContentView: View {
    var body: some View {
        Text("Aivo SDK ready on iOS!")
    }
}
```
