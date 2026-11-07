package io.github.tutu2652.climbingfinder.ui

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.tutu2652.climbingfinder.core.ClimbMedia
import io.github.tutu2652.climbingfinder.core.ClimbSession
import io.github.tutu2652.climbingfinder.core.MediaFile
import io.github.tutu2652.climbingfinder.core.MediaKind
import io.github.tutu2652.climbingfinder.core.linkedMediaURIs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

// Route photos and videos linked to climbs. Only a content:// link is saved in the
// logbook; the original stays in the phone's gallery. The app keeps read access to each
// linked item across restarts, and gives it back when you remove or replace the link.
// Deleting a climb keeps it, so the link still opens if the climb comes back from a backup.

object MediaLinks {
    private const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION

    /** Keeps read access to a picked item after the app restarts; false if Android didn't allow it. */
    fun keep(ctx: Context, uri: Uri): Boolean =
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, READ) }.isSuccess

    /** Gives back access to an item once no climb links it. */
    fun releaseIfUnused(ctx: Context, uri: String, sessions: List<ClimbSession>) {
        if (uri in linkedMediaURIs(sessions)) return
        runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(uri), READ) }
    }
}

private fun MediaKind.noun() = if (this == MediaKind.PHOTO) "photo" else "video"

/** A short reason a linked item can't be shown. */
fun mediaError(e: Throwable, kind: MediaKind): String = when (e) {
    is SecurityException -> "This app can't open this ${kind.noun()} any more. This happens after reinstalling the app, " +
        "or with a backup from another phone. Link it again to fix it."
    is FileNotFoundException -> "This ${kind.noun()} isn't on this phone. The original may have been deleted, or it was linked on another phone."
    else -> "This ${kind.noun()} can't be opened."
}

/** Throws FileNotFoundException or SecurityException if the item has gone or can't be read. */
fun checkReadable(ctx: Context, uri: Uri) {
    (ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())).close()
}

/**
 * A picked item's size, date taken and real file name, saved with its link so the same file
 * can be found again. The photo picker hides the name (it reports "1000000023.jpg"), so that
 * is left out.
 */
fun fileDetails(ctx: Context, uri: Uri): MediaFile? {
    fun <T> column(name: String, read: (android.database.Cursor) -> T?): T? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(name), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) read(c) else null }
    }.getOrNull()
    val size = column(OpenableColumns.SIZE) { it.getLong(0) }?.takeIf { it > 0 } ?: return null
    // The photo picker calls it date_taken_millis; gallery items and documents, datetaken.
    val taken = column("date_taken_millis") { it.getLong(0) } ?: column(MediaStore.MediaColumns.DATE_TAKEN) { it.getLong(0) }
    val name = column(OpenableColumns.DISPLAY_NAME) { it.getString(0) }?.takeIf { it.isNotBlank() && !pickerName.matches(it) }
    return MediaFile(size, taken?.takeIf { it > 0 }, name)
}

/** The stand-in name the photo picker gives: the item's number and an extension. */
private val pickerName = Regex("""^\d+(\.\w+)?$""")

/**
 * Whether the app may read the phone's photos and videos itself. It only asks when a link
 * restored from a backup can't be opened: Android forgets picked items when the app is
 * reinstalled, so the app then looks for the same file by name and size.
 */
object MediaAccess {
    enum class Level { FULL, PARTIAL, NONE }

