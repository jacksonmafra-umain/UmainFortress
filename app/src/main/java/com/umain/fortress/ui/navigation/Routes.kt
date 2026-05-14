package com.umain.fortress.ui.navigation

/**
 * Static route table for the Fortress navigation graph.
 *
 * Every destination wired in [FortressNavGraph] reads its path from here. Routes that
 * carry arguments use the bare base path (e.g. [TRANSFER]) and the navigation graph
 * appends the path-argument template (`"/{accountId}"`).
 */
object Routes {
    /** Bootstrap destination — runs the device-integrity probe and forks downstream. */
    const val SPLASH = "splash"

    /** Email + password login screen. */
    const val LOGIN = "login"

    /** Biometric-unlock challenge screen. */
    const val BIOMETRIC_UNLOCK = "biometric_unlock"

    /** Post-auth root hosting the five-tab scaffold (Home / Cards / Scan / Analytics / Profile). */
    const val MAIN = "main"

    /** Legacy direct alias for the dashboard; kept so links from tests still resolve. */
    const val DASHBOARD = "dashboard"

    /** Hard-stop screen shown when device-integrity verdict is Untrusted. */
    const val BLOCKED = "blocked"

    /** First-run onboarding pager. */
    const val ONBOARDING = "onboarding"

    /** Accounts list, reachable via the "grid" quick action. */
    const val ACCOUNTS = "accounts"

    /** Account detail; takes an `accountId` path argument. */
    const val ACCOUNT_DETAIL = "account_detail"

    /** Account-bound full-fat transfer with review + biometric signature. Takes `accountId`. */
    const val TRANSFER = "transfer"

    /** Quick numeric-keypad amount entry reachable from the Send pill on the dashboard. */
    const val TRANSFER_QUICK = "transfer_quick"

    /** Cards list with reveal flow. */
    const val CARDS = "cards"

    /** Add-card form: cardholder name, brand, last 4, expiry. POSTs `/me/cards`. */
    const val ADD_CARD = "add_card"

    /** Security-center screen with key-rotation, passkey enrolment, audit log. */
    const val SECURITY_CENTER = "security_center"

    /** Debug-build-only attack-simulation panel. */
    const val DEV_MODE = "dev_mode"
}
