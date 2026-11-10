package com.wallpaperz.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage

@Composable fun Home(state: CarouselState, app: WallpaperZApp, switching: Boolean, importing: Boolean,
    liveActive: Boolean, scrollState: LazyListState, onActivate: () -> Unit, onNext: () -> Unit,
    onPreview: (WallpaperItem) -> Unit, onOpenTheme: (String) -> Unit) {
    val displayed=state.playbackGroup
    val current=displayed.photos.find { it.id==displayed.currentId } ?: displayed.photos.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().testTag("category_list"),state=scrollState,
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=2.dp,bottom=116.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if(state.groups.any { it.photos.isNotEmpty() }) item(key="current") {
            ZGroup {
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled=current!=null) { current?.let(onPreview) },verticalAlignment=Alignment.CenterVertically) {
                        if(current!=null) AsyncImage(WallpaperMedia.file(app,current.id),"当前素材",Modifier.size(40.dp,54.dp).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)
                        else ZIcon(ZSymbol.PHOTO,Modifier.padding(8.dp).size(24.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start=12.dp)) {
                            Text(if(current!=null) "此刻的桌面" else "选择一张，开始播放",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                            Text(if(current==null) "轻点右侧切换壁纸" else "${displayed.name} · ${if(state.automatic) "轮播中" else "已暂停"}",Modifier.padding(top=4.dp),fontSize=11.sp,
                                color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                    ZIconButton(ZSymbol.NEXT,"换一张",onNext,Modifier.testTag("next_button"),
                        enabled=liveActive && state.enabledGroups.isNotEmpty() && !switching,filled=true)
                }
                if(!liveActive) ZButton("启用动态壁纸",onActivate,Modifier.fillMaxWidth().padding(start=12.dp,end=12.dp,bottom=12.dp).testTag("activate_wallpaper"))
            }
        }
        items(state.groups,key={ it.id }) { group ->
            ThemeCategoryCard(group,app,enabled=!importing,onOpen={ onOpenTheme(group.id) })
        }
    }
}

