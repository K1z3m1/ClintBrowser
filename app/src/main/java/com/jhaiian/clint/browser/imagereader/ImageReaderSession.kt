package com.jhaiian.clint.browser.imagereader

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.profiles.WebProfiles
import com.jhaiian.clint.util.PreferenceManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

internal class ImageReaderSession(
    private val activity: MainActivity,
    private val reader: WebView,
    start: ImageReaderLaunch,
    private val visibleCallback: (String, String) -> Unit,
    private val onOpenInTab: (String) -> Unit
) {

    companion object {
        const val JS_NAME = "ClintReader"
    }

    private class ChapterRecord(val id: Int, var ex: PageExtract)

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val records = LinkedHashMap<Int, ChapterRecord>()
    private val shown = ArrayList<Int>()
    private var nextId = 2
    private var busy = false
    private var destroyed = false
    private var loader: WebView? = null
    private var loadWaiter: CompletableDeferred<Boolean>? = null
    private var cachedList: Pair<String, List<ChapterListItem>>? = null
    private val userAgent = start.userAgent
    private val profileId = start.profileId
    private val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    private val extractJs: String by lazy { readAsset("JavaScript/image_reader_mode.js") }
    private val chaptersJs: String by lazy { readAsset("JavaScript/image_reader_chapters.js") }

    val bridge: Bridge = Bridge()

    init {
        records[1] = ChapterRecord(1, start.first)
        shown.add(1)
    }

    inner class Bridge {
        @JavascriptInterface
        fun go(direction: String, chapterId: Int, append: Boolean) {
            main.launch { handleGo(direction, chapterId, append) }
        }

        @JavascriptInterface
        fun openChapter(url: String) {
            main.launch { handleOpenChapter(url) }
        }

        @JavascriptInterface
        fun home(chapterId: Int) {
            main.launch { handleHome(chapterId) }
        }

        @JavascriptInterface
        fun openInTab(url: String) {
            main.launch { if (!destroyed) onOpenInTab(url) }
        }

        @JavascriptInterface
        fun visible(chapterId: Int, title: String) {
            main.launch {
                val rec = records[chapterId] ?: return@launch
                if (!destroyed) visibleCallback(title, rec.ex.url)
            }
        }

        @JavascriptInterface
        fun setAutoNext(enabled: Boolean) {
            prefs.edit().putBoolean(ImageReaderShell.PREF_AUTO_NEXT, enabled).apply()
        }
    }

    fun destroy() {
        destroyed = true
        main.cancel()
        loadWaiter?.cancel()
        loader?.let {
            it.stopLoading()
            it.destroy()
        }
        loader = null
    }

    private fun readAsset(path: String): String =
        activity.assets.open(path).bufferedReader().use { it.readText() }

    private fun dispatch(call: String, json: JSONObject) {
        if (destroyed) return
        val quoted = JSONObject.quote(json.toString())
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
        reader.evaluateJavascript("window.ClintReaderUI&&window.ClintReaderUI.$call(JSON.parse($quoted))", null)
    }

    private fun sendError(kind: String, id: Int) {
        val message = when (kind) {
            "noNext" -> activity.getString(R.string.image_reader_no_next)
            "noPrev" -> activity.getString(R.string.image_reader_no_prev)
            "noHome" -> activity.getString(R.string.image_reader_no_chapters)
            "failed" -> activity.getString(R.string.image_reader_load_failed)
            else -> ""
        }
        dispatch("onError", JSONObject().put("kind", kind).put("id", id).put("message", message))
    }

    private fun sendList(items: List<ChapterListItem>, homeUrl: String) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("t", it.title).put("h", it.url)) }
        dispatch("onChapterList", JSONObject().put("chapters", arr).put("homeUrl", homeUrl))
    }

    private fun register(ex: PageExtract): ChapterRecord {
        val rec = ChapterRecord(nextId++, ex)
        records[rec.id] = rec
        return rec
    }

    private fun isShownDuplicate(ex: PageExtract): Boolean {
        val first = ex.images.firstOrNull() ?: return true
        return shown.any { records[it]?.ex?.images?.firstOrNull() == first }
    }

    private suspend fun handleGo(direction: String, chapterId: Int, append: Boolean) {
        val ch = records[chapterId] ?: return
        val isNext = direction == "next"
        val target = if (isNext) ch.ex.nav.next else ch.ex.nav.prev
        if (target == null) {
            sendError(if (isNext) "noNext" else "noPrev", chapterId)
            return
        }
        if (busy) {
            sendError("busy", chapterId)
            return
        }
        busy = true
        try {
            val ex = navigate(ch, target)
            if (destroyed) return
            if (ex == null || ex.images.isEmpty()) {
                sendError("failed", chapterId)
                return
            }
            if (isShownDuplicate(ex)) {
                val nav = ch.ex.nav
                ch.ex = ch.ex.copy(nav = if (isNext) nav.copy(next = null) else nav.copy(prev = null))
                sendError(if (isNext) "noNext" else "noPrev", chapterId)
                return
            }
            showChapter(register(ex), append)
        } finally {
            busy = false
        }
    }

    private suspend fun handleOpenChapter(url: String) {
        if (busy) {
            sendError("busy", 0)
            return
        }
        busy = true
        try {
            val ex = navigate(null, NavTarget(url, null, ""))
            if (destroyed) return
            if (ex == null || ex.images.isEmpty()) {
                sendError("failed", 0)
                return
            }
            if (isShownDuplicate(ex)) {
                sendError("same", 0)
                return
            }
            showChapter(register(ex), false)
        } finally {
            busy = false
        }
    }

    private fun showChapter(rec: ChapterRecord, append: Boolean) {
        if (append) {
            shown.add(rec.id)
        } else {
            shown.clear()
            shown.add(rec.id)
            records.keys.retainAll(shown.toSet())
        }
        dispatch("onChapter", ImageReaderJson.chapter(rec.id, if (append) "append" else "replace", rec.ex))
    }

    private suspend fun handleHome(chapterId: Int) {
        val ch = records[chapterId] ?: return
        val home = ch.ex.nav.home?.href
        if (home == null) {
            sendError("noHome", chapterId)
            return
        }
        val cached = cachedList
        if (cached != null && cached.first == home) {
            sendList(cached.second, home)
            return
        }
        if (busy) {
            sendError("busy", chapterId)
            return
        }
        busy = true
        try {
            loadAndWait(home)
            var items: List<ChapterListItem> = emptyList()
            var last = -1
            var stable = 0
            for (i in 0 until 12) {
                if (destroyed) return
                delay(700)
                val list = ImageReaderParser.decodeJsResult(evalLoader(chaptersJs))
                    ?.let { ImageReaderParser.parseChapters(it) }
                    .orEmpty()
                if (list.size >= 2) {
                    items = list
                    if (list.size == last) {
                        stable++
                    } else {
                        stable = 0
                        last = list.size
                    }
                    if (stable >= 2) break
                }
            }
            if (items.isNotEmpty()) cachedList = home to items
            sendList(items, home)
        } finally {
            busy = false
        }
    }

    private suspend fun navigate(from: ChapterRecord?, target: NavTarget): PageExtract? {
        val wv = ensureLoader()
        val href = target.href
        if (href != null) {
            loadAndWait(href)
            return extractStable(null, 16)
        }
        val path = target.clickPath ?: return null
        if (from != null && !sameUrl(wv.url, from.ex.url)) {
            loadAndWait(from.ex.url)
            extractStable(null, 8)
        }
        val before = from?.ex?.images?.firstOrNull()
        if (!clickTarget(path)) return null
        return extractStable(before, 22)
    }

    private fun sameUrl(a: String?, b: String?): Boolean {
        fun n(u: String?) = (u ?: "").substringBefore('#').trimEnd('/')
        return n(a) == n(b)
    }

    private suspend fun clickTarget(path: String): Boolean {
        val quoted = JSONObject.quote(path)
        val raw = evalLoader(
            "(function(){try{var e=document.querySelector($quoted);if(!e)return 'no';e.scrollIntoView({block:'center'});e.click();return 'ok';}catch(x){return 'err';}})()"
        )
        return raw?.contains("ok") == true
    }

    private suspend fun extractOnce(): PageExtract? =
        ImageReaderParser.decodeJsResult(evalLoader(extractJs))?.let { ImageReaderParser.parseExtract(it) }

    private suspend fun extractStable(excludeFirst: String?, maxIter: Int): PageExtract? {
        var last = -1
        var stable = 0
        var best: PageExtract? = null
        for (i in 0 until maxIter) {
            if (destroyed) return null
            evalLoader("window.scrollTo(0,Math.min(document.body?document.body.scrollHeight:0,window.scrollY+window.innerHeight*1.5))")
            delay(700)
            val ex = extractOnce() ?: continue
            if (ex.images.isEmpty()) continue
            if (excludeFirst != null && ex.images.first() == excludeFirst) continue
            best = ex
            if (ex.images.size == last) {
                stable++
                if (stable >= 2) return ex
            } else {
                stable = 0
                last = ex.images.size
            }
        }
        return best
    }

    private suspend fun loadAndWait(url: String) {
        val wv = ensureLoader()
        val waiter = CompletableDeferred<Boolean>()
        loadWaiter = waiter
        wv.loadUrl(url)
        withTimeoutOrNull(25_000L) { waiter.await() }
    }

    private suspend fun evalLoader(script: String): String? = suspendCancellableCoroutine { cont ->
        val wv = loader
        if (wv == null || destroyed) {
            cont.resume(null)
        } else {
            wv.evaluateJavascript(script) { result -> if (cont.isActive) cont.resume(result) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureLoader(): WebView {
        loader?.let { return it }
        val wv = WebView(activity)
        WebProfiles.bind(wv, profileId)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            if (userAgent.isNotEmpty()) userAgentString = userAgent
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                loadWaiter?.complete(true)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) loadWaiter?.complete(false)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty()
                return !(scheme == "http" || scheme == "https")
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                result.cancel()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                result.cancel()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
                result.cancel()
                return true
            }
        }
        val metrics = activity.resources.displayMetrics
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY)
        )
        wv.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        wv.onResume()
        wv.resumeTimers()
        loader = wv
        return wv
    }
}
