package com.njxai.chessintel.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renders chat text with tappable links. Supports:
 *  - Markdown links: [label](https://...)
 *  - Bare URLs:      https://...
 * Everything else is displayed as plain text.
 */
@Composable
fun ChatMessageText(
    text: String,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val baseStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)

    val annotated = remember(text) { buildLinkAnnotated(text, linkColor) }

    ClickableText(
        text = annotated,
        style = baseStyle,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.let { onOpenUrl(it.item) }
        },
    )
}

private fun buildLinkAnnotated(text: String, linkColor: Color): AnnotatedString {
    val regex = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)|(https?://[^\s)\]]+)""")
    return buildAnnotatedString {
        var last = 0
        for (match in regex.findAll(text)) {
            append(text.substring(last, match.range.first))
            val label = match.groupValues[1]
            val url = match.groupValues[2].ifEmpty { match.groupValues[3] }
            pushStringAnnotation("URL", url)
            withStyle(SpanStyle(color = linkColor)) { append(label.ifEmpty { url }) }
            pop()
            last = match.range.last + 1
        }
        append(text.substring(last))
    }
}
