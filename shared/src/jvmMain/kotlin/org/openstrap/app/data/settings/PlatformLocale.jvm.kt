package org.openstrap.app.data.settings

import java.util.Locale

/** `java.util.Locale.getDefault().country`, as `SettingsStore.kt` @ 19f2865 line 47 reads it. */
internal actual fun platformUsesImperial(): Boolean = Locale.getDefault().country in UserProfile.IMPERIAL_REGIONS
