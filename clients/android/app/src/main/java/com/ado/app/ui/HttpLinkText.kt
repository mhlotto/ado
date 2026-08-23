package com.ado.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

data class DetectedLink(
    val start: Int,
    val endExclusive: Int,
    val url: String,
)

private val httpLinkPattern = Regex("""https?://\S+""")
private val alwaysTrimmedTrailingCharacters = setOf('.', ',', ';', ':', '!', '?')

fun detectHttpLinks(text: String): List<DetectedLink> = httpLinkPattern.findAll(text).mapNotNull { match ->
    var endExclusive = match.range.last + 1
    while (endExclusive > match.range.first) {
        val candidate = text.substring(match.range.first, endExclusive)
        val trailing = candidate.last()
        val shouldTrim = trailing in alwaysTrimmedTrailingCharacters || when (trailing) {
            ')' -> candidate.count { it == ')' } > candidate.count { it == '(' }
            ']' -> candidate.count { it == ']' } > candidate.count { it == '[' }
            '}' -> candidate.count { it == '}' } > candidate.count { it == '{' }
            else -> false
        }
        if (!shouldTrim) break
        endExclusive -= 1
    }

    if (endExclusive == match.range.first) {
        null
    } else {
        DetectedLink(
            start = match.range.first,
            endExclusive = endExclusive,
            url = text.substring(match.range.first, endExclusive),
        )
    }
}.toList()

@Composable
fun HttpLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    textDecoration: TextDecoration = TextDecoration.None,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val linkDecoration = if (textDecoration == TextDecoration.None) {
        TextDecoration.Underline
    } else {
        TextDecoration.combine(listOf(TextDecoration.Underline, textDecoration))
    }
    val interactionListener = remember(context) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            openExternalHttpUrl(context, url)
        }
    }
    val annotatedText = buildAnnotatedString {
        append(text)
        detectHttpLinks(text).forEach { link ->
            addLink(
                url = LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = linkDecoration,
                        ),
                    ),
                    linkInteractionListener = interactionListener,
                ),
                start = link.start,
                end = link.endExclusive,
            )
        }
    }

    Text(
        text = annotatedText,
        modifier = modifier,
        style = style,
        textDecoration = textDecoration,
    )
}
