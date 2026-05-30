package com.eventb.checker

import java.util.Properties

object Version {
    val value: String by lazy {
        packageVersion()
            ?: resourceVersion()
            ?: "dev"
    }

    private fun packageVersion(): String? = Version::class.java.`package`?.implementationVersion?.takeIf { it.isNotBlank() }

    private fun resourceVersion(): String? {
        val props = Properties()
        Version::class.java.getResourceAsStream("/eventb-checker-version.properties").use { stream ->
            if (stream == null) {
                return null
            }
            props.load(stream)
        }
        return props.getProperty("version")?.takeIf { it.isNotBlank() }
    }
}
