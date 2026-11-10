package com.wallpaperz.app

import android.Manifest
import android.app.NotificationManager
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PreviewSelection(val photos: List<WallpaperItem>, val initialId: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WallpaperZTheme {
            WallpaperZScreen(wallpaperApp)
            AppUpdateHost(wallpaperApp)
        } }
    }
}

@Composable private fun WallpaperZScreen(app: WallpaperZApp) {
    val state by app.repository.state.collectAsStateWithLifecycle()
    val switching by app.controller.switching.collectAsStateWithLifecycle()
    val importing by app.controller.importing.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    val categoryScroll = rememberLazyListState()
    var preview by remember { mutableStateOf<PreviewSelection?>(null) }
    fun openPreview(photo: WallpaperItem) {
        val photos = state!!.groups.first { group -> group.photos.any { it.id == photo.id } }.photos
        preview = PreviewSelection(photos, photo.id)
    }
    var deletingIds by remember { mutableStateOf(emptySet<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showGroups by remember { mutableStateOf(false) }
    var movingToGroup by remember { mutableStateOf(false) }
    fun finishSelection() { selecting = false; selectedIds = emptySet() }
    fun goBack() {
        if (selecting) finishSelection() else if (tab != 0) tab = 0 else detailOpen = false
    }
    BackHandler(enabled = selecting || detailOpen || tab != 0) { goBack() }
    var liveActive by remember { mutableStateOf(LiveWallpaperService.isActive(app)) }
    val livePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        liveActive = LiveWallpaperService.isActive(app)
        if (liveActive) app.previewItem.value?.let { selected ->
            app.scope.launch { app.controller.next(selectedId = selected.id) }
        }
        app.previewItem.value = null
    }
    fun activate(item: WallpaperItem? = null) {
        app.previewItem.value = item ?: state?.photos?.find { it.id == state?.currentId } ?: state?.photos?.firstOrNull()
        livePicker.launch(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, LiveWallpaperService.component(app)))
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var notificationsAllowed by remember { mutableStateOf(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) }
    fun perform(action: suspend () -> Unit) {
        app.scope.launch {
            try { action() } catch (e: Exception) { app.controller.report(e.message ?: "操作失败，请重试") }
        }
    }
    fun openGroup(id: String) {
        perform {
            app.controller.selectGroup(id)
            withContext(Dispatchers.Main) { finishSelection(); detailOpen = true; tab = 0 }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        perform { app.controller.next(enableAutomatic = true) }
    }
    val galleryPicker = rememberLauncherForActivityResult(XiaomiGalleryPicker()) { uris ->
        if (uris.isNotEmpty()) perform { app.controller.importPhotos(uris) }
    }
    fun openAlbums() {
        try { galleryPicker.launch(Unit) }
        catch (_: ActivityNotFoundException) {
            perform { app.controller.message("无法打开小米相册，请确认系统相册已安装并启用") }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
                liveActive = LiveWallpaperService.isActive(app)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(app) {
        app.controller.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(app) {
        try { app.controller.restoreService() }
        catch (e: Exception) { app.controller.report(e.message ?: "后台轮播恢复失败") }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().statusBarsPadding().semantics { testTagsAsResourceId = true }) {
            Column(Modifier.fillMaxSize()) {
                val current = state
                if(tab==0 && detailOpen && current!=null) Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        ZIconButton(ZSymbol.LEFT,"返回分类",{ goBack() },Modifier.testTag("category_back"),tint=MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        ZIconButton(ZSymbol.SETTINGS,"主题轮播设置",{ tab=1 },Modifier.testTag("group_settings"),enabled=!selecting && !importing,tint=MaterialTheme.colorScheme.onSurface)
                        if(current.photos.isNotEmpty()) ZTextAction(if(selecting) "完成" else "选择",{ if(selecting) finishSelection() else selecting=true },
                            Modifier.testTag("manage_library"),enabled=!importing)
                        if(importing) CircularProgressIndicator(Modifier.padding(10.dp).size(24.dp),strokeWidth=2.dp)
                        else ZIconButton(ZSymbol.PLUS,"导入照片与视频",{ openAlbums() },Modifier.testTag("import_button"),enabled=!selecting,tint=MaterialTheme.colorScheme.onSurface)
                    }
                    Column(Modifier.padding(start=24.dp,end=24.dp,top=10.dp,bottom=20.dp)) {
                        Text(current.group.name,fontSize=32.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(if(selecting) "已选择 ${selectedIds.size} 个素材" else "${current.photos.size} 个素材 · ${current.mode.title}播放",
                            Modifier.padding(top=6.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else Row(Modifier.fillMaxWidth().padding(start=24.dp,end=20.dp,top=18.dp,bottom=18.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(detailOpen) ZIconButton(ZSymbol.LEFT,"返回主题",{ goBack() },Modifier.testTag("detail_back"),tint=MaterialTheme.colorScheme.onSurface)
                    Column(Modifier.weight(1f)) {
                        Text(if(tab==0) "壁纸z" else "设置",fontSize=34.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                        Text(if(tab==0) "${current?.groups?.size ?: 0} 个主题，收藏不同的风景" else "为每一个主题，找到自己的节奏",
                            Modifier.padding(top=4.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(switching || importing) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                    else if(tab==0) ZIconButton(ZSymbol.PLUS,"管理与新建主题",{ showGroups=true },
                        Modifier.testTag("theme_picker"),filled=true,tint=MaterialTheme.colorScheme.onSurface)
                }
                if(current==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                else {
                    current.lastError?.let { error ->
                        Row(Modifier.padding(horizontal=20.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.error.copy(.09f))
                            .padding(start=14.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(error,Modifier.weight(1f).padding(vertical=12.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.error)
                            ZIconButton(ZSymbol.CLOSE,"关闭提示",{ perform { app.controller.clearError() } },tint=MaterialTheme.colorScheme.error)
                        }
                    }
                    if(tab==0 && detailOpen) ThemeDetail(current,app,selecting,selectedIds,importing,
                        onImport={ openAlbums() },onPreview={ openPreview(it) },
                        onSelect={ selecting=true; selectedIds=selectedIds+it },
                        onToggleSelection={ id -> selectedIds=if(id in selectedIds) selectedIds-id else selectedIds+id })
                    else if(tab==0) Home(current,app,switching,importing,liveActive,categoryScroll,
                        onActivate={ activate() },onNext={ perform { app.controller.next() } },onPreview={ openPreview(it) },onOpenTheme={ openGroup(it) })
                    else SettingsPage(current,switching,notificationsAllowed,liveActive,onActivate={ activate() },
                        onAutomatic={ enabled ->
                            if(!enabled) perform { app.controller.pause() }
                            else if(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else perform { app.controller.next(enableAutomatic=true) }
                        },onMode={ perform { app.controller.mode(it) } },onTrigger={ perform { app.controller.trigger(it) } },
                        onInterval={ perform { app.controller.interval(it) } },onWidget={
                            val manager=context.getSystemService(AppWidgetManager::class.java)
                            if(manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(context,NextWidget::class.java),null,null)
                            else perform { app.controller.message("请长按桌面，在小组件中添加壁纸z") }
                        })
                }
            }
            if(!detailOpen || selecting) GlassBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=if(selecting) 18.dp else 78.dp,vertical=12.dp)) {
                if(selecting) {
                    ZIconButton(ZSymbol.CLOSE,"退出多选",{ finishSelection() },Modifier.testTag("selection_close"),tint=MaterialTheme.colorScheme.onSurface)
                    ZTextAction(if(selectedIds.size==state?.photos?.size) "全不选" else "全选",{
                        val ids=state?.photos.orEmpty().map { it.id }.toSet()
                        selectedIds=if(selectedIds==ids) emptySet() else ids
                    },Modifier.weight(1f).testTag("selection_all"))
                    ZIconButton(ZSymbol.FOLDER,"移动到主题",{ movingToGroup=true },Modifier.testTag("selection_move"),
                        enabled=selectedIds.isNotEmpty() && (state?.groups?.size ?: 0)>1 && !importing)
                    val index=state?.photos?.indexOfFirst { it.id==selectedIds.singleOrNull() } ?: -1
                    listOf(-1,1).forEach { direction ->
                        val target=index+direction
                        ZIconButton(if(direction<0) ZSymbol.LEFT else ZSymbol.RIGHT,if(direction<0) "向前移动一位" else "向后移动一位",{
                            val order=state!!.photos.map { it.id }.toMutableList().apply { add(target,removeAt(index)) }
                            perform { app.controller.reorder(order) }
                        },Modifier.testTag(if(direction<0) "selection_previous" else "selection_next"),
                            enabled=selectedIds.size==1 && index>=0 && target in state!!.photos.indices && !switching && !importing)
                    }
                    ZIconButton(ZSymbol.TRASH,"删除选中素材",{ deletingIds=selectedIds },Modifier.testTag("selection_delete"),
                        enabled=selectedIds.isNotEmpty() && !importing,tint=MaterialTheme.colorScheme.error)
                } else listOf("壁纸","设置").forEachIndexed { index,title ->
                    val active=tab==index
                    Row(Modifier.weight(1f).height(48.dp).clip(CircleShape)
                        .background(if(active) MaterialTheme.colorScheme.primary.copy(.1f) else Color.Transparent)
                        .clickable { tab=index }.testTag(if(index==0) "home_tab" else "settings_tab"),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        val tint=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ZIcon(if(index==0) ZSymbol.PHOTO else ZSymbol.SETTINGS,tint=tint,modifier=Modifier.size(20.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(title,color=tint,fontSize=14.sp,fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp).padding(bottom=90.dp)) { data ->
                Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.onSurface,shadowElevation=8.dp) {
                    Text(data.visuals.message,Modifier.padding(horizontal=20.dp,vertical=16.dp),color=MaterialTheme.colorScheme.surface,fontSize=14.sp)
                }
            }
        }
    }
    preview?.let { selection ->
        Preview(selection.photos, selection.initialId, app, switching, onDismiss = { preview = null }, onApply = { selected ->
            if (!liveActive) { preview = null; activate(selected) } else app.scope.launch {
                if (app.controller.next(selectedId = selected.id) == RotationController.Result.APPLIED)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { preview = null }
            }
        })
    }
    if (showGroups && state != null) ThemeManager(state!!, onDismiss = { showGroups = false },
        onSelect = { id -> showGroups = false; openGroup(id) },
        onCreate = { name -> showGroups = false; perform {
            app.controller.createGroup(name)
            withContext(Dispatchers.Main) { finishSelection(); detailOpen=true; tab=0 }
        } },
        onRename = { id, name -> perform { app.controller.renameGroup(id, name) } },
        onDelete = { id -> perform { app.controller.deleteGroup(id) } },
        onEnable = { id, enabled -> perform { app.controller.enableGroup(id, enabled) } })
    if (movingToGroup && state != null) MoveToTheme(state!!, onDismiss = { movingToGroup = false }, onMove = { id ->
        val ids = selectedIds
        movingToGroup = false; finishSelection()
        perform { app.controller.moveToGroup(ids, id) }
    })
    if(deletingIds.isNotEmpty()) ZConfirm("删除选中的 ${deletingIds.size} 个素材？",
        "仅删除壁纸z中的副本，相册原素材会保留。",onDismiss={ deletingIds=emptySet() },onConfirm={
            val ids=deletingIds
            deletingIds=emptySet()
            perform { app.controller.delete(ids) }
            finishSelection()
        },modifier=Modifier.testTag("confirm_delete"))
}
