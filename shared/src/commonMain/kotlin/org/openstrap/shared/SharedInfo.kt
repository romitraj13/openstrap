package org.openstrap.shared

/** Plain Kotlin, called from Swift as `SharedInfo.shared.greeting()`. */
object SharedInfo {
    fun greeting(): String = "OpenStrap shared module is linked"
}
