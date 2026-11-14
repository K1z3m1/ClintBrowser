package com.jhaiian.clint.downloads

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.jhaiian.clint.util.PreferenceManager
import com.jhaiian.clint.BuildConfig
import com.jhaiian.clint.settings.downloads.DownloadSettingsKeys
import java.io.File

internal object DownloadFileHelper {


    fun isSafCustomMode(context: Context, item: DownloadItem? = null): Boolean {
        if (item != null) return item.locationMode == DownloadSettingsKeys.MODE_CUSTOM
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getString(
            DownloadSettingsKeys.PREF_DOWNLOAD_LOCATION_MODE,
            DownloadSettingsKeys.MODE_DEFAULT
        )
        return mode == DownloadSettingsKeys.MODE_CUSTOM
    }

    fun getSafTreeUri(context: Context, item: DownloadItem? = null): Uri? {
        if (item != null && item.locationMode == DownloadSettingsKeys.MODE_CUSTOM) {
            val uriStr = item.customLocationUri ?: return null
            return Uri.parse(uriStr)
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uriStr = prefs.getString(
            DownloadSettingsKeys.PREF_DOWNLOAD_CUSTOM_URI,
            null
        ) ?: return null
        return Uri.parse(uriStr)
    }

    fun tempDownloadDir(context: Context?): File {
        return context?.getExternalFilesDir("clint_downloads_temp")
            ?: File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                ".clint_temp"
            )
    }

    fun isInTempDir(context: Context, file: File?): Boolean {
        if (file == null) return false
        return file.absolutePath.startsWith(tempDownloadDir(context).absolutePath + File.separator)
    }

    fun hasAllFilesAccess(): Boolean =
        !BuildConfig.IS_FOSS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()

    private fun hasLegacyStoragePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    fun needsLegacyStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && !hasLegacyStoragePermission(context)

