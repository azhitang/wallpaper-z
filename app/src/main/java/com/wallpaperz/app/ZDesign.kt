package com.wallpaperz.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun WallpaperZTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF0A84FF), onPrimary = Color.White,
        background = Color(0xFF000000), surface = Color(0xFF1C1C1E),
        onBackground = Color(0xFFF5F5F7), onSurface = Color(0xFFF5F5F7),
        onSurfaceVariant = Color(0xFF98989F), secondaryContainer = Color(0xFF2C2C2E),
        primaryContainer = Color(0xFF102A45), error = Color(0xFFFF6961), outlineVariant = Color(0xFF38383A)
    ) else lightColorScheme(
        primary = Color(0xFF007AFF), onPrimary = Color.White,
        background = Color(0xFFF2F2F7), surface = Color.White,
        onBackground = Color(0xFF1C1C1E), onSurface = Color(0xFF1C1C1E),
        onSurfaceVariant = Color(0xFF767680), secondaryContainer = Color(0xFFE8E8ED),
        primaryContainer = Color(0xFFE6F1FF), error = Color(0xFFFF3B30), outlineVariant = Color(0xFFDEDEE3)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

enum class ZSymbol { PHOTO, SETTINGS, PLUS, NEXT, CLOSE, CHECK, DOWN, RIGHT, LEFT, TRASH, FOLDER, EDIT, CLOCK, SCREEN, BELL, PLAY }

/** A small set of rounded, consistent line symbols used throughout the interface. */
@Composable fun ZIcon(symbol: ZSymbol, modifier: Modifier = Modifier, description: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier.size(22.dp).semantics { if (description != null) contentDescription = description }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x,y), Offset(x2,y2), 1.7f, StrokeCap.Round)
            fun path(vararg points: Float) {
                drawPath(Path().apply { moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i],points[i+1]) }, tint, style = stroke)
            }
            when (symbol) {
                ZSymbol.PLUS -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
                ZSymbol.CLOSE -> { line(7f,7f,17f,17f); line(7f,17f,17f,7f) }
                ZSymbol.CHECK -> path(5f,12f,10f,17f,19f,7f)
                ZSymbol.RIGHT -> path(9f,5f,16f,12f,9f,19f)
                ZSymbol.LEFT -> path(15f,5f,8f,12f,15f,19f)
                ZSymbol.DOWN -> path(6f,9f,12f,15f,18f,9f)
                ZSymbol.NEXT -> { path(5f,6f,14f,12f,5f,18f,5f,6f); line(18f,6f,18f,18f) }
                ZSymbol.PLAY -> path(8f,5f,19f,12f,8f,19f,8f,5f)
                ZSymbol.PHOTO -> {
                    drawRoundRect(tint, Offset(3f,4f), Size(18f,16f), CornerRadius(4f), style=stroke)
                    drawCircle(tint,1.5f,Offset(8f,9f),style=stroke)
                    path(4f,17f,10f,12f,13f,15f,17f,10f,21f,14f)
                }
                ZSymbol.SETTINGS -> {
                    drawCircle(tint,7f,Offset(12f,12f),style=stroke)
                    drawCircle(tint,2.5f,Offset(12f,12f),style=stroke)
                    repeat(8) { index ->
                        val a = index * Math.PI / 4
                        line((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),
                            (12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat())
                    }
                }
                ZSymbol.TRASH -> {
                    path(5f,7f,6f,20f,18f,20f,19f,7f); line(3f,6f,21f,6f)
                    path(9f,6f,9f,3f,15f,3f,15f,6f); line(10f,10f,10f,16f); line(14f,10f,14f,16f)
                }
                ZSymbol.FOLDER -> path(3f,7f,3f,19f,21f,19f,21f,7f,12f,7f,10f,4f,3f,4f,3f,7f)
                ZSymbol.EDIT -> { path(5f,15f,4f,20f,9f,19f,20f,8f,16f,4f,5f,15f); line(13f,7f,17f,11f) }
                ZSymbol.CLOCK -> { drawCircle(tint,9f,Offset(12f,12f),style=stroke); path(12f,7f,12f,12f,16f,14f) }
                ZSymbol.SCREEN -> { drawRoundRect(tint,Offset(6f,2f),Size(12f,20f),CornerRadius(3f),style=stroke); line(10f,18f,14f,18f) }
                ZSymbol.BELL -> { path(5f,16f,7f,13f,7f,8f,9f,4f,15f,4f,17f,8f,17f,13f,19f,16f,5f,16f); line(10f,20f,14f,20f) }
            }
        }
    }
}