@Composable private fun ThemeCategoryCard(group: WallpaperGroup, app: WallpaperZApp, enabled: Boolean, onOpen: () -> Unit) {
    ZGroup(Modifier.testTag("category_${group.id}")) {
        Row(Modifier.fillMaxWidth().clickable(enabled=enabled,onClick=onOpen).testTag("category_header_${group.id}")
            .padding(start=18.dp,end=16.dp,top=17.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(group.name,Modifier.weight(1f),fontSize=19.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(group.photos.size.toString(),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ZIcon(ZSymbol.RIGHT,Modifier.padding(start=10.dp).size(13.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(.7f))
        }
        if(group.photos.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start=14.dp,end=14.dp,bottom=14.dp).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(.45f)).clickable(enabled=enabled,onClick=onOpen)
                .padding(horizontal=16.dp,vertical=24.dp),verticalAlignment=Alignment.CenterVertically) {
                ZIcon(ZSymbol.PHOTO,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("还没有素材，点击进入添加",Modifier.padding(start=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom=14.dp)) {
            // Two complete portraits and half of the third hint at horizontal scrolling.
            val coverWidth=(maxWidth-14.dp-20.dp)/2.5f
            LazyRow(Modifier.fillMaxWidth().testTag("category_previews_${group.id}"),contentPadding=PaddingValues(horizontal=14.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                items(group.photos,key={ it.id }) { photo ->
                    Box(Modifier.width(coverWidth).aspectRatio(.56f).clip(RoundedCornerShape(18.dp))
                        .clickable(enabled=enabled,onClick=onOpen).testTag("category_cover_${group.id}_${photo.id}")) {
                        AsyncImage(WallpaperMedia.file(app,photo.id),photo.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        if(photo.kind==MediaKind.VIDEO) ZIcon(ZSymbol.PLAY,Modifier.align(Alignment.BottomStart).padding(10.dp).size(15.dp),"视频",Color.White)
                    }
                }
            }
        }
    }
}

@Composable fun ThemeDetail(state: CarouselState, app: WallpaperZApp, selecting: Boolean, selectedIds: Set<String>, importing: Boolean,
    onImport: () -> Unit, onPreview: (WallpaperItem) -> Unit, onSelect: (String) -> Unit, onToggleSelection: (String) -> Unit) {
    val displayed=state.playbackGroup
    LazyColumn(Modifier.fillMaxSize().testTag("group_detail"),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=4.dp,bottom=116.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(state.photos.chunked(3),key={ "grid_${it.first().id}" }) { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach { photo ->
                    val playing=displayed.id==state.activeGroupId && state.currentId==photo.id
                    val checked=photo.id in selectedIds
                    Box(Modifier.weight(1f).aspectRatio(.56f).clip(RoundedCornerShape(18.dp))
                        .combinedClickable(onClick={ if(selecting) onToggleSelection(photo.id) else onPreview(photo) },
                            onLongClick={ onSelect(photo.id) },onLongClickLabel="选择素材")
                        .semantics { selected=checked }.testTag("photo_${photo.id}")) {
                        AsyncImage(WallpaperMedia.file(app,photo.id),photo.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.22f)))))
                        if(selecting) SelectionMark(checked,Modifier.align(Alignment.TopEnd).padding(8.dp),overImage=true)
                        else if(playing) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(20.dp).background(MaterialTheme.colorScheme.primary,CircleShape),contentAlignment=Alignment.Center) {
                            ZIcon(ZSymbol.CHECK,Modifier.size(12.dp),"当前素材",Color.White)
                        }
                        if(photo.kind==MediaKind.VIDEO) Row(Modifier.align(Alignment.BottomStart).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            ZIcon(ZSymbol.PLAY,Modifier.size(12.dp),"视频",Color.White)
                            Text("${photo.durationMs/60_000}:${((photo.durationMs/1000)%60).toString().padStart(2,'0')}",Modifier.padding(start=3.dp),fontSize=10.sp,color=Color.White)
                        }
                    }
                }
                repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if(state.photos.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=60.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                ZIcon(ZSymbol.PHOTO,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("这个主题还没有素材",Modifier.padding(top=18.dp),fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text("从小米相册添加照片与视频",Modifier.padding(top=7.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ZButton("添加素材",onImport,Modifier.padding(top=22.dp),enabled=!importing,symbol=ZSymbol.PLUS)
            }
        }
    }
}

@Composable fun Preview(photos: List<WallpaperItem>, initialId: String, app: WallpaperZApp, switching: Boolean,
    onDismiss: () -> Unit, onApply: (WallpaperItem) -> Unit) {
    val bounds=LocalContext.current.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
    val ratio=minOf(bounds.width(),bounds.height()).toFloat()/maxOf(bounds.width(),bounds.height())
    val pager=rememberPagerState(initialPage=photos.indexOfFirst { it.id==initialId },pageCount={ photos.size })
    val photo=photos[pager.settledPage]
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).semantics { testTagsAsResourceId=true }) {
            HorizontalPager(pager,Modifier.fillMaxSize().testTag("preview_pager"),userScrollEnabled=!switching,key={ photos[it].id }) { index ->
                val item=photos[index]
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    val mediaModifier=Modifier.fillMaxWidth().aspectRatio(ratio)
                    if(item.kind==MediaKind.VIDEO) VideoPreview(item,mediaModifier,playing=pager.settledPage==index && !pager.isScrollInProgress)
                    else AsyncImage(WallpaperMedia.file(app,item.id),"壁纸预览",mediaModifier,contentScale=ContentScale.Crop)
                }
            }
            Box(Modifier.fillMaxWidth().height(170.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.85f)))))
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(.5f),Color.Transparent))).statusBarsPadding()
                .padding(start=22.dp,end=14.dp,top=10.dp,bottom=18.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("壁纸预览",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                    Text(photo.name,Modifier.padding(top=4.dp).testTag("preview_name"),fontSize=12.sp,color=Color.White.copy(.7f),maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Text("${pager.settledPage+1} / ${photos.size}",Modifier.padding(horizontal=8.dp).testTag("preview_position"),fontSize=12.sp,color=Color.White.copy(.8f))
                ZIconButton(ZSymbol.CLOSE,"关闭预览",onDismiss,Modifier.testTag("preview_close"),tint=Color.White)
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(if(photo.kind==MediaKind.VIDEO) "视频 · 静音循环" else "照片 · 居中裁剪",Modifier.padding(bottom=16.dp).testTag("preview_kind"),color=Color.White.copy(.8f),fontSize=13.sp)
                ZButton(if(switching) "正在应用" else "设为桌面壁纸",{ onApply(photo) },Modifier.fillMaxWidth().testTag("apply_button"),
                    enabled=!switching && !pager.isScrollInProgress,symbol=ZSymbol.SCREEN)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun VideoPreview(item: WallpaperItem, modifier: Modifier, playing: Boolean) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val player=remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            volume=0f
            trackSelectionParameters=trackSelectionParameters.buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO,true).build()
            repeatMode=Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(WallpaperMedia.video(context,item.id))))
            prepare()
        }
    }
    DisposableEffect(player,lifecycle,playing) {
        player.playWhenReady=playing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer=LifecycleEventObserver { _,_ -> player.playWhenReady=playing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory={ PlayerView(it).apply {
        useController=false
        resizeMode=androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        this.player=player
    } },modifier=modifier)
}

