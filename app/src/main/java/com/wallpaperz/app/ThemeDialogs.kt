package com.wallpaperz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable fun ThemeManager(state: CarouselState, onDismiss: () -> Unit, onSelect: (String) -> Unit,
    onCreate: (String) -> Unit, onRename: (String,String) -> Unit, onDelete: (String) -> Unit,
    onEnable: (String,Boolean) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WallpaperGroup?>(null) }
    var deleting by remember { mutableStateOf<WallpaperGroup?>(null) }
    var draft by remember { mutableStateOf("") }
    val context=LocalContext.current
    ZSheet("我的主题",onDismiss,"勾选主题参与混播，轻点名称编辑素材") {
        LazyColumn(Modifier.padding(horizontal=16.dp).heightIn(max=390.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)) {
            items(state.groups,key={ it.id }) { group ->
                Row(Modifier.fillMaxWidth().padding(start=4.dp,end=4.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    ZCheck(group.id in state.enabledGroupIds,{ onEnable(group.id,it) },Modifier.testTag("enable_theme_${group.id}"))
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(group.id) }.testTag("theme_${group.id}")
                        .padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        val cover=group.photos.firstOrNull()
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondaryContainer),contentAlignment=Alignment.Center) {
                            if(cover!=null) AsyncImage(WallpaperMedia.file(context,cover.id),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            else ZIcon(ZSymbol.FOLDER,modifier=Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f).padding(start=10.dp)) {
                            Text(group.name,fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("${group.photos.size} 项 · ${group.mode.title}",Modifier.padding(top=4.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(group.id==state.activeGroupId) Text("正在编辑",Modifier.padding(top=3.dp),fontSize=10.sp,color=MaterialTheme.colorScheme.primary)
                        }
                    }
                    ZIconButton(ZSymbol.EDIT,"重命名 ${group.name}",{ editing=group; draft=group.name },tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    ZIconButton(ZSymbol.TRASH,"删除主题 ${group.name}",{ deleting=group },enabled=state.groups.size>1,tint=MaterialTheme.colorScheme.error)
                }
                if(group!=state.groups.last()) ZDivider()
            }
        }
        ZCaption("每个主题独立保存播放规则，空主题会自动跳过。")
        ZButton("新建主题",{ creating=true; draft="" },Modifier.fillMaxWidth().padding(horizontal=20.dp).testTag("create_theme"),symbol=ZSymbol.PLUS)
    }
    if(creating || editing!=null) {
        val duplicate=state.groups.any { it.id!=editing?.id && it.name==draft.trim() }
        val dismiss={ creating=false; editing=null }
        ZSheet(if(creating) "新建主题" else "重命名主题",dismiss,"给喜欢的风景，一个专属的名字") {
            BasicTextField(draft,{ draft=it },singleLine=true,textStyle=TextStyle(color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp),
                cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal=16.dp,vertical=17.dp).testTag("theme_name"),
                decorationBox={ inner ->
                    Box { if(draft.isEmpty()) Text("主题名称",fontSize=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                })
            if(duplicate) Text("主题名称已存在",Modifier.padding(horizontal=24.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.error)
            ZButton("保存",{
                if(creating) onCreate(draft.trim()) else onRename(editing!!.id,draft.trim())
                creating=false; editing=null
            },Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp).testTag("save_theme"),enabled=draft.isNotBlank() && !duplicate)
            ZTextAction("取消",dismiss,Modifier.fillMaxWidth())
        }
    }
    deleting?.let { group ->
        ZConfirm("删除主题“${group.name}”？","同时移除 ${group.photos.size} 个素材副本，相册原素材会保留。",
            onDismiss={ deleting=null },onConfirm={ deleting=null; onDelete(group.id) })
    }
}

@Composable fun MoveToTheme(state: CarouselState, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    ZSheet("移动到主题",onDismiss,"素材会保留原文件，只调整所属主题") {
        LazyColumn(Modifier.padding(horizontal=20.dp).heightIn(max=380.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)) {
            items(state.groups.filterNot { it.id==state.activeGroupId },key={ it.id }) { group ->
                ZSettingsRow(group.name,"${group.photos.size} 个素材",ZSymbol.FOLDER,Color(0xFF007AFF),{ onMove(group.id) })
            }
        }
        ZTextAction("取消",onDismiss,Modifier.fillMaxWidth().padding(top=8.dp))
    }
}
