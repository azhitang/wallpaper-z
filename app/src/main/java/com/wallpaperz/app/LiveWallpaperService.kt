package com.wallpaperz.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20 as GL
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class LiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val renderer = WallpaperRenderer(this@LiveWallpaperService,
            onError = { message -> scope.launch { wallpaperApp.controller.report(message) } },
            onPlayback = { id, generation, ended ->
                if (!isPreview && wallpaperApp.desktopEngine === this) wallpaperApp.desktopPlayback.update {
                    if (it.itemId == id && it.generation == generation) it.copy(ready = true, ended = ended) else it
                }
            })
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(false)
            if (!isPreview) wallpaperApp.desktopEngine = this
            scope.launch {
                combine(wallpaperApp.repository.changes, wallpaperApp.previewItem) { state, preview ->
                    val group = state.playbackGroup
                    val item = if (isPreview && preview != null) preview else
                        group.photos.find { it.id == group.currentId } ?: group.photos.firstOrNull()
                    Triple(item, group.lastChangedAt, !isPreview && state.automatic && group.trigger == Trigger.INTERVAL)
                }.collect { (item, generation, finishAtEnd) ->
                    if (!isPreview && wallpaperApp.desktopEngine === this@WallpaperEngine) wallpaperApp.desktopPlayback.update {
                        if (it.itemId == item?.id && it.generation == generation) it else
                            it.copy(itemId = item?.id, generation = generation, ready = false, ended = false)
                    }
                    renderer.show(item, generation, finishAtEnd)
                }
            }
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            renderer.attach(holder.surface, width, height)
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer.detach()
        }
        override fun onVisibilityChanged(visible: Boolean) {
            if (!isPreview && wallpaperApp.desktopEngine === this) wallpaperApp.desktopPlayback.update { it.copy(visible = visible) }
            renderer.visibility(visible)
        }
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) { renderer.redraw() }
        override fun onDestroy() {
            if (!isPreview && wallpaperApp.desktopEngine === this) {
                wallpaperApp.desktopEngine = null
                wallpaperApp.desktopPlayback.value = DesktopPlayback()
            }
            scope.cancel(); renderer.close(); super.onDestroy()
        }
    }

    companion object {
        const val FADE_MS = 650L
        fun component(context: Context) = ComponentName(context, LiveWallpaperService::class.java)
        fun isActive(context: Context) = WallpaperManager.getInstance(context)
            .getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.component == component(context)
    }
}