    /** What to ask for, on this version of Android. */
    val permissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun level(ctx: Context, kind: MediaKind): Level {
        fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 -> when {
                has(if (kind == MediaKind.PHOTO) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_MEDIA_VIDEO) -> Level.FULL
                // Android 14+: only the photos and videos you chose.
                Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Level.PARTIAL
                else -> Level.NONE
            }
            has(Manifest.permission.READ_EXTERNAL_STORAGE) -> Level.FULL
            else -> Level.NONE
        }
    }

    /** Goes up when access may have changed, so links are looked up again. */
    var revision by mutableIntStateOf(0)
        private set

    fun changed() {
        found.clear()
        revision++
    }

    private val found = ConcurrentHashMap<String, Uri>()

    /**
     * What to read for a link: the link itself, or, if it can't be opened, the same file
     * found in the phone's gallery by size, date taken and name. If more than one file
     * could be it, none is used, so a link from another phone's backup never opens some
     * other photo.
     */
    fun readable(ctx: Context, uri: String, kind: MediaKind, file: MediaFile?): Uri {
        val u = Uri.parse(uri)
        val failure: Exception = try {
            checkReadable(ctx, u); return u
        } catch (e: SecurityException) {
            e
        } catch (e: FileNotFoundException) {
            e
        } catch (e: IllegalArgumentException) {
            FileNotFoundException(uri)
        }
        if (file == null) throw failure
        found[uri]?.let { f -> if (runCatching { checkReadable(ctx, f) }.isSuccess) return f }
        val level = level(ctx, kind)
        if (level == Level.NONE) throw failure
        find(ctx, kind, file, u.lastPathSegment)?.let { found[uri] = it; return it }
        // With only some items allowed, it may be one you haven't allowed.
        throw if (level == Level.PARTIAL) SecurityException("Not among the allowed items") else FileNotFoundException(uri)
    }

    private fun find(ctx: Context, kind: MediaKind, file: MediaFile, idHint: String?): Uri? = runCatching {
        val collection = if (kind == MediaKind.PHOTO) {
            if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        ctx.contentResolver.query(collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.SIZE} = ?", arrayOf(file.size.toString()), null)?.use { c ->
            val ids = generateSequence {
                if (!c.moveToNext()) null
                else Triple(c.getLong(0), if (c.isNull(1)) null else c.getLong(1), c.getString(2))
            }.filter { (_, taken, name) -> file.matches(file.size, taken, name) }.map { it.first }.toList()
            // On the phone that made the link, the item keeps its number; elsewhere, only a single match is trusted.
            val id = ids.firstOrNull { it.toString() == idHint } ?: ids.singleOrNull()
            id?.let { ContentUris.withAppendedId(collection, it) }
        }
    }.getOrNull()
}

/** Asks for photo and video access, or once refused, opens the app's settings. */
class AccessRequest(val label: String, val run: () -> Unit)

@Composable
fun rememberAccessRequest(level: MediaAccess.Level): AccessRequest {
    val ctx = LocalContext.current
    var refused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        // Android stops showing the prompt after you refuse it twice; then only Settings can change it.
        val activity = ctx as? Activity
        refused = granted.values.none { it } && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, MediaAccess.permissions[0])
        MediaAccess.changed()
    }
    return when {
        refused && level == MediaAccess.Level.NONE -> AccessRequest("Open settings to allow access") {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
        }
        level == MediaAccess.Level.PARTIAL -> AccessRequest("Allow more photos & videos") { launcher.launch(MediaAccess.permissions) }
        else -> AccessRequest("Allow access to photos & videos") { launcher.launch(MediaAccess.permissions) }
    }
}

/** The access level, checked again when it may have changed or the app comes back to the front. */
@Composable
fun rememberAccessLevel(kind: MediaKind): MediaAccess.Level {
    val ctx = LocalContext.current
    val resumed = rememberResumeCount()
    return remember(MediaAccess.revision, resumed, kind) { MediaAccess.level(ctx, kind) }
}

/** A photo decoded no smaller than `maxPx` on its longer side (not at full resolution), the right way up. */
fun decodePhoto(ctx: Context, uri: Uri, maxPx: Int): Bitmap {
    fun sample(w: Int, h: Int): Int {
        var n = 1
        while (max(w, h) / (n * 2) >= maxPx) n *= 2
        return n
    }
    if (Build.VERSION.SDK_INT >= 28) {
        // ImageDecoder turns photos upright from their EXIF orientation by itself.
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.setTargetSampleSize(sample(info.size.width, info.size.height))
        }
    }
    val cr = ctx.contentResolver
    fun open() = cr.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
    val opts = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }
    val bitmap = open().use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IOException("Not an image")
    val degrees = runCatching {
        open().use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
}

/**
 * Why a link won't open. NO_ACCESS: the item may still be on the phone, but this install
 * of the app can't read it (Android forgets picked items when the app is reinstalled, and a
 * link restored from another phone's backup was never picked here); picking it again fixes
 * it. MISSING: the original is gone, or can't be read.
 */
enum class MediaProblem {
    MISSING, NO_ACCESS;

