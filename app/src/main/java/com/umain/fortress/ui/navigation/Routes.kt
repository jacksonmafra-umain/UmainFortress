package com.umain.fortress.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val BIOMETRIC_UNLOCK = "biometric_unlock"

    /** Post-auth root: hosts the bottom-tab scaffold (Home / Cards / Scan / Analytics / Profile). */
    const val MAIN = "main"

    /** Legacy direct alias — kept so links from tests / outside the nav graph still resolve. */
    const val DASHBOARD = "dashboard"

    const val BLOCKED = "blocked"
    const val ONBOARDING = "onboarding"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_DETAIL = "account_detail"

    /** Account-bound full-fat transfer with review + biometric signature. */
    const val TRANSFER = "transfer"

    /** Quick numeric-keypad amount entry reachable from the Send pill on the dashboard. */
    const val TRANSFER_QUICK = "transfer_quick"

    const val CARDS = "cards"
    const val SECURITY_CENTER = "security_center"
    const val DEV_MODE = "dev_mode"
}