/** A single GPU composition path for photos, video frames and the transition between them. */
private class WallpaperRenderer(private val context: Context, private val onError: (String) -> Unit,
                                private val onPlayback: (String, Long, Boolean) -> Unit) {
    private val thread = HandlerThread("WallpaperZ-GL").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var bufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var windowAttached = false
    private var config: EGLConfig? = null
    private var width = 1
    private var height = 1
    private var program = 0
    private var requested: WallpaperItem? = null
    private var generation = 0L
    private var finishAtEnd = false
    private var front: Layer? = null
    private var back: Layer? = null
    private var fadeStarted = 0L
    @Volatile private var visible = false
    private var closed = false
    private val vertices = buffer(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f))
    private val coordinates = buffer(floatArrayOf(0f,1f, 1f,1f, 0f,0f, 1f,0f))
    private val drawTask = Runnable { draw() }

    private class Layer(val item: WallpaperItem, val generation: Long, val image: Int, var width: Int, var height: Int) {
        var external = 0
        var texture: SurfaceTexture? = null
        var inputSurface: Surface? = null
        var player: ExoPlayer? = null // Accessed on the main looper only.
        var newFrame = false
        var hasFrame = false
        var reportedReady = false
        @Volatile var disposed = false
        val matrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    }

    fun show(item: WallpaperItem?, generation: Long, finishAtEnd: Boolean) { handler.post {
        requested = item
        this.generation = generation
        this.finishAtEnd = finishAtEnd
        if (ready()) load(item)
        val layer = front
        main.post { layer?.let { if (!it.disposed) it.player?.apply {
            repeatMode = if (finishAtEnd) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            if (!finishAtEnd && playbackState == Player.STATE_ENDED) seekTo(0)
        } } }
    } }
    fun redraw() { handler.post { schedule() } }
    fun visibility(value: Boolean) { handler.post {
        visible = value
        if (value) schedule() else { handler.removeCallbacks(drawTask); back?.let(::release); back = null; fadeStarted = 0 }
    } }

    fun attach(nativeSurface: Surface, w: Int, h: Int) { handler.post {
        if (closed) return@post
        try {
            detachWindow()
            width = w; height = h
            if (display == EGL14.EGL_NO_DISPLAY) {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)) { "无法初始化桌面绘制" }
                val choices = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE), 0, choices, 0, 1, count, 0)
                config = choices[0] ?: error("设备不支持壁纸绘制")
                eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE), 0)
                bufferSurface = EGL14.eglCreatePbufferSurface(display, config,
                    intArrayOf(EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE), 0)
                check(EGL14.eglMakeCurrent(display, bufferSurface, bufferSurface, eglContext)) { "无法初始化视频绘制环境" }
                program = GL.glCreateProgram().also {
                    GL.glAttachShader(it, shader(GL.GL_VERTEX_SHADER, VERTEX))
                    GL.glAttachShader(it, shader(GL.GL_FRAGMENT_SHADER, FRAGMENT))
                    GL.glLinkProgram(it)
                    val status = IntArray(1); GL.glGetProgramiv(it, GL.GL_LINK_STATUS, status, 0)
                    check(status[0] != 0) { GL.glGetProgramInfoLog(it) }
                }
            }
            surface = EGL14.eglCreateWindowSurface(display, config, nativeSurface, intArrayOf(EGL14.EGL_NONE), 0)
            check(EGL14.eglMakeCurrent(display, surface, surface, eglContext)) { "桌面绘制表面不可用" }
            windowAttached = true
            GL.glViewport(0,0,width,height)
            load(requested)
            schedule()
        } catch (e: Exception) { onError(e.message ?: "桌面初始化失败") }
    } }

    private fun load(item: WallpaperItem?) {
        if (item == null || (front?.item == item && front?.generation == generation)) return
        try {
            val bitmap = WallpaperMedia.decode(WallpaperMedia.file(context, item.id), max(width,height).coerceAtMost(3840))
            val texture = newTexture(GL.GL_TEXTURE_2D)
            val layer = Layer(item, generation, texture, bitmap.width, bitmap.height)
            try { GLUtils.texImage2D(GL.GL_TEXTURE_2D,0,bitmap,0) } finally { bitmap.recycle() }
            back?.let(::release)
            back = front
            back?.let { old -> main.post { old.player?.pause() } }
            front = layer
            fadeStarted = if (back != null && visible) SystemClock.uptimeMillis() else 0L
            if (fadeStarted == 0L) { back?.let(::release); back = null }
            if (item.kind == MediaKind.VIDEO) prepareVideo(layer)
            schedule()
        } catch (e: Exception) { onError("无法显示素材：${e.message}") }
    }

    private fun prepareVideo(layer: Layer) {
        layer.external = newTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        val texture = SurfaceTexture(layer.external)
        layer.texture = texture
        texture.setOnFrameAvailableListener({
            if (!layer.disposed) {
                layer.newFrame = true
                if (visible && windowAttached) schedule() else consumeFrame(layer)
            }
        }, handler)
        layer.inputSurface = Surface(texture)
        val stopAtEnd = finishAtEnd
        main.post {
            if (layer.disposed) return@post
            layer.player = ExoPlayer.Builder(context).build().apply {
                volume = 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                repeatMode = if (stopAtEnd) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                setVideoSurface(layer.inputSurface)
                addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(size: VideoSize) { handler.post {
                        if (!layer.disposed) { layer.width = size.width; layer.height = size.height; schedule() }
                    } }
                    override fun onPlayerError(error: PlaybackException) { onError("视频播放失败：${error.errorCodeName}") }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (!layer.disposed && (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED))
                            onPlayback(layer.item.id, layer.generation, playbackState == Player.STATE_ENDED)
                    }
                })
                setMediaItem(MediaItem.fromUri(UriFromFile.video(context, layer.item.id)))
                prepare()
                playWhenReady = true
            }
        }
    }

    private fun schedule() {
        handler.removeCallbacks(drawTask)
        if (ready() && visible && windowAttached) handler.post(drawTask)
    }
    private fun draw() {
        if (!ready() || !visible || !windowAttached) return
        try {
            GL.glClearColor(.06f,.065f,.08f,1f)
            GL.glClear(GL.GL_COLOR_BUFFER_BIT)
            GL.glUseProgram(program)
            val progress = if (fadeStarted == 0L) 1f else
                ((SystemClock.uptimeMillis() - fadeStarted).toFloat() / LiveWallpaperService.FADE_MS).coerceIn(0f,1f)
            GL.glDisable(GL.GL_BLEND)
            back?.let { paint(it,1f) }
            GL.glEnable(GL.GL_BLEND)
            GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA)
            front?.let { paint(it, progress * progress * (3 - 2 * progress)) }
            EGL14.eglSwapBuffers(display,surface)
            front?.let { layer ->
                if (!layer.reportedReady && (layer.item.kind == MediaKind.PHOTO || layer.hasFrame)) {
                    layer.reportedReady = true
                    onPlayback(layer.item.id, layer.generation, false)
                }
            }
            if (progress < 1f) handler.postDelayed(drawTask,16)
            else if (back != null) { back?.let(::release); back = null; fadeStarted = 0 }
        } catch (e: Exception) { onError(e.message ?: "桌面绘制失败") }
    }
    private fun consumeFrame(layer: Layer) {
        if (layer.newFrame) {
            layer.texture?.updateTexImage()
            layer.texture?.getTransformMatrix(layer.matrix)
            layer.newFrame = false; layer.hasFrame = true
        }
    }
    private fun paint(layer: Layer, alpha: Float) {
        consumeFrame(layer)
        val screenRatio = width.toFloat()/height
        val mediaRatio = layer.width.toFloat()/layer.height
        val cropX = if (mediaRatio > screenRatio) screenRatio/mediaRatio else 1f
        val cropY = if (mediaRatio < screenRatio) mediaRatio/screenRatio else 1f
        GL.glUniform2f(GL.glGetUniformLocation(program,"uCrop"),cropX,cropY)
        GL.glUniform1f(GL.glGetUniformLocation(program,"uAlpha"),alpha)
        GL.glUniform1i(GL.glGetUniformLocation(program,"uUseVideo"),if (layer.hasFrame) 1 else 0)
        GL.glUniformMatrix4fv(GL.glGetUniformLocation(program,"uMatrix"),1,false,layer.matrix,0)
        GL.glActiveTexture(GL.GL_TEXTURE0); GL.glBindTexture(GL.GL_TEXTURE_2D,layer.image)
        GL.glUniform1i(GL.glGetUniformLocation(program,"uImage"),0)
        GL.glActiveTexture(GL.GL_TEXTURE1); GL.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,layer.external)
        GL.glUniform1i(GL.glGetUniformLocation(program,"uVideo"),1)
        val position = GL.glGetAttribLocation(program,"aPosition")
        val uv = GL.glGetAttribLocation(program,"aUv")
        GL.glEnableVertexAttribArray(position); GL.glEnableVertexAttribArray(uv)
        GL.glVertexAttribPointer(position,2,GL.GL_FLOAT,false,0,vertices)
        GL.glVertexAttribPointer(uv,2,GL.GL_FLOAT,false,0,coordinates)
        GL.glDrawArrays(GL.GL_TRIANGLE_STRIP,0,4)
    }
    private fun newTexture(target: Int): Int {
        val id = IntArray(1); GL.glGenTextures(1,id,0); GL.glBindTexture(target,id[0])
        GL.glTexParameteri(target,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR)
        GL.glTexParameteri(target,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
        GL.glTexParameteri(target,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE)
        GL.glTexParameteri(target,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
        return id[0]
    }
    private fun release(layer: Layer) {
        layer.disposed = true
        main.post {
            layer.player?.release(); layer.player = null
            layer.inputSurface?.release()
            layer.texture?.release()
        }
        GL.glDeleteTextures(1,intArrayOf(layer.image),0)
        if (layer.external != 0) GL.glDeleteTextures(1,intArrayOf(layer.external),0)
    }
    private fun ready() = !closed && program != 0
    // Preserve the video and GL context when Android removes the hidden desktop surface.
    private fun detachWindow() {
        handler.removeCallbacks(drawTask)
        if (display != EGL14.EGL_NO_DISPLAY && surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, bufferSurface, bufferSurface, eglContext)
            EGL14.eglDestroySurface(display, surface)
        }
        surface = EGL14.EGL_NO_SURFACE
        windowAttached = false
    }
    private fun destroySurface() {
        handler.removeCallbacks(drawTask)
        front?.let(::release); back?.let(::release); front = null; back = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (program != 0) GL.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,surface)
            if (bufferSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,bufferSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,eglContext)
            EGL14.eglTerminate(display)
        }
        surface = EGL14.EGL_NO_SURFACE; display = EGL14.EGL_NO_DISPLAY
        bufferSurface = EGL14.EGL_NO_SURFACE; windowAttached = false
        eglContext = EGL14.EGL_NO_CONTEXT; program = 0
    }
    fun detach() { handler.post { detachWindow() } }
    fun close() { handler.post { destroySurface(); closed = true; thread.quitSafely() } }
    private fun shader(type: Int, code: String): Int = GL.glCreateShader(type).also {
        GL.glShaderSource(it,code); GL.glCompileShader(it)
        val status = IntArray(1); GL.glGetShaderiv(it,GL.GL_COMPILE_STATUS,status,0)
        check(status[0] != 0) { GL.glGetShaderInfoLog(it) }
    }
    companion object {
        private fun buffer(data: FloatArray) = ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
        private const val VERTEX = "attribute vec2 aPosition; attribute vec2 aUv; varying vec2 vUv; void main(){gl_Position=vec4(aPosition,0.,1.);vUv=aUv;}"
        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vUv; uniform vec2 uCrop; uniform float uAlpha; uniform int uUseVideo;
            uniform sampler2D uImage; uniform samplerExternalOES uVideo; uniform mat4 uMatrix;
            void main(){
                vec2 uv=(vUv-0.5)*uCrop+0.5;
                vec4 color;
                if(uUseVideo==1){uv.y=1.0-uv.y;color=texture2D(uVideo,(uMatrix*vec4(uv,0.,1.)).xy);}
                else{color=texture2D(uImage,uv);}
                gl_FragColor=vec4(color.rgb,color.a*uAlpha);
            }
        """
    }
}

private object UriFromFile {
    fun video(context: Context, id: String) = android.net.Uri.fromFile(WallpaperMedia.video(context,id))
}