    companion object {
        fun of(e: Throwable) = if (e is SecurityException) NO_ACCESS else MISSING
    }
}

/** A thumbnail, and a video's length. */
class Thumb(val bitmap: Bitmap, val durationMs: Long?)

/**
 * Thumbnails for the gallery and the climb screen, loaded off the main thread and kept
 * in memory. Items found missing are remembered so the gallery can say so.
 */
object MediaThumbnails {
    const val GRID_PX = 360

    private val cache = object : LruCache<String, Thumb>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Thumb) = maxOf(1, value.bitmap.byteCount)
    }

    /** Links that can't be opened, by URI, and why. */
    val problems = mutableStateMapOf<String, MediaProblem>()

    fun cached(uri: String, kind: MediaKind, size: Int): Thumb? = cache.get("$size/${kind.name}/$uri")

    suspend fun load(ctx: Context, uri: String, kind: MediaKind, file: MediaFile?, size: Int): Thumb = withContext(Dispatchers.IO) {
        val key = "$size/${kind.name}/$uri"
        try {
            val u = MediaAccess.readable(ctx, uri, kind, file)
            cache.get(key)?.let { problems.remove(uri); return@withContext it }
            val bitmap = if (kind == MediaKind.PHOTO && size > GRID_PX) decodePhoto(ctx, u, size) else thumbnail(ctx, u, kind, size)
            val duration = if (kind == MediaKind.VIDEO) videoDuration(ctx, u) else null
            Thumb(bitmap, duration).also { cache.put(key, it); problems.remove(uri) }
        } catch (e: Exception) {
            if (e !is CancellationException) { cache.remove(key); problems[uri] = MediaProblem.of(e) }
            throw e
        }
    }

    /** Checks the items still open, so the gallery can say some are missing before you scroll to them. */
    suspend fun check(ctx: Context, items: List<ClimbMedia>) = withContext(Dispatchers.IO) {
        for (item in items.distinctBy { it.uri }) {
            val failure = runCatching { MediaAccess.readable(ctx, item.uri, item.kind, item.file) }.exceptionOrNull()
            if (failure == null) problems.remove(item.uri) else problems[item.uri] = MediaProblem.of(failure)
        }
    }

    private fun thumbnail(ctx: Context, uri: Uri, kind: MediaKind, size: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return ctx.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } catch (e: FileNotFoundException) {
                throw e
            } catch (e: SecurityException) {
                throw e
            } catch (_: Exception) {
                // Some apps' items have no thumbnail; decode one below instead.
            }
        }
        if (kind == MediaKind.PHOTO) return decodePhoto(ctx, uri, size)
        checkReadable(ctx, uri)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size)
            } else {
                r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { f ->
                    val scale = size.toFloat() / max(f.width, f.height)
                    if (scale >= 1f) f else Bitmap.createScaledBitmap(f, (f.width * scale).toInt().coerceAtLeast(1), (f.height * scale).toInt().coerceAtLeast(1), true)
                }
            }
            return frame ?: throw IOException("No video frame")
        } catch (e: SecurityException) {
            throw e
        } catch (e: RuntimeException) {
            throw IOException("Unreadable video", e)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun videoDuration(ctx: Context, uri: Uri): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}

