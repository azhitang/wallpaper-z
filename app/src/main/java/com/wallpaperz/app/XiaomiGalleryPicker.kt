package com.wallpaperz.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class XiaomiGalleryPicker : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_GET_CONTENT).apply {
        setPackage("com.miui.gallery")
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        return buildList {
            intent.data?.let { add(it) }
            intent.clipData?.let { clips ->
                for (index in 0 until clips.itemCount) add(clips.getItemAt(index).uri)
            }
        }.distinct()
    }
}
