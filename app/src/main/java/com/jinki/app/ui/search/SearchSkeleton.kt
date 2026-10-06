package com.jinki.app.ui.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


private val SkeletonBase = Color.White.copy(alpha = 0.3f)


private val SkeletonHighlight = Color.White.copy(alpha = 0.5f)

private const val SHIMMER_MS = 1200
private const val SHIMMER_BAND_PX = 400f


@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "searchSkeleton")
    val x by transition.animateFloat(
        initialValue = -SHIMMER_BAND_PX,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing)),
        label = "searchSkeletonX"
    )
    return Brush.linearGradient(
        colors = listOf(SkeletonBase, SkeletonHighlight, SkeletonBase),
        start = Offset(x, 0f),
        end = Offset(x + SHIMMER_BAND_PX, 0f)
    )
}


@Composable
fun SearchSkeletonList(
    modifier: Modifier = Modifier,
    rowCount: Int = 8,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val brush = rememberShimmerBrush()
    LazyColumn(
        modifier = modifier.clearAndSetSemantics { },
        userScrollEnabled = false,
        contentPadding = contentPadding
    ) {
        items((0 until rowCount).toList()) { index ->
            SkeletonRow(brush = brush, index = index)
        }
    }
}

@Composable
private fun SkeletonRow(brush: Brush, index: Int) {
    
    val titleWidth = TITLE_FRACTIONS[index % TITLE_FRACTIONS.size]
    val subtitleWidth = SUBTITLE_FRACTIONS[index % SUBTITLE_FRACTIONS.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Bar(brush, Modifier.size(52.dp), corner = 10.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Bar(brush, Modifier.fillMaxWidth(titleWidth).height(16.dp), corner = 6.dp)
            Spacer(Modifier.height(10.dp))
            Bar(brush, Modifier.fillMaxWidth(subtitleWidth).height(12.dp), corner = 6.dp)
        }
        Bar(brush, Modifier.width(30.dp).height(12.dp), corner = 6.dp)
    }
}

@Composable
private fun Bar(brush: Brush, modifier: Modifier, corner: Dp) {
    Box(modifier = modifier.background(brush, RoundedCornerShape(corner)))
}

private val TITLE_FRACTIONS = listOf(0.7f, 0.55f, 0.8f, 0.6f, 0.75f)
private val SUBTITLE_FRACTIONS = listOf(0.45f, 0.35f, 0.5f, 0.4f)
