package com.forgequest.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radii of the Soft Coach design system (see DESIGN.md at the repo root). */
object ForgeShape {
    val SmRadius = 12.dp
    val MdRadius = 20.dp
    val LgRadius = 28.dp

    val Sm = RoundedCornerShape(SmRadius)
    val Md = RoundedCornerShape(MdRadius)
    val Lg = RoundedCornerShape(LgRadius)
    val Full = CircleShape
}

/** Spacing scale of the Soft Coach design system (see DESIGN.md at the repo root). */
object ForgeSpace {
    val Xxs = 4.dp
    val Xs = 8.dp
    val Sm = 12.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 48.dp
    val Gutter = 20.dp
    val CardPadding = 24.dp
    val CardGap = 16.dp
}