    fun canWriteSharedStorageDirectly(context: Context): Boolean = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> hasLegacyStoragePermission(context)
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
            hasLegacyStoragePermission(context) && Environment.isExternalStorageLegacy()
        else -> hasAllFilesAccess()
    }

    fun safTreeUriToFile(uri: Uri): File? {
        val docId = uri.lastPathSegment ?: return null
        val separator = docId.indexOf(':')
        if (separator < 0) return null
        val volume = docId.substring(0, separator)
        val relativePath = docId.substring(separator + 1)
        val root = if (volume == "primary") Environment.getExternalStorageDirectory() else File("/storage/$volume")
        return File(root, relativePath)
    }

    fun isCustomLocationAccessible(context: Context, locationMode: String, customLocationUri: String?): Boolean {
        if (locationMode != DownloadSettingsKeys.MODE_CUSTOM) return true
        val uriStr = customLocationUri ?: return false
        return try {
            val treeUri = Uri.parse(uriStr)
            val hasGrant = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isWritePermission
            }
            if (!hasGrant) return false
            val docDir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            docDir.exists() && docDir.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    fun resolveDirectCustomDir(context: Context, item: DownloadItem? = null): File? {
        if (!canWriteSharedStorageDirectly(context) || !isSafCustomMode(context, item)) return null
        val treeUri = getSafTreeUri(context, item) ?: return null
        return safTreeUriToFile(treeUri)
    }

    fun moveFileDirect(source: File, target: File): Boolean =
        runCatching { source.renameTo(target) }.getOrDefault(false)

    fun resolveDownloadDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    internal fun sanitizeFileName(name: String): String {
        var cleaned = name.substringAfterLast('/').substringAfterLast('\\').trim()
        cleaned = cleaned.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
        if (cleaned.isBlank() || cleaned.all { it == '.' }) cleaned = "download"
        if (cleaned.length > 200) {
            val dot = cleaned.lastIndexOf('.')
            val ext = if (dot > 0 && cleaned.length - dot <= 16) cleaned.substring(dot) else ""
            cleaned = cleaned.substring(0, 200 - ext.length) + ext
        }
        return cleaned
    }

    fun uniqueFile(dir: File, name: String): File {
        val safeName = sanitizeFileName(name)
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""
        var candidate = File(dir, safeName)
        var i = 1
        while (i <= 1000) {
            try {
                if (candidate.createNewFile()) return candidate
            } catch (_: java.io.IOException) {
            }
            candidate = File(dir, "$base($i)$ext")
            i++
        }
        return File(dir, "$base(${System.nanoTime()})$ext")
    }

    const val PART_SUFFIX = ".part"

    fun isPartFile(file: File): Boolean = file.name.endsWith(PART_SUFFIX)

    fun finalNameOf(file: File): String = file.name.removeSuffix(PART_SUFFIX)

    fun uniquePartFile(dir: File, name: String): File {
        val safeName = sanitizeFileName(name)
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""
        var i = 0
        while (i <= 1000) {
            val candidate = if (i == 0) safeName else "$base($i)$ext"
            if (!File(dir, candidate).exists()) {
                val part = File(dir, candidate + PART_SUFFIX)
                try {
                    if (part.createNewFile()) return part
                } catch (_: java.io.IOException) {
                }
            }
            i++
        }
        return File(dir, "$base(${System.nanoTime()})$ext$PART_SUFFIX")
    }

    fun promotePartFile(part: File): File? {
        if (!isPartFile(part)) return part
        val dir = part.parentFile ?: return null
        val name = finalNameOf(part)
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 0
        while (i <= 1000) {
            val target = File(dir, if (i == 0) name else "$base($i)$ext")
            if (!target.exists()) return if (part.renameTo(target)) target else null
            i++
        }
        return null
    }

    fun previewUniqueName(dir: File, name: String): String {
        val safeName = sanitizeFileName(name)
        if (!File(dir, safeName).exists()) return safeName
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""
        var i = 1
        while (i <= 1000) {
            val candidate = "$base($i)$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
        return "$base(${System.nanoTime()})$ext"
    }

    fun uniqueSafName(docDir: DocumentFile, name: String): String {
        val safeName = sanitizeFileName(name)
        if (docDir.findFile(safeName) == null) return safeName
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""
        var i = 1
        var candidate = "$base($i)$ext"
        while (docDir.findFile(candidate) != null) { i++; candidate = "$base($i)$ext" }
        return candidate
    }

    fun extractFilenameFromContentDisposition(cd: String): String? {
        if (cd.isBlank()) return null
        val fnStar = Regex("""filename\*\s*=\s*(?:[^';]*'[^';]*')?\s*([^;\r\n]+)""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()?.trim('"')?.let(::percentDecodeUtf8)
        if (!fnStar.isNullOrBlank()) return fnStar
        val quoted = Regex("""filename\s*=\s*"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.replace(Regex("""\\(.)"""), "$1")?.trim()
        if (!quoted.isNullOrBlank()) return quoted
        val fn = Regex("""filename\s*=\s*([^;\r\n]+)""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()?.trim('"', '\'')?.trim()
        return fn?.ifBlank { null }
    }

    private fun percentDecodeUtf8(value: String): String {
        val out = java.io.ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val hi = if (c == '%' && i + 2 < value.length) Character.digit(value[i + 1], 16) else -1
            val lo = if (hi >= 0) Character.digit(value[i + 2], 16) else -1
            if (hi >= 0 && lo >= 0) {
                out.write(hi * 16 + lo)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    fun detectExtFromMagicBytes(b: ByteArray): String? {
        if (b.size < 4) return null

        fun at(i: Int) = b.getOrElse(i) { 0 }.toInt() and 0xFF

        if (b.size >= 8
            && at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70) {
            if (b.size >= 12) {
                val brand = String(b, 8, 4, Charsets.ISO_8859_1)
                return when {
                    brand.startsWith("M4A") || brand.startsWith("m4a") -> "m4a"
                    brand.startsWith("M4V") || brand.startsWith("m4v") -> "m4v"
                    brand.startsWith("qt") || brand.startsWith("QT") -> "mov"
                    brand.startsWith("3gp") || brand.startsWith("3GP") -> "3gp"
                    brand.startsWith("3g2") || brand.startsWith("3G2") -> "3g2"
                    brand.startsWith("f4v") || brand.startsWith("F4V") -> "f4v"
                    else -> "mp4"
                }
            }
            return "mp4"
        }

        if (at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3) {
            val header = String(b, 0, minOf(b.size, 64), Charsets.ISO_8859_1)
            return if (header.contains("webm", ignoreCase = true)) "webm" else "mkv"
        }

        if (at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 && b.size >= 12) {
            return when {
                at(8) == 0x41 && at(9) == 0x56 && at(10) == 0x49 -> "avi"
                at(8) == 0x57 && at(9) == 0x41 && at(10) == 0x56 && at(11) == 0x45 -> "wav"
                at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "webp"
                else -> null
            }
        }

        if (at(0) == 0x46 && at(1) == 0x4C && at(2) == 0x56) return "flv"
        if (at(0) == 0x47 && b.size >= 189 && at(188) == 0x47) return "ts"

        if (at(0) == 0x00 && at(1) == 0x00 && at(2) == 0x01) {
            return when (at(3)) {
                0xBA -> "mpg"
                0xB3 -> "mpg"
                else -> null
            }
        }

        if (at(0) == 0x4F && at(1) == 0x67 && at(2) == 0x67 && at(3) == 0x53) return "ogg"
        if (at(0) == 0x49 && at(1) == 0x44 && at(2) == 0x33) return "mp3"

        if (at(0) == 0xFF && (at(1) and 0xE0) == 0xE0) {
            val layer = (at(1) shr 1) and 0x03
            if (layer == 1) return "mp3"
        }

        if (at(0) == 0xFF && (at(1) == 0xF1 || at(1) == 0xF9)) return "aac"
        if (at(0) == 0x66 && at(1) == 0x4C && at(2) == 0x61 && at(3) == 0x43) return "flac"
        if (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return "jpg"
        if (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) return "png"
        if (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46) return "gif"
        if (at(0) == 0x42 && at(1) == 0x4D) return "bmp"
        if (at(0) == 0x25 && at(1) == 0x50 && at(2) == 0x44 && at(3) == 0x46) return "pdf"

        if (at(0) == 0x50 && at(1) == 0x4B && at(2) == 0x03 && at(3) == 0x04) return zipSubtype(b)

        if (at(0) == 0x52 && at(1) == 0x61 && at(2) == 0x72 && at(3) == 0x21) return "rar"
        if (at(0) == 0x37 && at(1) == 0x7A && at(2) == 0xBC && at(3) == 0xAF) return "7z"
        if (at(0) == 0x1F && at(1) == 0x8B) return "gz"

        if (at(0) == 0x42 && at(1) == 0x5A && at(2) == 0x68) return "bz2"
        if (at(0) == 0xFD && at(1) == 0x37 && at(2) == 0x7A && at(3) == 0x58 && at(4) == 0x5A) return "xz"
        if (at(0) == 0x53 && at(1) == 0x51 && at(2) == 0x4C && at(3) == 0x69) return "sqlite"
        if (at(0) == 0x7F && at(1) == 0x45 && at(2) == 0x4C && at(3) == 0x46) return "elf"
        if (at(0) == 0x4D && at(1) == 0x5A) return "exe"
        if (at(0) == 0x4D && at(1) == 0x54 && at(2) == 0x68 && at(3) == 0x64) return "mid"
        if (at(0) == 0x38 && at(1) == 0x42 && at(2) == 0x50 && at(3) == 0x53) return "psd"
        if (at(0) == 0x7B && at(1) == 0x5C && at(2) == 0x72 && at(3) == 0x74 && at(4) == 0x66) return "rtf"
        if (at(0) == 0xD0 && at(1) == 0xCF && at(2) == 0x11 && at(3) == 0xE0) return "doc"
        if (at(0) == 0x30 && at(1) == 0x26 && at(2) == 0xB2 && at(3) == 0x75) return "wmv"
        if (at(0) == 0x77 && at(1) == 0x4F && at(2) == 0x46 && at(3) == 0x46) return "woff"
        if (at(0) == 0x77 && at(1) == 0x4F && at(2) == 0x46 && at(3) == 0x32) return "woff2"
        if ((at(0) == 0x49 && at(1) == 0x49 && at(2) == 0x2A && at(3) == 0x00) ||
            (at(0) == 0x4D && at(1) == 0x4D && at(2) == 0x00 && at(3) == 0x2A)) return "tif"
        if (at(0) == 0x00 && at(1) == 0x00 && at(2) == 0x01 && at(3) == 0x00) return "ico"
        if (b.size >= 262 && String(b, 257, 5, Charsets.ISO_8859_1) == "ustar") return "tar"
        return null
    }
    private fun zipSubtype(b: ByteArray): String {
        val text = String(b, 0, minOf(b.size, 4096), Charsets.ISO_8859_1)
        return when {
            text.contains("AndroidManifest.xml") -> "apk"
            text.contains("word/") -> "docx"
            text.contains("xl/") -> "xlsx"
            text.contains("ppt/") -> "pptx"
            text.contains("application/epub+zip") -> "epub"
            text.contains("application/vnd.oasis.opendocument.text") -> "odt"
            text.contains("application/vnd.oasis.opendocument.spreadsheet") -> "ods"
            text.contains("application/vnd.oasis.opendocument.presentation") -> "odp"
            text.contains("META-INF/MANIFEST.MF") -> "jar"
            else -> "zip"
        }
    }
}
