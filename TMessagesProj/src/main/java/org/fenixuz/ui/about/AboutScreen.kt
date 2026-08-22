package org.fenixuz.ui.about

import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.fenixuz.utils.LanguageCode
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper

/**
 * Novagram "About" — a single clean, Telegram-native page describing the app and the firm behind it.
 * Grey settings backdrop with a seamless toolbar (same look as [org.fenixuz.ui.analytics_screen.AnalyticsScreen]),
 * then three white rounded cards:
 *   1. Hero — big app logo, name, and two side-by-side version chips (our version + the Telegram base it runs on).
 *   2. Support — a direct DM to the support admin and a one-tap Google Play review link.
 *   3. Company — the legal entity (VipAds LLC), founding year, country, and its web/email contacts.
 *
 * Everything here is static device-local content: the only dynamic value is the app version, read from the
 * PackageManager at runtime so it always matches the installed build. The Telegram base version is a hand-kept
 * constant — bump [TELEGRAM_BASE] whenever we actually merge a newer upstream.
 */
class AboutScreen : BaseFragment() {

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LanguageCode.getMyTitles(355))          // About
        actionBar.setCastShadows(false)
        // Dissolve the toolbar into the grey page — no visible header bar, matching the Analytics screen.
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false)
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false)
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) finishFragment()
            }
        })

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        }

        // Content column capped to a comfortable width and centred, so on tablets / landscape / a wide
        // split-screen the cards don't stretch edge-to-edge. Recomputed on every measure → adapts to
        // rotation and multi-window without rebuilding the view.
        val column = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val avail = MeasureSpec.getSize(widthMeasureSpec)
                val max = dp(560)
                val side = if (avail > max) (avail - max) / 2 + dp(12) else dp(12)
                if (paddingLeft != side || paddingRight != side) {
                    setPadding(side, dp(12), side, dp(16))
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(16))
        }

        column.addView(heroCard(context))
        column.addView(supportCard(context), spacedCard())
        column.addView(companyCard(context), spacedCard())

        scroll.addView(column, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP))
        fragmentView = scroll
        return scroll
    }

    // ---- Hero card -------------------------------------------------------------------------------------------

    private fun heroCard(context: Context): View {
        val card = card(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(22), dp(16), dp(20))
        }

        // App logo — the launcher icon, softly rounded so it reads as a product mark, not an app-drawer icon.
        val logo = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(22).toFloat())
                }
            }
            clipToOutline = true
        }
        card.addView(logo, LinearLayout.LayoutParams(dp(96), dp(96)))

        card.addView(TextView(context).apply {
            text = "Novagram"
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            typeface = AndroidUtilities.bold()
            textSize = 22f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        card.addView(TextView(context).apply {
            text = LanguageCode.getMyTitles(366)                   // Secure messenger
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })

        // Two version chips side by side: what we ship, and the Telegram base it is built on.
        val chips = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        chips.addView(chip(context, "Novagram", appVersion(context)), chipParams(true))
        chips.addView(chip(context, "Telegram", TELEGRAM_BASE), chipParams(false))
        card.addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })

        return card
    }

    /** A rounded pill with a quiet brand label above a bold value. */
    private fun chip(context: Context, label: String, value: String): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Theme.getColor(Theme.key_windowBackgroundGray))
            }
        }
        box.addView(TextView(context).apply {
            text = label
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        box.addView(TextView(context).apply {
            text = value
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
            typeface = AndroidUtilities.bold()
            textSize = 15f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        })
        return box
    }

    private fun chipParams(first: Boolean) =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            // marginStart/End (not left/right) so the gap mirrors correctly under RTL locales.
            if (first) marginEnd = dp(5) else marginStart = dp(5)
        }

    // ---- Support card ----------------------------------------------------------------------------------------

    private fun supportCard(context: Context): View {
        val card = card(context)
        card.addView(header(context, LanguageCode.getMyTitles(356)))     // Support
        card.addView(
            row(
                context, R.drawable.msg_contacts, 0xFF2CBE8C.toInt(),
                LanguageCode.getMyTitles(357),                            // Contact support
                LanguageCode.getMyTitles(358)                             // Message our admin directly
            ) { Browser.openUrl(context, "https://t.me/$SUPPORT_ADMIN") }
        )
        card.addView(divider(context))
        // The app knows which build it is (RuStore vs Play), so route the review to the right store — a Play
        // link is useless to a RuStore user who can't reach Play, and vice-versa.
        val isRustore = context.packageName.startsWith(RUSTORE_PACKAGE)
        card.addView(
            row(
                context, R.drawable.msg_openin, 0xFF1BA4ED.toInt(),
                LanguageCode.getMyTitles(359),                            // Rate the app
                LanguageCode.getMyTitles(if (isRustore) 369 else 360)     // Leave a review on RuStore / Google Play
            ) { openReview(context) }
        )
        return card
    }

    // ---- Company card ----------------------------------------------------------------------------------------

    private fun companyCard(context: Context): View {
        val card = card(context)
        card.addView(header(context, LanguageCode.getMyTitles(361)))     // Company

        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(12))
        }
        block.addView(TextView(context).apply {
            text = COMPANY_NAME
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            typeface = AndroidUtilities.bold()
            textSize = 16f
        })
        block.addView(TextView(context).apply {
            text = LanguageCode.getMyTitles(367) + " · " + LanguageCode.getMyTitles(368)  // Founded 2022 · Republic of Uzbekistan
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
            textSize = 13f
            setPadding(0, dp(2), 0, 0)
        })
        block.addView(TextView(context).apply {
            text = LanguageCode.getMyTitles(364)                          // legal blurb
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
            textSize = 14f
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(10), 0, 0)
        })
        card.addView(block)

        // Each row now leads with a plain-language label (what it is) and shows the address underneath, so a
        // user knows what they'll open before tapping — not a bare "novagram.org" whose meaning is unclear.
        card.addView(divider(context))
        card.addView(row(context, R.drawable.msg_link, 0xFF1BA4ED.toInt(),
                LanguageCode.getMyTitles(370), "novagram.org") {          // Novagram website
            Browser.openUrl(context, "https://novagram.org")
        })
        card.addView(divider(context))
        card.addView(row(context, R.drawable.msg_link, 0xFFC46EF4.toInt(),
                LanguageCode.getMyTitles(371), "vipads.uz") {             // Company website
            Browser.openUrl(context, "https://vipads.uz")
        })
        card.addView(divider(context))
        card.addView(row(context, R.drawable.msg_message, 0xFFF28B31.toInt(),
                LanguageCode.getMyTitles(363), COMPANY_EMAIL) {           // Email
            openEmail(context)
        })
        return card
    }

    // ---- Shared building blocks ------------------------------------------------------------------------------

    /** A white rounded card container. */
    private fun card(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        // Clip children to the card's rounded shape so a row's rectangular ripple can't bleed past the
        // corners on the top/bottom rows — the corner-overflow users reported.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(18).toFloat())
            }
        }
        clipToOutline = true
    }

    private fun spacedCard() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        }

    private fun header(context: Context, title: String) = TextView(context).apply {
        text = title
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader))
        typeface = AndroidUtilities.bold()
        textSize = 15f
        setPadding(dp(16), dp(14), dp(16), dp(8))
    }

    /**
     * One tappable settings-style row: a small rounded colored icon, a title with an optional subtitle,
     * and a trailing chevron. Uses the platform ripple so it feels native under the finger.
     */
    private fun row(
        context: Context,
        iconRes: Int,
        iconColor: Int,
        title: String,
        subtitle: String?,
        onClick: () -> Unit
    ): View {
        val rowView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(14), dp(12))
            val tv = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            setBackgroundResource(tv.resourceId)
            isClickable = true
            setOnClickListener { onClick() }
        }

        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            colorFilter = android.graphics.PorterDuffColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(9).toFloat()
                setColor(iconColor)
            }
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        rowView.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(14) })

        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = title
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            textSize = 16f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        if (!subtitle.isNullOrEmpty()) {
            texts.addView(TextView(context).apply {
                text = subtitle
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                textSize = 13f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(1), 0, 0)
            })
        }
        rowView.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        })

        rowView.addView(ImageView(context).apply {
            setImageResource(R.drawable.msg_arrowright)
            colorFilter = android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), android.graphics.PorterDuff.Mode.SRC_IN
            )
            alpha = 0.6f
            // The chevron art points right; mirror it under RTL so it points the natural way.
            if (LocaleController.isRTL) scaleX = -1f
        }, LinearLayout.LayoutParams(dp(18), dp(18)))

        return rowView
    }

    /** A faint hair-line inset from the left so it lines up under the row text, Telegram-style. */
    private fun divider(context: Context) = View(context).apply {
        setBackgroundColor(Theme.getColor(Theme.key_divider))
    }.also { }.let { v ->
        val holder = LinearLayout(context)
        holder.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))).apply {
            marginStart = dp(62)   // inset under the row text; marginStart so it mirrors under RTL
        })
        holder
    }

    // ---- Actions ---------------------------------------------------------------------------------------------

    /** Route the review to the store this build actually ships on. */
    private fun openReview(context: Context) {
        if (context.packageName.startsWith(RUSTORE_PACKAGE)) openRuStoreReview(context)
        else openPlayReview(context)
    }

    private fun openPlayReview(context: Context) {
        // Use the fixed published package, NOT context.packageName — a debug build carries a .beta/.web
        // suffix that would point market:// at a listing that does not exist.
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_PACKAGE"))
                .setPackage("com.android.vending")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            Browser.openUrl(context, "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE")
        }
    }

    private fun openRuStoreReview(context: Context) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("rustore://apps.rustore.ru/app/$RUSTORE_PACKAGE"))
                .setPackage("ru.vk.store")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            // RuStore app absent → open the web listing (RuStore intercepts it if present, else the browser).
            Browser.openUrl(context, "https://apps.rustore.ru/app/$RUSTORE_PACKAGE")
        }
    }

    private fun openEmail(context: Context) {
        try {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$COMPANY_EMAIL"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            AndroidUtilities.addToClipboard(COMPANY_EMAIL)
        }
    }

    private fun appVersion(context: Context): String {
        return try {
            // Only the version name is user-facing here; the build/version code is intentionally omitted.
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun hasForceLightStatusBar(): Boolean = false

    companion object {
        /** Telegram upstream this fork is built on. Bump only when we actually merge a newer Telegram base. */
        private const val TELEGRAM_BASE = "12.9.0"
        private const val SUPPORT_ADMIN = "Avazbekedu"
        private const val COMPANY_NAME = "VipAds LLC"
        private const val COMPANY_EMAIL = "admin@novagram.org"
        /** Published store package IDs — fixed, so the review link never breaks on a suffixed debug build. */
        private const val PLAY_PACKAGE = "uz.codingtech.messengerapp"
        private const val RUSTORE_PACKAGE = "org.messenger_pro.messenger"
    }
}

private fun dp(value: Int): Int = AndroidUtilities.dp(value.toFloat())
