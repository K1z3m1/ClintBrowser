package com.jhaiian.clint.browser.imagereader

import android.content.Context
import com.jhaiian.clint.R
import com.jhaiian.clint.util.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class NavTarget(val href: String?, val clickPath: String?, val text: String)

data class ReaderNav(val next: NavTarget?, val prev: NavTarget?, val home: NavTarget?)

data class PageExtract(val title: String, val url: String, val images: List<String>, val nav: ReaderNav, val fallbacks: List<String> = emptyList())

data class ChapterListItem(val title: String, val url: String)

data class ImageReaderLaunch(val first: PageExtract, val userAgent: String, val profileId: String)

object ImageReaderParser {

    fun decodeJsResult(raw: String?): JSONObject? {
        if (raw.isNullOrEmpty() || raw == "null") return null
        return try {
            (JSONTokener(raw).nextValue() as? String)?.let { JSONObject(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun parseExtract(o: JSONObject): PageExtract {
        val arr = o.optJSONArray("images")
        val images = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val u = arr.optString(i)
                if (u.isNotEmpty()) images.add(u)
            }
        }
        val fbArr = o.optJSONArray("fallbacks")
        val fallbacks = ArrayList<String>()
        for (i in 0 until images.size) fallbacks.add(fbArr?.optString(i).orEmpty())
        val nav = o.optJSONObject("nav")
        return PageExtract(
            title = o.optString("title"),
            url = o.optString("url"),
            images = images,
            fallbacks = fallbacks,
            nav = ReaderNav(
                next = target(nav?.optJSONObject("next")),
                prev = target(nav?.optJSONObject("prev")),
                home = target(nav?.optJSONObject("home"))
            )
        )
    }

    fun parseChapters(o: JSONObject): List<ChapterListItem> {
        val arr = o.optJSONArray("chapters") ?: return emptyList()
        val out = ArrayList<ChapterListItem>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val url = item.optString("h")
            if (url.isNotEmpty()) out.add(ChapterListItem(item.optString("t"), url))
        }
        return out
    }

    private fun target(o: JSONObject?): NavTarget? {
        if (o == null) return null
        val href = o.optString("href").ifEmpty { null }
        val click = o.optString("clickPath").ifEmpty { null }
        if (href == null && click == null) return null
        return NavTarget(href, click, o.optString("text"))
    }
}

object ImageReaderJson {

    fun chapter(id: Int, mode: String, ex: PageExtract): JSONObject =
        JSONObject()
            .put("id", id)
            .put("mode", mode)
            .put("title", ex.title)
            .put("url", ex.url)
            .put("images", JSONArray(ex.images))
            .put("fallbacks", JSONArray(ex.fallbacks))
            .put("hasNext", ex.nav.next != null)
            .put("hasPrev", ex.nav.prev != null)
            .put("hasHome", ex.nav.home != null)

    fun forScript(json: JSONObject): String =
        json.toString()
            .replace("</", "<\\/")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
}

object ImageReaderShell {

    const val PREF_AUTO_NEXT = "image_reader_auto_next"

    fun build(context: Context, first: PageExtract, isDark: Boolean): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val labels = JSONObject()
            .put("prev", context.getString(R.string.image_reader_prev))
            .put("next", context.getString(R.string.image_reader_next))
            .put("chapters", context.getString(R.string.image_reader_chapters))
            .put("loading", context.getString(R.string.image_reader_loading))
            .put("noNext", context.getString(R.string.image_reader_no_next))
            .put("noPrev", context.getString(R.string.image_reader_no_prev))
            .put("noChapters", context.getString(R.string.image_reader_no_chapters))
            .put("openInBrowser", context.getString(R.string.image_reader_open_in_browser))
            .put("autoNext", context.getString(R.string.image_reader_auto_next))
        val init = JSONObject()
            .put("autoNext", prefs.getBoolean(PREF_AUTO_NEXT, true))
            .put("labels", labels)
            .put("chapter", ImageReaderJson.chapter(1, "replace", first))
        val template = context.assets.open("JavaScript/image_reader_shell.html").bufferedReader().use { it.readText() }
        return template
            .replace("%%BG%%", if (isDark) "#000000" else "#f2f2f2")
            .replace("%%FG%%", if (isDark) "#cfcfcf" else "#222222")
            .replace("%%PANEL%%", if (isDark) "#1c1c1e" else "#ffffff")
            .replace("%%ACCENT%%", if (isDark) "#4f8ef7" else "#1a73e8")
            .replace("%%PH%%", if (isDark) "#141414" else "#e0e0e0")
            .replace("%%INIT%%", ImageReaderJson.forScript(init))
    }
}
