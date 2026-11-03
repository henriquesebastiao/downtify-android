package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * One of a few options as a connected button group: the selected button filled, the group's ends
 * rounded, every button as wide as the others. A long label steps down rather than wrapping.
 * [role] is what the buttons are to accessibility: radio buttons for a setting, tabs for two views
 * of one screen. On a card, give the unselected buttons a [containerColor] a step above it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val colors = ToggleButtonDefaults.colors(containerColor = containerColor)
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            val text = label(option)
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = colors,
                contentPadding = PaddingValues(horizontal = Spacing.md),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clearAndSetSemantics {
                        this.role = role
                        this.selected = checked
                        this.text = AnnotatedString(text)
                        onClick {
                            onSelect(option)
                            true
                        }
                    },
            ) {
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = LabelAutoSize)
            }
        }
    }
}

// "Системна" or "Rendszer" in a third of a 360 dp card: smaller rather than cut.
private val LabelAutoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp, stepSize = 1.sp)

@PhonePreviews
@Composable
private fun ChoiceGroupPreview() {
    DowntifyTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg)) {
                var choice by remember { mutableStateOf("System") }
                ChoiceGroup(listOf("System", "Light", "Dark"), choice, { it }, { choice = it })
            }
        }
    }
}
