package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.qymartin.reinminder.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Text1)
        if (subtitle != null) Text(subtitle, fontSize = 15.sp, color = Text2)
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    background: Color = CardBg,
    border: Color? = null,
    padding: Int = 16,
    spacing: Int = 10,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background)
    if (border != null) m = m.border(1.dp, border, RoundedCornerShape(16.dp))
    Column(m.padding(padding.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
}

@Composable
fun CardTitle(text: String, color: Color = Text1) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
}

@Composable
fun LabelTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Accent)
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = Text2, onClick: (() -> Unit)? = null) {
    val m = Modifier.fillMaxWidth().heightIn(min = 32.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, fontSize = 15.sp, color = Text1, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 15.sp, color = valueColor)
    }
}

@Composable
fun SwitchRow(title: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit, noteColor: Color = Text3) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, color = Text1)
            if (note != null) Text(note, fontSize = 12.sp, color = noteColor, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun Chip(text: String, color: Color = OnTonal, background: Color = TonalBg, border: Color? = null) {
    var m = Modifier.clip(RoundedCornerShape(15.dp)).background(background)
    if (border != null) m = m.border(1.dp, border, RoundedCornerShape(15.dp))
    Box(m.heightIn(min = 30.dp).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = color)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = DividerColor, thickness = 1.dp)
}

/** Round tick box with a 48dp touch area. */
@Composable
fun CheckCircle(done: Boolean, ringColor: Color, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        val m = Modifier.size(22.dp).clip(CircleShape)
        if (done) {
            Box(m.background(Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = contentDescription, tint = OnAccent, modifier = Modifier.size(15.dp))
            }
        } else {
            Box(m.border(2.dp, ringColor, CircleShape))
        }
    }
}

/**
 * One event line, sized like the Schedule list (title 15sp, details 13sp, time 14sp): tick box, title + subtitle, time on the right.
 * [timeColor] highlights overdue/soon items.
 */
@Composable
fun EventLine(
    title: String,
    meta: String,
    time: String,
    done: Boolean,
    ringColor: Color,
    timeColor: Color,
    checkLabel: String,
    onCheck: (() -> Unit)?,
    onClick: () -> Unit,
    selected: Boolean = false,
    important: Boolean = false,
    strike: Boolean = done,
    parent: String? = null,
    checkEnabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) TonalBg else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(end = 14.dp, start = if (onCheck == null) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onCheck != null) CheckCircle(done, if (checkEnabled) ringColor else DividerColor, checkLabel, onCheck, enabled = checkEnabled)
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TitleText(title, important, muted = done, strike = strike, fontSize = 15, parent = parent)
            if (meta.isNotEmpty()) Text(meta, fontSize = 13.sp, color = Text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(time, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (done) Muted else timeColor)
    }
}

/** Event title; important events get an amber star in front. */
@Composable
fun TitleText(title: String, important: Boolean, muted: Boolean = false, strike: Boolean = false, fontSize: Int = 15, parent: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (important) {
            Icon(
                Icons.Filled.Star, contentDescription = stringResource(R.string.important),
                tint = if (muted) Muted else Amber, modifier = Modifier.size((fontSize + 2).dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            title, fontSize = fontSize.sp, fontWeight = FontWeight.Medium,
            color = if (muted) Muted else Text1,
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // A subtask names its main task after its own title, in the accent colour.
        if (parent != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                parent, fontSize = (fontSize - 3).sp, color = if (muted) Muted else Accent,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
    }
}
