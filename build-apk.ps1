# Builds the Downtify Android APK (debug + release).
#
# Needs on this machine (once):
#   - a JDK 21 for Gradle itself (JAVA_HOME or -Jdk21)
#   - a JDK 17 for the compile toolchain
#   - the Android SDK with platform-37 + build-tools 37 (ANDROID_HOME or -Sdk)
#
# Example:
#   .\build-apk.ps1
#   .\build-apk.ps1 -Jdk21 E:\toolchains\jdk21\jdk-21.0.12.1+1 -Jdk17 E:\toolchains\jdk17\jdk-17.0.20.1+1 -Sdk E:\toolchains\sdk
#
# The APKs land in app\build\outputs\apk\debug and ...\release.
# The release APK is debug-signed unless the DOWNTIFY_KEYSTORE_* env vars are set (see README / release.yml).

param(
    [string]$Jdk21 = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "E:\toolchains\jdk21\jdk-21.0.12.1+1" }),
    [string]$Jdk17 = "E:\toolchains\jdk17\jdk-17.0.20.1+1",
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "E:\toolchains\sdk" })
)

$ErrorActionPreference = "Stop"

function Require-Dir($path, $name) {
    if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path -PathType Container)) {
        throw "$name not found: '$path'. Pass it explicitly, e.g. -Jdk21 E:\toolchains\jdk21\jdk-21.0.12.1+1"
    }
    return (Resolve-Path -LiteralPath $path).Path
}

$Jdk21 = Require-Dir $Jdk21 "JDK 21 (Gradle JVM)"
$Jdk17 = Require-Dir $Jdk17 "JDK 17 (compile toolchain)"
$Sdk = Require-Dir $Sdk "Android SDK"

$java = Join-Path $Jdk21 "bin\java.exe"
if (-not (Test-Path -LiteralPath $java)) { throw "No java.exe in $Jdk21\bin" }
Write-Output "Using JDK: $Jdk21"

$env:JAVA_HOME = $Jdk21
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

& .\gradlew.bat build detekt spotlessCheck --console=plain "-Porg.gradle.java.installations.paths=$Jdk17"
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)" }

Get-ChildItem app\build\outputs\apk -Recurse -Filter *.apk | Select-Object FullName, Length
