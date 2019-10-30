[![Build Status](https://travis-ci.org/anti-social/prometheus-kt.svg?branch=master)](https://travis-ci.org/anti-social/prometheus-kt)
[![codecov](https://codecov.io/gh/anti-social/prometheus-kt/branch/master/graph/badge.svg)](https://codecov.io/gh/anti-social/prometheus-kt)
[![Download](https://api.bintray.com/packages/evo/maven/prometheus-kt/images/download.svg) ](https://bintray.com/evo/maven/prometheus-kt/_latestVersion)

# Prometheus-kt
Prometheus client for Kotlin

## Motivation

1. Kotlin multiplatform support
2. Coroutines friendly (does not use `synchronized` at all)
3. Typed labels
4. Nice DSL
5. Ktor support out of the box

At the moment official prometheus java client has a bit more performance.

## How to use?

Add it into your build script:

`build.gradle.kts`:

```
repositories {
    maven {
        url = uri("https://dl.bintray.com/evo/maven")
    }
}

dependencies {
    implementation("dev.evo", "prometheus-kt-ktor", "0.1.0-alpha-4")
}
```

`build.gradle`:

```
repositories {
    maven {
        url 'https://dl.bintray.com/evo/maven'
    }
}

dependencies {
    implementation "dev.evo:prometheus-kt-ktor:0.1.0-alpha-4"
}
```

Create your own metrics:

```kotlin
import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.jvm.DefaultJvmMetrics

class ProcessingLabels : LabelSet() {
    var source by label()
}

object AppMetrics : PrometheusMetrics() {
    val processedProducts by histogram("processed_products", logScale(0, 2)) {
        ProcessingLabels()
    }
    val jvm by submetrics(DefaultJvmMetrics())
}
```

Use them in your application:

```kotlin
import kotlinx.coroutines.delay

suspend fun startProcessing() {
    while (true) {
        AppMetrics.processedProducts.measureTime({
            source = "manual"
        }) {
            // Processing
            delay(100)
        }
    }
}
```

Then expose them:

```kotlin
import dev.evo.prometheus.ktor.metricsModule
import dev.evo.prometheus.ktor.MetricsFeature

import io.ktor.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import java.util.concurrent.TimeUnit

suspend fun main(args: Array<String>) {
    val metricsApp = embeddedServer(
        Netty,
        port = 9090,
        module = Application::metricsModule
    )
            .start(wait = false)
    
    // Start processing
    startProcessing()
    
    metricsApp.stop(1000, 2000, TimeUnit.MILLISECONDS)
}
```

And finally see them:

```bash
curl -X GET 'localhost:9090/metrics'
```

More samples at: https://github.com/anti-social/prometheus-kt/tree/master/samples

Just run them:

```bash
./gradlew --project-dir samples/processing-app run
```

```bash
./gradlew --project-dir samples/http-app run
```
