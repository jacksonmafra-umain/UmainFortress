package com.umain.fortress.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Fortress icon registry — the single source of truth for every glyph the app renders.
 *
 * The reference set is the
 * [Free UI Icons — Open Source Vector Icon Set](https://www.figma.com/community/file/1063138616574654762/free-ui-icons-open-source-vector-icon-set-svg)
 * from the Streamline / Lucide lineage: 24×24 viewport, 1.5 px stroke, rounded caps and
 * joins, optical sizing. We mirror the visual language using the **outlined** Material
 * Symbols family as a fallback — close enough in stroke weight and geometry that the app
 * reads as Lucide-styled without shipping a second SVG bundle.
 *
 * To swap in the exact Streamline / Lucide glyphs, replace each property body with an
 * [ImageVector] built from the SVG path data; no caller has to change.
 *
 * Caller rule: never reach for `Icons.Default.*` directly — always go through this object.
 */
object FortressIcons {
    /** Back chevron (auto-mirrored for RTL). */
    val Back: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowBack
    /** Sign-out glyph. */
    val Logout: ImageVector get() = Icons.AutoMirrored.Outlined.Logout
    /** Outlined notification bell. */
    val Notifications: ImageVector get() = Icons.Outlined.NotificationsNone
    /** Cogwheel settings glyph. */
    val Settings: ImageVector get() = Icons.Outlined.Settings
    /** Horizontal overflow dots. */
    val More: ImageVector get() = Icons.Outlined.MoreHoriz

    /** Bottom-nav Home tab. */
    val Home: ImageVector get() = Icons.Outlined.Home
    /** Bottom-nav Cards tab. */
    val Cards: ImageVector get() = Icons.Outlined.Wallet
    /** Bottom-nav Scan tab (QR scanner). */
    val Scan: ImageVector get() = Icons.Outlined.QrCodeScanner
    /** Bottom-nav Analytics tab. */
    val Analytics: ImageVector get() = Icons.Outlined.PieChart
    /** Bottom-nav Profile tab. */
    val Profile: ImageVector get() = Icons.Outlined.AccountCircle

    /** Send / outbound — arrow-up-right semantic. */
    val Send: ImageVector get() = Icons.Outlined.TrendingUp
    /** Receive / inbound — arrow-down-left semantic. */
    val Receive: ImageVector get() = Icons.Outlined.SwapHoriz
    /** "More / grid / all accounts" affordance. */
    val Grid: ImageVector get() = Icons.Outlined.GridView

    /** Savings account icon. */
    val Savings: ImageVector get() = Icons.Outlined.Savings
    /** Investment account icon. */
    val Investment: ImageVector get() = Icons.Outlined.ShowChart
    /** Credit / debit card icon. */
    val Card: ImageVector get() = Icons.Outlined.CreditCard
    /** Insights / report icon. */
    val Insights: ImageVector get() = Icons.Outlined.Insights
    /** Document icon used for statements and exports. */
    val Document: ImageVector get() = Icons.Outlined.Description

    /** Trusted-verdict shield. */
    val ShieldGood: ImageVector get() = Icons.Outlined.GppGood
    /** Limited-verdict shield with question mark. */
    val ShieldMaybe: ImageVector get() = Icons.Outlined.GppMaybe
    /** Untrusted-verdict shield with cross. */
    val ShieldBad: ImageVector get() = Icons.Outlined.GppBad
    /** Generic verified-by-shield mark for onboarding. */
    val ShieldVerified: ImageVector get() = Icons.Outlined.VerifiedUser
    /** Fingerprint glyph for biometric flows. */
    val Fingerprint: ImageVector get() = Icons.Outlined.Fingerprint
    /** Padlock glyph for privacy / lock screens. */
    val Lock: ImageVector get() = Icons.Outlined.Lock
    /** Lightning bolt — Dev Mode entry. */
    val Bolt: ImageVector get() = Icons.Outlined.Bolt

    /** Eye glyph for "show balance". */
    val Eye: ImageVector get() = Icons.Outlined.Visibility
    /** Eye-slashed glyph for "hide balance". */
    val EyeOff: ImageVector get() = Icons.Outlined.VisibilityOff
    /** Outlined check-in-circle used by the transfer-success screen. */
    val Check: ImageVector get() = Icons.Outlined.CheckCircle
}