@Composable fun SettingsPage(state: CarouselState, switching: Boolean, notificationsAllowed: Boolean,
    liveActive: Boolean, onActivate: () -> Unit, onAutomatic: (Boolean) -> Unit, onMode: (PlayMode) -> Unit,
    onTrigger: (Trigger) -> Unit, onInterval: (Int) -> Unit, onWidget: () -> Unit) {
    val context=LocalContext.current
    var intervalMenu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings_list"),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=116.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item {
            ZGroup {
                Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动轮播",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                        Text(if(!liveActive) "请先启用动态壁纸" else if(state.enabledGroups.isEmpty()) "请先勾选有素材的主题" else if(state.automatic) "正在混播 ${state.enabledGroups.size} 个主题" else "已暂停，仍可手动切换",
                            Modifier.padding(top=5.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ZToggle(state.automatic,onAutomatic,Modifier.testTag("automatic_switch"),enabled=liveActive && state.enabledGroups.isNotEmpty() && !switching)
                }
                if(state.photos.isNotEmpty()) {
                    ZDivider()
                    ZSettingsRow(if(liveActive) "动态壁纸已启用" else "启用动态壁纸",subtitle=null,symbol=ZSymbol.SCREEN,color=Color(0xFF007AFF),onClick=onActivate)
                }
            }
            if(state.enabledGroups.isNotEmpty()) ZCaption("当前播放「${state.playbackGroup.name}」，按此主题规则切换。")
        }
        item {
            ZSectionLabel("主题专属设置 · ${state.group.name}")
            ZGroup(Modifier.animateContentSize()) {
                Column(Modifier.padding(18.dp)) {
                    Text("播放方式",fontSize=16.sp,fontWeight=FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    ZSegmented(PlayMode.entries.map { it.title },state.mode.ordinal,{ onMode(PlayMode.entries[it]) })
                }
                ZDivider()
                Column(Modifier.padding(18.dp)) {
                    Text("切换时机",fontSize=16.sp,fontWeight=FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    ZSegmented(Trigger.entries.map { it.title },state.trigger.ordinal,{ onTrigger(Trigger.entries[it]) })
                }
                if(state.trigger==Trigger.INTERVAL) {
                    ZDivider()
                    ZSettingsRow("图片停留",subtitle=null,symbol=ZSymbol.CLOCK,color=Color(0xFFFF9F0A),onClick={ intervalMenu=true },value=intervalLabel(state.intervalSeconds),
                        modifier=Modifier.testTag("interval_picker"))
                }
            }
            ZCaption(when(state.mode) {
                PlayMode.ORDERED -> "按素材排列顺序播放；已播完的主题等待其他主题。"
                PlayMode.SHUFFLE -> "主题内随机抽取，全部勾选壁纸播完前不重复。"
                PlayMode.FAVOR_NEW -> "新导入素材更容易排在本轮前面，最高 4 倍权重、7 天内回落；所有壁纸播完前不重复。"
            } + if(state.trigger==Trigger.INTERVAL) "图片按所选时间切换，视频静音播完切换；打开其他应用后继续轮播。" else "按所选屏幕事件切换，不受桌面可见性限制。")
        }
        item {
            ZSectionLabel("桌面与系统")
            ZGroup {
                ZSettingsRow("桌面小组件","1×1 图标，轻点换下一张",ZSymbol.PLUS,Color(0xFFAF52DE),onWidget,Modifier.testTag("add_widget"))
                ZDivider()
                ZSettingsRow("通知",subtitle=null,symbol=ZSymbol.BELL,color=Color(0xFFFF453A),value=if(notificationsAllowed) "已开启" else "未开启",onClick={
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
                })
                ZDivider()
                ZSettingsRow("后台运行设置",subtitle=null,symbol=ZSymbol.SETTINGS,color=Color(0xFF8E8E93),onClick={
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
                })
            }
            ZCaption("小米手机建议允许后台自启动，并将省电策略设为“无限制”。系统强行停止后需重新打开应用。")
        }
        item { ZGroup { UpdateSettingsRow() } }
        item {
            Column(Modifier.fillMaxWidth().padding(top=2.dp,bottom=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                AsyncImage(com.wallpaperz.app.R.drawable.icon_swirl,null,Modifier.size(46.dp))
                Text("壁纸z",Modifier.padding(top=8.dp),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0.6.3 · 让桌面随心而变",Modifier.padding(top=3.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant.copy(.7f))
            }
        }
    }
    if(intervalMenu) ZSheet("图片停留时间",{ intervalMenu=false },"视频静音播放一遍后切换，不受桌面可见性限制") {
        LazyColumn(Modifier.padding(horizontal=20.dp).heightIn(max=440.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)) {
            items(INTERVALS) { seconds ->
                Row(Modifier.fillMaxWidth().heightIn(min=50.dp).clickable { intervalMenu=false; onInterval(seconds) }
                    .padding(horizontal=18.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(intervalLabel(seconds),Modifier.weight(1f),fontSize=16.sp)
                    if(seconds==state.intervalSeconds) ZIcon(ZSymbol.CHECK,modifier=Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                }
                if(seconds!=INTERVALS.last()) ZDivider()
            }
        }
    }
}
