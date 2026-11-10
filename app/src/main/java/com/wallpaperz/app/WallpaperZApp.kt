package com.wallpaperz.app

import android.app.Application
import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

class WallpaperZApp : Application() {
    val previewItem = kotlinx.coroutines.flow.MutableStateFlow<WallpaperItem?>(null)
    val desktopPlayback = kotlinx.coroutines.flow.MutableStateFlow(DesktopPlayback())
    @Volatile var desktopEngine: Any? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy { CarouselRepository(this, scope) }
    val controller by lazy { RotationController(this) }
    val updates by lazy { AppUpdates(this, scope) }
}

val Context.wallpaperApp: WallpaperZApp get() = applicationContext as WallpaperZApp

class CarouselRepository(context: Context, scope: CoroutineScope) {
    private val store = DataStoreFactory.create(
        serializer = object : Serializer<CarouselState> {
            override val defaultValue = CarouselState()
            override suspend fun readFrom(input: InputStream): CarouselState = try {
                decodeCarouselState(input.readBytes().decodeToString())
            } catch (e: SerializationException) {
                throw CorruptionException("轮播配置读取失败", e)
            }
            override suspend fun writeTo(t: CarouselState, output: OutputStream) {
                output.write(Json.encodeToString(t).encodeToByteArray())
            }
        }, scope = scope, produceFile = { context.dataStoreFile("carousel.json") }
    )
    val changes get() = store.data
    val state: StateFlow<CarouselState?> = store.data.map<CarouselState, CarouselState?> { it }
        .stateIn(scope, SharingStarted.Eagerly, null)
    suspend fun read() = store.data.first()
    suspend fun update(transform: (CarouselState) -> CarouselState) = store.updateData(transform)
}
