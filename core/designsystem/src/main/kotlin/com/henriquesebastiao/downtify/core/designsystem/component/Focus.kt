package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Focus for a D-pad (a TV remote) or a keyboard. A touch screen never moves focus onto buttons or
 * rows, so none of this shows on a phone used by touch.
 */

/** How thick the ring around what has the D-pad's focus is: visible from across a room. */
val FocusRingWidth: Dp = 3.dp

/**
 * Draws a ring of [color] along [shape], over the edge of the content, while the focusable that
 * follows this modifier (a clickable, a button's own) has focus. Put it before that focusable, on
 * the same element. A child of it having focus (a row's "more" button) doesn't light the ring.
 */
fun Modifier.focusRing(shape: Shape, color: Color, width: Dp = FocusRingWidth): Modifier =
    this then FocusRingElement(shape, color, width)

/** The ring of [focusRing] for a [focused] state the caller tracks (a card whose cover takes it). */
fun Modifier.focusRing(focused: Boolean, shape: Shape, color: Color, width: Dp = FocusRingWidth): Modifier =
    if (focused) border(width, color, shape) else this

/**
 * Keeps the D-pad's focus inside while [active]: an overlay (Now Playing, the queue, the side menu)
 * over content that is still laid out and focusable under it. Moving focus away from inside it
 * stops at its edge; leaving is the overlay's own job (its close button, back).
 */
fun Modifier.focusTrap(active: Boolean): Modifier = this
    .focusProperties { if (active) onExit = { cancelFocusChange() } }
    .focusGroup()

/**
 * Asks for focus after the next frame: something just composed (an overlay that opened, a row
 * that came back under a new key) isn't placed yet, and focus only goes to what is. On a
 * [focusGroup] it lands on the group's first focusable. Under touch a button can't take focus, so
 * on a phone this changes nothing.
 */
suspend fun FocusRequester.requestFocusWhenPlaced() {
    withFrameNanos { }
    requestFocus()
}

private data class FocusRingElement(val shape: Shape, val color: Color, val width: Dp) :
    ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(shape, color, width)

    override fun update(node: FocusRingNode) {
        node.shape = shape
        node.color = color
        node.width = width
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusRing"
        properties["shape"] = shape
        properties["color"] = color
        properties["width"] = width
    }
}

private class FocusRingNode(var shape: Shape, var color: Color, var width: Dp) :
    Modifier.Node(),
    FocusEventModifierNode,
    DrawModifierNode {
    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focused != focusState.isFocused) {
            focused = focusState.isFocused
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val stroke = width.toPx()
        // Inset by half the stroke so the whole ring stays inside the element's bounds.
        val inset = stroke / 2
        val outline = shape.createOutline(Size(size.width - stroke, size.height - stroke), layoutDirection, this)
        translate(inset, inset) { drawOutline(outline, color, style = Stroke(stroke)) }
    }
}
