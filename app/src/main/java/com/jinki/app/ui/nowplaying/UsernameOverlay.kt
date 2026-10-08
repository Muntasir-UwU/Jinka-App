package com.jinki.app.ui.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val UoAmber = Color(0xFFB5761F)
private val UoPlum = Color(0xFF190F1B)
private val UoCream = Color(0xFFFFF8E7)

private const val MAX_NAME = 20

/**
 * Full-screen, non-dismissible "what should we call you?" overlay.
 * Draw it AFTER (on top of) the screen it covers.
 */
@Composable
fun UsernameOverlay(
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    errorText: String? = null,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val canSubmit = trimmed.isNotEmpty() && !isSaving

    // No way out without a name: swallow back presses and taps behind.
    BackHandler(enabled = true) { }

    Box(
        modifier
            .fillMaxSize()
            .background(UoPlum.copy(alpha = 0.94f))
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = UoCream.copy(alpha = 0.07f),
            border = BorderStroke(1.dp, UoCream.copy(alpha = 0.14f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Live initial badge
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(UoAmber.copy(alpha = if (trimmed.isEmpty()) 0.25f else 1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = trimmed.firstOrNull()?.uppercase() ?: "♪",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = UoCream,
                    )
                }

                Spacer8()

                Text(
                    "What should we call you?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    color = UoCream,
                )
                Text(
                    "Others will see this name and what you're playing.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = UoCream.copy(alpha = 0.65f),
                )

                Spacer8()

                OutlinedTextField(
                    value = name,
                    onValueChange = { new ->
                        if (new.length <= MAX_NAME) name = new.replace("\n", "")
                    },
                    singleLine = true,
                    enabled = !isSaving,
                    placeholder = { Text("Your name", color = UoCream.copy(alpha = 0.4f)) },
                    supportingText = {
                        Text(
                            errorText ?: "${trimmed.length}/$MAX_NAME",
                            color = if (errorText != null) MaterialTheme.colorScheme.error
                            else UoCream.copy(alpha = 0.5f),
                        )
                    },
                    isError = errorText != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canSubmit) onSubmit(trimmed) }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = UoCream,
                        unfocusedTextColor = UoCream,
                        disabledTextColor = UoCream.copy(alpha = 0.5f),
                        cursorColor = UoAmber,
                        focusedBorderColor = UoAmber,
                        unfocusedBorderColor = UoCream.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = { onSubmit(trimmed) },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UoAmber,
                        contentColor = UoCream,
                        disabledContainerColor = UoCream.copy(alpha = 0.12f),
                        disabledContentColor = UoCream.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = UoCream,
                        )
                    } else {
                        Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))