/** "0:42", "12:05" or "1:02:05". */
fun videoLength(ms: Long): String {
    val s = (ms + 500) / 1000
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

sealed interface MediaState {
    data object Loading : MediaState
    class Ready(val thumb: Thumb) : MediaState
    class Failed(val message: String, val problem: MediaProblem = MediaProblem.MISSING) : MediaState
}

/** Counts each time the app comes back to the front, so links are checked again (an original may have been deleted meanwhile). */
@Composable
fun rememberResumeCount(): Int {
    var n by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { n++ }
    return n
}

@Composable
fun rememberThumbnail(uri: String, kind: MediaKind, file: MediaFile?, size: Int = MediaThumbnails.GRID_PX, reload: Int = 0): State<MediaState> {
    val ctx = LocalContext.current.applicationContext
    val resumed = rememberResumeCount()
    return produceState(MediaThumbnails.cached(uri, kind, size)?.let { MediaState.Ready(it) } ?: MediaState.Loading,
        uri, kind, file, size, reload, resumed, MediaAccess.revision) {
        value = try {
            MediaState.Ready(MediaThumbnails.load(ctx, uri, kind, file, size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediaState.Failed(mediaError(e, kind), MediaProblem.of(e))
        } catch (e: OutOfMemoryError) {
            MediaState.Failed("This ${kind.noun()} is too big to show.")
        }
    }
}

/** A small "▶ 0:42" badge for a video thumbnail. */
@Composable
fun VideoBadge(durationMs: Long?, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(5.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
        if (durationMs != null) {
            Text(videoLength(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * A climb's route photo or video: a preview, Full screen, Link / Replace link and
 * Remove link. A missing original shows why, and can be replaced or removed.
 */
@Composable
fun ClimbMediaSection(kind: MediaKind, uri: String?, file: MediaFile?, message: String?, onView: () -> Unit, onChoose: () -> Unit, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val previewPx = ctx.resources.displayMetrics.widthPixels.coerceIn(MediaThumbnails.GRID_PX, 1440)
    Column {
        Text(kind.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = secondaryText,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 6.dp))
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                var first = true
                fun divider(): Boolean = !first.also { first = false }
                if (uri != null) {
                    val state by rememberThumbnail(uri, kind, file, previewPx, reload)
                    when (val s = state) {
                        MediaState.Loading -> RowItem(divider = divider()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Loading…", color = secondaryText)
                            }
                        }
                        is MediaState.Ready -> {
                            divider()
                            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).clickable(onClick = onView), contentAlignment = Alignment.Center) {
                                Image(s.thumb.bitmap.asImageBitmap(),
                                    if (kind == MediaKind.PHOTO) "View route photo full screen" else "Play climb video full screen",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(8.dp)))
                                if (kind == MediaKind.VIDEO) {
                                    Box(Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
                                    }
                                    VideoBadge(s.thumb.durationMs, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                                }
                            }
                            ActionRow(Icons.Filled.Fullscreen, "Full screen", divider = true, chevron = false, onClick = onView)
                        }
                        is MediaState.Failed -> {
                            // A link from before a reinstall, or another phone, can be found again with access to the gallery.
                            val level = rememberAccessLevel(kind)
                            val findable = s.problem == MediaProblem.NO_ACCESS && file != null && level != MediaAccess.Level.FULL
                            val text = when {
                                findable && level == MediaAccess.Level.PARTIAL ->
                                    "This ${kind.noun()} isn't in the photos and videos you've allowed this app to see. Allow more, or link it again."
                                findable -> "This ${kind.noun()} was linked before the app was reinstalled, or on another phone. " +
                                    "Allow access to your photos and videos so the app can find it again, or link it again."
                                else -> s.message
                            }
                            RowItem(divider = divider()) { Text(text, style = MaterialTheme.typography.bodyMedium, color = secondaryText) }
                            if (findable) {
                                val request = rememberAccessRequest(level)
                                ActionRow(Icons.Filled.Lock, request.label, chevron = false, onClick = request.run)
                            } else {
                                ActionRow(Icons.Filled.Refresh, "Try again", chevron = false) { reload++ }
                            }
                        }
                    }
                }
                if (message != null) RowItem(divider = divider()) { Text(message, style = MaterialTheme.typography.bodyMedium, color = secondaryText) }
                ActionRow(if (kind == MediaKind.PHOTO) Icons.Filled.AddPhotoAlternate else Icons.Filled.VideoLibrary,
                    if (uri == null) "Link from gallery" else "Replace link", divider = divider(), chevron = false, onClick = onChoose)
                if (uri != null) ActionRow(Icons.Filled.LinkOff, "Remove link", tint = Warn, chevron = false, onClick = onRemove)
            }
        }
    }
}

/** A photo or video to show full screen, and what its Replace / Remove buttons do if it can't be opened. */
class ViewerRequest(val kind: MediaKind, val uri: String, val file: MediaFile?, val onReplace: () -> Unit, val onRemove: () -> Unit)

/**
 * The full-screen viewer is drawn by MainActivity over the whole app, tabs included. (A
 * Compose Dialog's window stops below the status bar but keeps the screen's height, so
 * its bottom, with the video controls, ran off the screen.)
 */
class MediaViewerHost {
    var shown by mutableStateOf<ViewerRequest?>(null)
}

val LocalMediaViewer = staticCompositionLocalOf { MediaViewerHost() }

/** The photo or video full screen: a photo decoded at screen size and zoomable; a video in a VideoView with play controls. */
@Composable
fun MediaViewer(request: ViewerRequest, onClose: () -> Unit) {
    val kind = request.kind
    val uri = request.uri
    BackHandler(onBack = onClose)
    // Light status bar icons on the black background while it's open.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose { if (wasLight != null) bars.isAppearanceLightStatusBars = wasLight }
    }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    // Swallow taps so nothing underneath reacts.
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        val e = error
        if (e != null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(e, color = Color.White, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = request.onReplace) { Text("Replace link", color = Color.White) }
                    OutlinedButton(onClick = request.onRemove) { Text("Remove link", color = Warn) }
                }
            }
        } else if (kind == MediaKind.PHOTO) {
            ZoomablePhoto(uri, request.file) { error = it }
        } else {
            VideoPlayer(uri, request.file) { error = it }
        }
        Row(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(kind.title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ZoomablePhoto(uri: String, file: MediaFile?, onError: (String) -> Unit) {
    val ctx = LocalContext.current
    val screenPx = ctx.resources.displayMetrics.let { max(it.widthPixels, it.heightPixels) }
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = try {
            withContext(Dispatchers.IO) { decodePhoto(ctx, MediaAccess.readable(ctx, uri, MediaKind.PHOTO, file), screenPx) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(mediaError(e, MediaKind.PHOTO)); null
        } catch (e: OutOfMemoryError) {
            onError("This photo is too big to show."); null
        }
    }
    val b = bitmap
    if (b == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }
    val image = remember(b) { b.asImageBitmap() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(o: Offset, s: Float): Offset {
        val mx = box.width * (s - 1) / 2
        val my = box.height * (s - 1) / 2
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = clamp(offset + pan * scale, scale)
    }
    Image(image, "Route photo. Pinch to zoom, or double-tap to zoom in and out.", contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .transformable(state)
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y))
}

@Composable
private fun VideoPlayer(uri: String, file: MediaFile?, onError: (String) -> Unit) {
    val ctx = LocalContext.current
    // Find what to play first: VideoView only reports a generic error for a missing file.
    var failed by remember(uri) { mutableStateOf(false) }
    val readable by produceState<Uri?>(null, uri) {
        value = try {
            withContext(Dispatchers.IO) { MediaAccess.readable(ctx, uri, MediaKind.VIDEO, file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true; onError(mediaError(e, MediaKind.VIDEO)); null
        }
    }
    val playable = readable
    if (playable == null) {
        if (!failed) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }
    var video by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var length by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    // Keep the time and seek bar up to date while it plays.
    LaunchedEffect(video, playing) {
        val v = video ?: return@LaunchedEffect
        while (playing) {
            if (seeking == null) position = v.currentPosition
            delay(250)
        }
        position = v.currentPosition
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                FrameLayout(c).apply {
                    val v = VideoView(c)
                    addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                    v.setOnPreparedListener { length = v.duration; v.start(); playing = true }
                    v.setOnCompletionListener { playing = false; controls = true; position = length }
                    v.setOnErrorListener { _, _, _ ->
                        onError("This video can't be played. The original may have been deleted or moved.")
                        true
                    }
                    v.setVideoURI(playable)
                    video = v
                }
            },
        )
        // Tap anywhere to show or hide the controls.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { controls = !controls } })
        if (controls) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f))
                    .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    val v = video ?: return@IconButton
                    if (v.isPlaying) { v.pause(); playing = false } else { v.start(); playing = true }
                }) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White)
                }
                Text(videoLength(position.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = seeking ?: position.toFloat(),
                    onValueChange = { seeking = it },
                    onValueChangeFinished = { seeking?.let { video?.seekTo(it.toInt()); position = it.toInt() }; seeking = null },
                    valueRange = 0f..maxOf(1, length).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(videoLength(length.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    DisposableEffect(uri) { onDispose { video?.stopPlayback() } }
}

/** The footer under a climb's photo and video. */
const val MEDIA_FOOTER = "Only links are saved here. Originals stay in your phone's gallery. If you delete an original, " +
    "its link stops working. Backups keep the links. After reinstalling the app, it can find them again if you allow access to your photos and videos."
