package kr.co.donghyun.pinglauncher.presentation.ui.components

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.donghyun.pinglauncher.data.key.KeyButton
import kr.co.donghyun.pinglauncher.data.key.KeyLayoutManager
import kr.co.donghyun.pinglauncher.presentation.MinecraftActivity
import kr.co.donghyun.pinglauncher.presentation.util.MinecraftActivityBridge

private const val GLFW_PRESS = 1
private const val GLFW_RELEASE = 0

private val CtrlBg = Color(0xFF1A0A14).copy(alpha = 0.85f)
private val CtrlBorderNormal = Color(0xFF7A2855)
private val CtrlPressed = Color(0xFF9C1060)
private val CtrlAccentBg = Color(0xFF6B0040).copy(alpha = 0.85f)
private val CtrlAccentPress = Color(0xFFE91E8C)
private val CtrlBorderAccent = Color(0xFFFF6BB5)

@Composable
fun GameControllerOverlay() {
    val activity = LocalActivity.current as MinecraftActivity
    val context = LocalContext.current
    val buttons = remember { KeyLayoutManager.load(context) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { canvasSize = it.size }
    ) {
        if (canvasSize != IntSize.Zero) {
            buttons.forEach { button ->
                OverlayKeyButton(
                    button = button,
                    canvasSize = canvasSize,
                    onPress = { handleKeyPress(activity, button.glfwCode, GLFW_PRESS) },
                    onRelease = { handleKeyPress(activity, button.glfwCode, GLFW_RELEASE) }
                )
            }
        }
    }
}

@Composable
fun OverlayKeyButton(
    button: KeyButton,
    canvasSize: IntSize,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }

    val xDp = with(density) { (button.x * canvasSize.width).toDp() }
    val yDp = with(density) { (button.y * canvasSize.height).toDp() }
    val wDp = button.width.dp
    val hDp = button.height.dp

    val bgColor = when {
        pressed && button.isAccent -> CtrlAccentPress
        pressed -> CtrlPressed
        button.isAccent -> CtrlAccentBg
        else -> CtrlBg
    }
    val borderColor = if (button.isAccent) CtrlBorderAccent else CtrlBorderNormal

    Box(modifier = Modifier.offset(x = xDp, y = yDp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(wDp)
                .height(hDp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(
                    width = if (pressed) 2.dp else 1.dp,
                    color = borderColor.copy(alpha = if (pressed) 1f else 0.7f),
                    shape = RoundedCornerShape(10.dp)
                )
                .pointerInput(button.id) {
                    awaitEachGesture {
                        awaitFirstDown().also {
                            it.consume()
                            pressed = true
                            onPress()
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) {
                                pressed = false
                                onRelease()
                                break
                            }
                        }
                    }
                }
        ) {
            Text(
                text = button.label,
                color = Color.White,
                fontSize = (minOf(wDp.value, hDp.value) * 0.28f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

private fun handleKeyPress(activity: MinecraftActivity, glfwCode: Int, action: Int) {
    when {
        glfwCode >= 0 -> activity.sendKey(glfwCode, action)
        glfwCode == -1 -> activity.sendMouseButton(0, action)
        glfwCode == -2 -> activity.sendMouseButton(1, action)
        glfwCode == -3 -> activity.sendMouseButton(2, action)
        glfwCode == -4 && action == GLFW_PRESS -> {
            val prev = (MinecraftActivityBridge.currentHotbarSlot - 1 + 9) % 9
            MinecraftActivityBridge.currentHotbarSlot = prev
            activity.sendKey(49 + prev, GLFW_PRESS)
            activity.sendKey(49 + prev, GLFW_RELEASE)
        }
        glfwCode == -5 && action == GLFW_PRESS -> {
            val next = (MinecraftActivityBridge.currentHotbarSlot + 1) % 9
            MinecraftActivityBridge.currentHotbarSlot = next
            activity.sendKey(49 + next, GLFW_PRESS)
            activity.sendKey(49 + next, GLFW_RELEASE)
        }
    }
}