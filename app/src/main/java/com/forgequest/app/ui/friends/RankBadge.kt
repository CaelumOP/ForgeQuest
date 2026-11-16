package com.forgequest.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgequest.app.R
import com.forgequest.app.domain.FriendsLeaderboard
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.NumberFont
import com.forgequest.app.ui.theme.tabular
import java.util.Locale

private data class RankBadgeStyle(
    val container: Color,
    val border: Color,
    val text: Color,
)

@Composable
private fun rankBadgeStyle(rank: Int?): RankBadgeStyle {
    val c = Fit.colors
    val dark = c.isDark
    return when (rank) {
        1 -> RankBadgeStyle(
            container = if (dark) Color(0xFF2A2519) else Color(0xFFF8F2DF),
            border = if (dark) Color(0xFF5C4D2A) else Color(0xFFD8C38A),
            text = if (dark) Color(0xFFE8CA74) else Color(0xFF785400),
        )
        2 -> RankBadgeStyle(
            container = if (dark) Color(0xFF2B2D30) else Color(0xFFEDEFF3),
            border = if (dark) Color(0xFF52565E) else Color(0xFFC7CDD6),
            text = if (dark) Color(0xFFC8CED6) else Color(0xFF4E545C),
        )
        3 -> RankBadgeStyle(
            container = if (dark) Color(0xFF2D221C) else Color(0xFFF6EAE2),
            border = if (dark) Color(0xFF5D4232) else Color(0xFFD7B49E),
            text = if (dark) Color(0xFFDFA37D) else Color(0xFF844924),
        )
        else -> RankBadgeStyle(
            container = c.cardHigh,
            border = c.divider,
            text = c.textSecondary,
        )
    }
}

/** Full accessible phrase for a rank badge (e.g. "2nd of 4" or "المركز 2 من 4"). */
@Composable
fun rankContentDescription(rank: Int, total: Int, isArabic: Boolean): String {
    val rankStr = if (isArabic) rank.toString() else FriendsLeaderboard.ordinal(rank)
    return stringResource(R.string.friends_rank_cd, rankStr, total)
}

/**
 * Compact pill/circle rank badge. English renders bold digits + raised uppercase ordinal suffix;
 * Arabic renders Latin digits only. Top 3 get subtle medal tints; 4th+ and unsynced are neutral.
 */
@Composable
fun RankBadge(
    rank: Int?,
    total: Int,
    modifier: Modifier = Modifier,
    includeSemantics: Boolean = true,
) {
    val style = rankBadgeStyle(rank)
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val cd = when {
        !includeSemantics -> null
        rank != null && total > 0 -> rankContentDescription(rank, total, isArabic)
        else -> stringResource(R.string.friends_badge_not_synced)
    }
    val semanticsMod = if (cd != null) {
        Modifier.clearAndSetSemantics { contentDescription = cd }
    } else {
        Modifier.clearAndSetSemantics {}
    }

    val minWidth = if (isArabic) 32.dp else 42.dp
    val horizontalPad = if (isArabic) 6.dp else 8.dp
    val displayText = remember(rank, isArabic) {
        buildAnnotatedString {
            if (rank == null) {
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) {
                    append("–")
                }
            } else if (isArabic) {
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)) {
                    append(rank.toString())
                }
            } else {
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)) {
                    append(rank.toString())
                }
                withStyle(
                    SpanStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        baselineShift = BaselineShift(0.30f),
                    ),
                ) {
                    append(FriendsLeaderboard.ordinalSuffix(rank).uppercase(Locale.ROOT))
                }
            }
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = 32.dp)
            .clip(ForgeShape.Full)
            .background(style.container)
            .border(1.dp, style.border, ForgeShape.Full)
            .padding(horizontal = horizontalPad, vertical = 4.dp)
            .then(semanticsMod),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = NumberFont,
                lineHeight = 16.sp,
            ).tabular(),
            color = style.text,
            maxLines = 1,
            softWrap = false,
        )
    }
}
