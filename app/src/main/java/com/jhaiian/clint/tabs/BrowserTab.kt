package com.jhaiian.clint.tabs

import android.webkit.WebView
import com.jhaiian.clint.profiles.WebProfiles
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "",
    val isIncognito: Boolean = false,
    val isRefreshLinkTab: Boolean = false,
    val openerTabId: String? = null,
    val shortcutId: String? = null,
    val previousTabId: String? = null,
    var pendingUrl: String? = null,
    val profileId: String = WebProfiles.DEFAULT_ID,
    var groupId: String? = null,
    val webView: WebView
) {
    var lastActiveAt: Long = System.currentTimeMillis()

    /** WebView state (history + scroll) saved when the page was unloaded by Low RAM mode. */
    var savedState: android.os.Bundle? = null

    init {
        WebProfiles.registerTab(id, profileId)
    }
}
