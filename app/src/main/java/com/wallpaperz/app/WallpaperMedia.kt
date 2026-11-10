package com.wallpaperz.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object WallpaperMedia {
    fun file(context: Context, id: String) = File(context.filesDir, "$id.image")
    fun video(context: Context, id: String) = File(context.filesDir, "$id.video")

    fun import(context: Context, uri: Uri): WallpaperItem {
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "$id.import")
        val cover = file(context, id)
        val video = video(context, id)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { input.copyTo(it) } }
                ?: throw IOException("无法读取所选素材")
            val mime = context.contentResolver.getType(uri).orEmpty()
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "素材"
            if (mime.startsWith("video/")) {
                source.copyTo(video)
                val duration = validateVideo(video)
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(video.path)
                    val poster = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1080, 1920)
                        ?: throw IOException("无法读取视频封面")
                    try { cover.outputStream().use { poster.compress(Bitmap.CompressFormat.JPEG, 92, it) } }
                    finally { poster.recycle() }
                }
                return WallpaperItem(id, name, MediaKind.VIDEO, duration, importedAt = System.currentTimeMillis())
            }
            source.copyTo(cover)
            decode(cover, 64).recycle()
            return WallpaperItem(id, name, importedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            cover.delete(); video.delete()
            throw e
        } finally { source.delete() }
    }

    fun validateVideo(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val format = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: throw IOException("未找到可播放的视频片段")
            require(MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null) {
                "当前设备不支持这个视频的编码"
            }
            return format.getLong(MediaFormat.KEY_DURATION) / 1000
        } finally { extractor.release() }
    }

    fun validate(context: Context, item: WallpaperItem) {
        decode(file(context, item.id), 64).recycle()
        if (item.kind != MediaKind.PHOTO) validateVideo(video(context, item.id))
    }

    fun decode(file: File, maxSide: Int): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        require(!info.isAnimated) { "请将动画导出为视频后导入" }
        val scale = min(1f, maxSide.toFloat() / max(info.size.width, info.size.height))
        decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    fun delete(context: Context, id: String) { file(context, id).delete(); video(context, id).delete() }
}
