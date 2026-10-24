package org.openstrap.app.data.settings

/**
 * Whether the phone's region shows pounds and feet, for [UserProfile.usesImperial] when the user
 * hasn't chosen. Android reads `java.util.Locale.getDefault().country`; each target does the same
 * for its own locale here.
 */
internal expect fun platformUsesImperial(): Boolean
