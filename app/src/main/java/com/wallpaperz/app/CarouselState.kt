package com.wallpaperz.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.random.Random

@Serializable enum class PlayMode(val title: String) { ORDERED("顺序"), SHUFFLE("随机"), FAVOR_NEW("偏爱新图") }
@Serializable enum class Trigger(val title: String) {
    INTERVAL("定时"), SCREEN_ON("每次亮屏"), UNLOCK("每次解锁")
}
@Serializable enum class MediaKind(val title: String) { PHOTO("照片"), VIDEO("视频") }
@Serializable enum class VideoTiming(val title: String) { FINISH("播完切换"), INTERVAL("跟随间隔") }
@Serializable data class WallpaperItem(val id: String, val name: String, val kind: MediaKind = MediaKind.PHOTO,
                                     val durationMs: Long = 0, val importedAt: Long = 0)
@Serializable data class WallpaperGroup(
    val id: String = "default",
    val name: String = "默认主题",
    val photos: List<WallpaperItem> = emptyList(),
    val mode: PlayMode = PlayMode.ORDERED,
    val trigger: Trigger = Trigger.UNLOCK,
    val intervalSeconds: Int = 60,
    val videoTiming: VideoTiming = VideoTiming.FINISH,
    val currentId: String? = null,
    val lastChangedAt: Long = 0
) {
    val switchesOnVideoEnd get() = trigger == Trigger.INTERVAL && videoTiming == VideoTiming.FINISH
}

@Serializable data class CarouselState(
    val groups: List<WallpaperGroup> = listOf(WallpaperGroup()),
    val activeGroupId: String = "default",
    val enabledGroupIds: List<String> = listOf("default"),
    val playingGroupId: String? = null,
    val roundPlayedIds: List<String> = emptyList(),
    val automatic: Boolean = false,
    val lastError: String? = null
) {
    val group get() = groups.first { it.id == activeGroupId }
    val playbackGroup get() = groups.firstOrNull { it.id == playingGroupId } ?: groups.first()
    val enabledGroups get() = enabledGroupIds.mapNotNull { id -> groups.firstOrNull { it.id == id && it.photos.isNotEmpty() } }
    val photos get() = group.photos
    val mode get() = group.mode
    val trigger get() = group.trigger
    val intervalSeconds get() = group.intervalSeconds
    val videoTiming get() = group.videoTiming
    val currentId get() = group.currentId
    val lastChangedAt get() = group.lastChangedAt
    fun updateGroup(id: String = activeGroupId, transform: (WallpaperGroup) -> WallpaperGroup) =
        copy(groups = groups.map { if (it.id == id) transform(it) else it })
}

/** Upgrade the saved flat library into one default theme; runtime code uses only groups. */
internal fun decodeCarouselState(source: String): CarouselState {
    val data = Json.decodeFromString<JsonObject>(source)
    val grouped = if (data.keys.any { it in setOf("groups", "activeGroupId", "enabledGroupIds", "playingGroupId", "roundPlayedIds") }) data else buildJsonObject {
        put("groups", buildJsonArray {
            add(JsonObject(data.filterKeys { it in setOf("photos", "mode", "trigger", "intervalMinutes", "currentId", "remaining", "lastChangedAt") }))
        })
        data["automatic"]?.let { put("automatic", it) }
        data["lastError"]?.let { put("lastError", it) }
        if ((data["photos"] as? JsonArray)?.isNotEmpty() == true) put("playingGroupId", "default")
    }
    // Convert persisted settings, removing the former per-theme random bags.
    val groups = grouped["groups"]?.jsonArray?.map { element ->
        val group = element.jsonObject
        val seconds = group["intervalSeconds"]?.jsonPrimitive?.int
            ?: group["intervalMinutes"]?.jsonPrimitive?.int?.times(60) ?: 60
        JsonObject((group - "intervalMinutes" - "remaining") + ("intervalSeconds" to JsonPrimitive(if (seconds == 3) 60 else seconds)))
    }
    val migrated = JsonObject(grouped + buildMap {
        if (groups != null) put("groups", JsonArray(groups))
        if ("roundPlayedIds" !in grouped) {
            val playingId = grouped["playingGroupId"]?.jsonPrimitive?.content
            val current = (groups?.firstOrNull { it["id"]?.jsonPrimitive?.content == playingId }
                ?: groups?.firstOrNull())?.get("currentId")
            put("roundPlayedIds", JsonArray(listOfNotNull(current?.takeUnless { it is JsonNull })))
        }
    })
    return Json.decodeFromJsonElement<CarouselState>(migrated)
}

object RotationLogic {
    data class Selection(val groupId: String, val id: String, val playedIds: List<String>)

    /** One round covers every item in every checked theme, regardless of theme size. */
    fun next(state: CarouselState, random: Random = Random.Default, now: Long = System.currentTimeMillis()): Selection {
        val themes = state.enabledGroups
        val allIds = themes.flatMap { it.photos }.map { it.id }
        check(allIds.isNotEmpty()) { "请先勾选有素材的主题" }
        val unseen = allIds.filterNot { it in state.roundPlayedIds }
        val newRound = unseen.isEmpty()
        val previous = state.playbackGroup.currentId
        val available = if (newRound) allIds.filter { allIds.size == 1 || it != previous } else unseen
        val start = themes.indexOfFirst { it.id == state.playingGroupId }
        val target = (1..themes.size).map { themes[(start + it) % themes.size] }
            .first { theme -> theme.photos.any { it.id in available } }
        val candidates = target.photos.filter { it.id in available }
        val selected = when (target.mode) {
            PlayMode.ORDERED -> {
                val current = target.photos.indexOfFirst { it.id == target.currentId }
                (1..target.photos.size).map { target.photos[(current + it) % target.photos.size] }
                    .first { it.id in available }
            }
            PlayMode.SHUFFLE -> candidates.random(random)
            PlayMode.FAVOR_NEW -> {
                val weights = candidates.map { newnessWeight(it, now) }
                var draw = random.nextDouble() * weights.sum()
                candidates.indices.firstOrNull { draw -= weights[it]; draw < 0 }
                    .let { candidates[it ?: candidates.lastIndex] }
            }
        }
        val played = if (newRound) emptyList() else state.roundPlayedIds
        return Selection(target.id, selected.id, (played + selected.id).distinct())
    }

    fun select(state: CarouselState, id: String): Selection {
        val theme = state.groups.firstOrNull { group -> group.photos.any { it.id == id } } ?: error("这张壁纸已删除")
        return Selection(theme.id, id, (state.roundPlayedIds + id).distinct())
    }

    // Weight affects this round's order only; selection is always without replacement.
    fun newnessWeight(item: WallpaperItem, now: Long): Double = if (item.importedAt == 0L) 1.0 else
        1.0 + 3.0 * (1.0 - (now - item.importedAt).coerceAtLeast(0).toDouble() / (7 * 86_400_000L)).coerceIn(0.0, 1.0)
}

val INTERVALS = listOf(60, 300, 900, 1800, 3600, 10800, 21600, 43200, 86400)
fun intervalLabel(seconds: Int) = if (seconds < 3600) "${seconds / 60} 分钟" else "${seconds / 3600} 小时"

data class DesktopPlayback(val itemId: String? = null, val generation: Long = 0,
                           val visible: Boolean = false, val ready: Boolean = false, val ended: Boolean = false)