@Composable fun ZButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    symbol: ZSymbol? = null, destructive: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(stiffness=550f), label="button-press")
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val foreground = Color.White
    Row(modifier.graphicsLayer { scaleX=scale; scaleY=scale; alpha=if(enabled) 1f else .35f }
        .heightIn(min=50.dp).clip(RoundedCornerShape(17.dp)).background(color)
        .clickable(source, indication=null, enabled=enabled, role=Role.Button, onClick=onClick)
        .padding(horizontal=20.dp, vertical=13.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.Center) {
        if (symbol != null) { ZIcon(symbol, tint=foreground, modifier=Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color=foreground, fontSize=16.sp, fontWeight=FontWeight.SemiBold, maxLines=1)
    }
}

@Composable fun ZTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.heightIn(min=44.dp).clip(CircleShape)
        .clickable(enabled=enabled, role=Role.Button, onClick=onClick).padding(horizontal=12.dp), contentAlignment=Alignment.Center) {
        Text(text, color=color.copy(alpha=if(enabled) 1f else .35f), fontSize=15.sp, fontWeight=FontWeight.Medium)
    }
}

@Composable fun ZIconButton(symbol: ZSymbol, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, filled: Boolean = false, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.size(44.dp).clip(CircleShape).background(if(filled) MaterialTheme.colorScheme.secondaryContainer.copy(.65f) else Color.Transparent)
        .clickable(enabled=enabled, role=Role.Button, onClick=onClick), contentAlignment=Alignment.Center) {
        ZIcon(symbol,Modifier.size(21.dp),description,tint.copy(alpha=if(enabled) 1f else .25f))
    }
}

@Composable fun GlassBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.shadow(20.dp,CircleShape,ambientColor=Color.Black.copy(.08f),spotColor=Color.Black.copy(.1f))
        .clip(CircleShape).background(Brush.verticalGradient(listOf(colors.surface.copy(.96f),colors.surface.copy(.87f))))
        .border(.8.dp,colors.onSurface.copy(.07f),CircleShape).padding(6.dp),
        verticalAlignment=Alignment.CenterVertically,content=content)
}

@Composable fun ZToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val offset by animateDpAsState(if(checked) 20.dp else 0.dp,spring(stiffness=500f),label="toggle")
    Box(modifier.size(55.dp,44.dp).toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=onCheckedChange),contentAlignment=Alignment.Center) {
        Box(Modifier.size(51.dp,31.dp).clip(CircleShape).background(
            (if(checked) Color(0xFF34C759) else MaterialTheme.colorScheme.secondaryContainer).copy(alpha=if(enabled) 1f else .45f))) {
            Box(Modifier.padding(2.dp).offset { IntOffset(offset.roundToPx(),0) }.size(27.dp).shadow(2.dp,CircleShape).background(Color.White,CircleShape))
        }
    }
}

@Composable fun ZCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).toggleable(checked,role=Role.Checkbox,onValueChange=onCheckedChange),contentAlignment=Alignment.Center) {
        SelectionMark(checked)
    }
}

