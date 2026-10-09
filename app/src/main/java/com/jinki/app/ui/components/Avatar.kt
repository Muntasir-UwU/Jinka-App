package com.jinki.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/** A resolved picture for one user; anything but None draws over the letter. */
sealed interface AvatarPhoto {
    data object None : AvatarPhoto
    data class LocalFile(val path: String, val version: Long) : AvatarPhoto
    data class Decoded(val bitmap: android.graphics.Bitmap) : AvatarPhoto
    data class Url(val url: String) : AvatarPhoto
}

/**
 * One user icon: the photo when available, with the initial-letter circle
 * behind it as placeholder while loading and fallback when missing.
 * Same circle + clip everywhere it is used.
 */
@Composable
fun Avatar(
    uid: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    photo: AvatarPhoto = AvatarPhoto.None,
    tint: Color,
    initialColor: Color,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint)
            .border(2.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AvatarInitial(
            name = name,
            fontSize = (size.value * 0.42f).sp,
            color = initialColor
        )
        when (photo) {
            is AvatarPhoto.None -> {}
            is AvatarPhoto.LocalFile -> {
                val request = remember(photo) {
                    ImageRequest.Builder(context)
                        .data(File(photo.path))
                        .memoryCacheKey("${photo.path}:${photo.version}")
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is AvatarPhoto.Decoded -> {
                Image(
                    bitmap = photo.bitmap.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is AvatarPhoto.Url -> {
                AsyncImage(
                    model = photo.url,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** The initial-letter circle every avatar falls back to. */
@Composable
fun AvatarInitial(
    name: String,
    fontSize: TextUnit,
    color: Color,
    fontFamily: FontFamily = FontFamily.Default,
    fontWeight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name.firstOrNull()?.uppercase() ?: "",
        color = color,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
