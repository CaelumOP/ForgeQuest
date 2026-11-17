package com.forgequest.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.forgequest.app.R
import com.forgequest.app.ui.theme.Fit

/** The photo to show on Home: the Friends sign-in photo first, else the Drive account photo; blank counts as none. */
internal fun pickAvatarPhoto(friendsPhoto: String?, drivePhoto: String?): String? =
    friendsPhoto?.takeIf { it.isNotBlank() } ?: drivePhoto?.takeIf { it.isNotBlank() }

/** Name for the photo-less Home avatar: Settings name first, else the Friends shownName; blank counts as none. */
internal fun pickAvatarName(settingsName: String, friendsShownName: String?): String =
    settingsName.trim().ifEmpty { friendsShownName?.trim().orEmpty() }

/** First letter of the name for the photo-less avatar; "?" when there is no name. */
internal fun avatarInitial(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/**
 * Home header entry to Profile: the photo (initials when there is none), the rank letter as a small badge on one
 * corner and a gear dot on the other. The tap target is 48dp; the badge and dot are decoration under one label.
 */
@Composable
internal fun ProfileAvatar(name: String, photoUrl: String?, rankMark: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fit.colors
    val label = stringResource(R.string.home41_profile_cd)
    Box(
        // No clip here: the gear dot and rank badge sit on the square's corners, outside a 56dp circle, so a circle clip
        // cut them in half. The ripple stays round through an unbounded 28dp ripple instead.
        modifier.size(56.dp)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 28.dp),
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.tealTrack), contentAlignment = Alignment.Center) {
            Text(avatarInitial(name), style = MaterialTheme.typography.titleMedium, color = c.teal, fontWeight = FontWeight.Bold)
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
        }
        if (rankMark != null) Box(
            Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(c.blueTrack).border(BorderStroke(1.5.dp, c.background), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(rankMark, style = MaterialTheme.typography.labelSmall, color = c.blue, fontWeight = FontWeight.Bold) }
        Box(
            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(c.card).border(BorderStroke(1.dp, c.divider), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Settings, contentDescription = null, tint = c.teal, modifier = Modifier.size(12.dp)) }
    }
}