@Composable fun SelectionMark(checked: Boolean, modifier: Modifier = Modifier, overImage: Boolean = false) {
    val color=MaterialTheme.colorScheme.primary
    Box(modifier.size(24.dp).background(if(checked) color else if(overImage) Color.Black.copy(.15f) else Color.Transparent,CircleShape)
        .border(if(checked) 0.dp else 1.5.dp,if(overImage) Color.White.copy(.9f) else MaterialTheme.colorScheme.outlineVariant,CircleShape),contentAlignment=Alignment.Center) {
        if(checked) ZIcon(ZSymbol.CHECK,Modifier.size(15.dp),"已选中",Color.White)
    }
}

@Composable fun ZSegmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(.6f)).padding(3.dp)) {
        val width=maxWidth/labels.size
        val offset by animateDpAsState(width*selected,spring(stiffness=500f),label="segmented-selection")
        Box(Modifier.offset { IntOffset(offset.roundToPx(),0) }.width(width).fillMaxHeight().shadow(2.dp,RoundedCornerShape(10.dp))
            .background(if(isSystemInDarkTheme()) Color(0xFF48484A) else MaterialTheme.colorScheme.surface,RoundedCornerShape(10.dp)))
        Row(Modifier.fillMaxSize()) { labels.forEachIndexed { index,label ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable(role=Role.RadioButton) { onSelect(index) }
                .semantics { this.selected = index==selected },contentAlignment=Alignment.Center) {
                Text(label,fontSize=14.sp,fontWeight=if(index==selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        } }
    }
}

@Composable fun ZGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface),content=content)
}

@Composable fun ZDivider() { HorizontalDivider(Modifier.padding(start=18.dp),thickness=.5.dp,color=MaterialTheme.colorScheme.outlineVariant.copy(.65f)) }

@Composable fun ZSectionLabel(text: String) {
    Text(text,Modifier.padding(start=18.dp,bottom=8.dp),fontSize=12.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun ZCaption(text: String, modifier: Modifier = Modifier) {
    Text(text,modifier.padding(horizontal=18.dp,vertical=9.dp),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun ZSettingsRow(title: String, subtitle: String?, symbol: ZSymbol, color: Color,
    onClick: () -> Unit, modifier: Modifier = Modifier, value: String? = null) {
    Row(modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onClick).padding(horizontal=18.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(color,RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
            ZIcon(symbol,modifier=Modifier.size(19.dp),tint=Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title,fontSize=16.sp)
            if(subtitle!=null) Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))
        }
        if(value!=null) Text(value,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=8.dp))
        ZIcon(ZSymbol.RIGHT,modifier=Modifier.padding(start=8.dp).size(12.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(.65f))
    }
}

@Composable fun ZSheet(title: String, onDismiss: () -> Unit, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible=true }
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().semantics { testTagsAsResourceId=true },contentAlignment=Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() },indication=null,onClick=onDismiss))
            AnimatedVisibility(visible,enter=slideInVertically { it }+fadeIn()) {
                Column(Modifier.fillMaxWidth().heightIn(max=maxHeight*.9f)
                    .clip(RoundedCornerShape(topStart=32.dp,topEnd=32.dp)).background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().padding(bottom=20.dp)) {
                    Box(Modifier.padding(top=10.dp,bottom=6.dp).size(34.dp,5.dp).background(MaterialTheme.colorScheme.onSurface.copy(.15f),CircleShape).align(Alignment.CenterHorizontally))
                    Row(Modifier.fillMaxWidth().padding(start=24.dp,end=14.dp,top=6.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title,fontSize=25.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            if(subtitle!=null) Text(subtitle,Modifier.padding(top=5.dp),fontSize=13.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ZIconButton(ZSymbol.CLOSE,"关闭",onDismiss,filled=true,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    content()
                }
            }
        }
    }
}

@Composable fun ZConfirm(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    ZSheet(title,onDismiss,subtitle=message) {
        ZButton("删除",onConfirm,modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),destructive=true)
        ZTextAction("取消",onDismiss,Modifier.fillMaxWidth().padding(top=2.dp))
    }
}
