package org.openstrap.app.data.settings

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

/** The iOS equivalent of `Locale.getDefault().country`: the region the phone is set to. */
internal actual fun platformUsesImperial(): Boolean =
    NSLocale.currentLocale.countryCode?.uppercase() in UserProfile.IMPERIAL_REGIONS
