package com.wallpaperz.app

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class RotationController(private val app: WallpaperZApp) {
    enum class Result { APPLIED, FAILED, IGNORED }
    private val repository get() = app.repository
    private val mutex = Mutex()
    private val _switching = MutableStateFlow(false)
    val switching = _switching.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    // Applying a wallpaper and committing its cursor must finish together, even if a timer is rescheduled.
    suspend fun next(expectedTrigger: Trigger? = null, selectedId: String? = null,
                     enableAutomatic: Boolean = false, expectedGeneration: Long? = null): Result = withContext(Dispatchers.IO + NonCancellable) {
        if (!mutex.tryLock()) return@withContext Result.IGNORED
        _switching.value = true
        try {
            val state = repository.read()
            if (expectedTrigger != null && (!state.automatic || state.playbackGroup.trigger != expectedTrigger)) {
                return@withContext Result.IGNORED
            }
            if (expectedGeneration != null && state.playbackGroup.lastChangedAt != expectedGeneration) return@withContext Result.IGNORED
            if (enableAutomatic && state.automatic) return@withContext Result.IGNORED
            val selection = if (selectedId == null) RotationLogic.next(state) else RotationLogic.select(state, selectedId)
            val target = state.groups.first { it.id == selection.groupId }
            check(LiveWallpaperService.isActive(app)) { "请先启用壁纸z动态壁纸" }
            WallpaperMedia.validate(app, target.photos.first { it.id == selection.id })
            repository.update { state -> state.updateGroup(target.id) { it.copy(currentId = selection.id,
                lastChangedAt = System.currentTimeMillis()) }.copy(lastError = null,
                playingGroupId = target.id, automatic = state.automatic || enableAutomatic, roundPlayedIds = selection.playedIds) }
            if (expectedTrigger == null) delay(LiveWallpaperService.FADE_MS)
            NextWidget.updateAll(app)
            if (enableAutomatic) {
                try { restoreService() } catch (e: Exception) {
                    repository.update { it.copy(automatic = false) }
                    throw e
                }
            }
            Result.APPLIED
        } catch (e: Exception) {
            report(e.message ?: "壁纸切换失败，请重试")
            Result.FAILED
        } finally {
            _switching.value = false
            mutex.unlock()
        }
    }

    suspend fun importPhotos(uris: List<Uri>) = withContext(Dispatchers.IO + NonCancellable) {
        if (uris.isEmpty() || !_importing.compareAndSet(false, true)) return@withContext
        val imported = mutableListOf<WallpaperItem>()
        var failed = 0
        var reason = ""
        try {
            for (uri in uris) {
                try { imported += WallpaperMedia.import(app, uri) }
                catch (e: Exception) { failed++; reason = e.message ?: "无法读取素材" }
            }
            mutex.withLock {
                try {
                    repository.update { state -> state.updateGroup { it.copy(photos = it.photos + imported) }
                        .copy(lastError = if (failed > 0) "$failed 个素材导入失败：$reason" else null) }
                } catch (e: Exception) {
                    imported.forEach { WallpaperMedia.delete(app, it.id) }
                    throw e
                }
            }
            NextWidget.updateAll(app)
            _messages.emit("已导入 ${imported.size} 个素材" + if (failed > 0) "，$failed 个失败：$reason" else "")
        } catch (e: Exception) { report(e.message ?: "导入失败，请检查剩余空间") }
        finally { _importing.value = false }
    }

    suspend fun delete(ids: Set<String>) = mutate { state ->
        val photos = state.photos.filterNot { photo -> photo.id in ids }
        val next = state.updateGroup { it.copy(photos = photos, currentId = it.currentId.takeUnless { current -> current in ids }) }
        next.copy(automatic = state.automatic && next.enabledGroups.isNotEmpty(), lastError = null)
    }.also { withContext(Dispatchers.IO) { ids.forEach { WallpaperMedia.delete(app, it) } } }

    suspend fun reorder(ids: List<String>) = mutate { state ->
        check(ids.size == state.photos.size && ids.toSet() == state.photos.map { it.id }.toSet()) { "素材列表已更新，请重试排序" }
        val photos = state.photos.associateBy { it.id }
        state.updateGroup { it.copy(photos = ids.map { photos.getValue(it) }) }
    }

    suspend fun mode(mode: PlayMode) = mutate { state -> state.updateGroup { it.copy(mode = mode) } }
    suspend fun trigger(trigger: Trigger) = mutate { state -> state.updateGroup { it.copy(trigger = trigger) } }
    suspend fun interval(seconds: Int) = mutate {
        require(seconds in INTERVALS)
        it.updateGroup { group -> group.copy(intervalSeconds = seconds) }
    }

    suspend fun createGroup(name: String) = mutate { state ->
        val label = themeName(state, name)
        val group = WallpaperGroup(id = java.util.UUID.randomUUID().toString(), name = label)
        state.copy(groups = state.groups + group, activeGroupId = group.id, enabledGroupIds = state.enabledGroupIds + group.id, lastError = null)
    }

    suspend fun renameGroup(id: String, name: String) = mutate { state ->
        val label = themeName(state, name, id)
        state.copy(groups = state.groups.map { if (it.id == id) it.copy(name = label) else it })
    }

    private fun themeName(state: CarouselState, name: String, except: String? = null): String {
        val label = name.trim()
        require(label.isNotEmpty()) { "请输入主题名称" }
        require(state.groups.none { it.id != except && it.name == label }) { "主题名称已存在" }
        return label
    }

    suspend fun selectGroup(id: String) = mutate { state ->
        require(state.groups.any { it.id == id }) { "主题已删除" }
        state.copy(activeGroupId = id, lastError = null)
    }

    suspend fun enableGroup(id: String, enabled: Boolean) = mutate { state ->
        val next = state.copy(enabledGroupIds = if (enabled) (state.enabledGroupIds + id).distinct() else state.enabledGroupIds - id)
        next.copy(automatic = state.automatic && next.enabledGroups.isNotEmpty())
    }

    suspend fun deleteGroup(id: String) {
        var removed = emptyList<WallpaperItem>()
        mutate { state ->
            require(state.groups.size > 1) { "至少保留一个主题" }
            removed = state.groups.first { it.id == id }.photos
            val groups = state.groups.filterNot { it.id == id }
            val next = state.copy(groups = groups, activeGroupId = if (id == state.activeGroupId) groups.first().id else state.activeGroupId,
                enabledGroupIds = state.enabledGroupIds - id, playingGroupId = state.playingGroupId.takeUnless { it == id }, lastError = null)
            next.copy(automatic = next.automatic && next.enabledGroups.isNotEmpty())
        }
        withContext(Dispatchers.IO) { removed.forEach { WallpaperMedia.delete(app, it.id) } }
    }

    suspend fun moveToGroup(ids: Set<String>, targetId: String) = mutate { state ->
        require(targetId != state.activeGroupId && state.groups.any { it.id == targetId }) { "请选择另一个主题" }
        val moved = state.photos.filter { it.id in ids }
        val movingDisplayed = state.playingGroupId == state.activeGroupId && state.currentId in ids
        val next = state.copy(groups = state.groups.map { group -> when (group.id) {
            state.activeGroupId -> group.copy(photos = group.photos.filterNot { it.id in ids }, currentId = group.currentId.takeUnless { it in ids })
            targetId -> group.copy(photos = group.photos + moved,
                currentId = if (movingDisplayed) state.currentId else group.currentId,
                lastChangedAt = if (movingDisplayed) state.lastChangedAt else group.lastChangedAt)
            else -> group
        } }, playingGroupId = if (movingDisplayed) targetId else state.playingGroupId, lastError = null)
        next.copy(automatic = state.automatic && next.enabledGroups.isNotEmpty())
    }

    suspend fun clearError() = mutate { it.copy(lastError = null) }
    suspend fun message(text: String) { _messages.emit(text) }
    suspend fun pause() {
        mutate { it.copy(automatic = false) }
        app.stopService(Intent(app, RotationService::class.java))
    }

    private suspend fun mutate(transform: (CarouselState) -> CarouselState) = withContext(Dispatchers.IO) {
        mutex.withLock { repository.update(transform) }
        NextWidget.updateAll(app)
    }

    suspend fun restoreService() {
        if (repository.read().automatic) app.startForegroundService(Intent(app, RotationService::class.java))
    }

    suspend fun report(message: String) {
        repository.update { it.copy(lastError = message) }
        _messages.emit(message)
        NextWidget.updateAll(app)
    }
}
