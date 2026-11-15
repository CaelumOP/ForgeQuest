package com.forgequest.app.ui.friends

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.forgequest.app.R
import com.forgequest.app.data.friends.FriendsUiData
import com.forgequest.app.data.friends.Me
import com.forgequest.app.data.friends.Nudge
import com.forgequest.app.data.friends.Person
import com.forgequest.app.data.friends.findActivity
import com.forgequest.app.domain.FriendBadge
import com.forgequest.app.domain.LeaderboardEntry
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.FitSwitch
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.MorphLoader
import com.forgequest.app.ui.components.SectionTitle
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.components.rememberTextClipboard
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.NumberFont
import com.forgequest.app.ui.theme.tabular
import com.forgequest.app.data.friends.Usernames
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen() {
    val vm = appViewModel { FriendsViewModel(it) }
    val uiData by vm.state.collectAsState()
    val signedIn by vm.signedIn.collectAsState()
    val me by vm.me.collectAsState()
    val error by vm.error.collectAsState()
    val nudgedToday by vm.nudgedToday.collectAsState()
    val settings by vm.settings.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val busy by vm.busy.collectAsState()

    val c = Fit.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFriend by remember { mutableStateOf<LeaderboardEntry?>(null) }

    LaunchedEffect(signedIn) {
        if (signedIn || vm.isDemo) vm.refresh()
    }

    val activeDemo = vm.isDemo
    val showJoined = uiData != null && (signedIn || activeDemo)
    val showJoinForm = signedIn && !activeDemo && uiData == null && me?.joined != true
    val showLoadingJoined = signedIn && !activeDemo && uiData == null && me?.joined == true

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = ForgeSpace.Gutter,
                end = ForgeSpace.Gutter,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ForgeSpace.CardGap),
        ) {
            item {
                Text(
                    text = stringResource(R.string.friends_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics { heading() },
                )
            }

            error?.let { code ->
                item {
                    Text(
                        text = stringResource(R.string.friends_error_line, code),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            when {
                !signedIn && !activeDemo -> {
                    item {
                        SignedOutCard(
                            busy = busy,
                            onSignIn = { context.findActivity()?.let(vm::signIn) },
                        )
                    }
                }

                showJoinForm -> {
                    item {
                        JoinFormCard(
                            suggestedName = settings?.name?.takeIf { it.isNotBlank() } ?: me?.name.orEmpty(),
                            busy = busy,
                            onJoin = vm::join,
                        )
                    }
                }

                showLoadingJoined -> {
                    item {
                        FitCard {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                MorphLoader(com.forgequest.app.ui.theme.Fit.colors.teal, Modifier.size(32.dp))
                            }
                        }
                    }
                }

                showJoined -> {
                    val data = uiData!!
                    item {
                        InviteHeaderCard(
                            me = data.view.me,
                            busy = busy,
                            onSendInvite = { username -> vm.invite(username) },
                        )
                    }

                    if (data.view.nudges.isNotEmpty()) {
                        items(data.view.nudges, key = { "nudge_${it.fromId}_${it.day}" }) { nudge ->
                            NudgeBanner(nudge)
                        }
                    }

                    if (data.view.incoming.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.friends_incoming_title)) }
                        items(data.view.incoming, key = { "in_${it.id}" }) { person ->
                            IncomingRequestCard(
                                person = person,
                                busy = busy,
                                onAccept = { vm.accept(person.id) },
                                onDecline = { vm.decline(person.id) },
                            )
                        }
                    }

                    item { SectionTitle(stringResource(R.string.friends_board_title)) }

                    itemsIndexed(data.leaderboard, key = { _, e -> "lb_${e.id}_${e.isMe}" }) { index, entry ->
                        LeaderboardRow(
                            rank = index + 1,
                            entry = entry,
                            onClick = if (entry.isMe) null else ({ selectedFriend = entry }),
                        )
                    }

                    if (data.view.friends.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.friends_empty_board),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }

                    if (data.view.outgoing.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.friends_outgoing_title)) }
                        items(data.view.outgoing, key = { "out_${it.id}" }) { person ->
                            OutgoingRow(person)
                        }
                    }
                }
            }
        }
    }

    selectedFriend?.let { entry ->
        val unit = WeightUnit.fromCodeOrLabel(settings?.unit ?: WeightUnit.KG.code)
        FriendDetailSheet(
            entry = entry,
            unit = unit,
            nudgedToday = entry.id in nudgedToday,
            busy = busy,
            onNudge = { vm.nudge(entry.id) },
            onRemove = {
                selectedFriend = null
                scope.launch { vm.remove(entry.id) }
            },
            onBlock = {
                selectedFriend = null
                scope.launch { vm.block(entry.id) }
            },
            onDismiss = { selectedFriend = null },
        )
    }
}

@Composable
private fun SignedOutCard(busy: Boolean, onSignIn: () -> Unit) {
    val c = Fit.colors
    FitCard {
        IconCircle(Icons.Rounded.Group, c.teal, c.tealTrack, 52.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.friends_pitch_title),
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.friends_pitch_body),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onSignIn,
            enabled = !busy,
            shape = ForgeShape.Full,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.friends_sign_in_google),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun JoinFormCard(
    suggestedName: String,
    busy: Boolean,
    onJoin: (String, String, Boolean, Boolean) -> Unit,
) {
    val c = Fit.colors
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var sharePhoto by rememberSaveable { mutableStateOf(true) }
    var sharePrs by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(suggestedName) {
        if (!nameEdited && name.isEmpty() && suggestedName.isNotBlank()) {
            name = suggestedName.trim().take(40)
        }
    }

    val valid = name.trim().length in 1..40 && Usernames.normalize(username) != null

    FitCard {
        Text(
            text = stringResource(R.string.friends_join_title),
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.friends_join_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = name,
            onValueChange = {
                nameEdited = true
                name = it.take(40)
            },
            label = { Text(stringResource(R.string.friends_name_label)) },
            placeholder = { Text(stringResource(R.string.friends_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            shape = ForgeShape.Md,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.teal,
                unfocusedBorderColor = c.divider,
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                cursorColor = c.teal,
                focusedLabelColor = c.teal,
                unfocusedLabelColor = c.textSecondary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        UsernameField(value = username, onValueChange = { username = it }, enabled = !busy)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = sharePhoto, role = Role.Switch) { sharePhoto = it }
                .heightIn(min = 48.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.friends_share_photo),
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            FitSwitch(sharePhoto, null)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = sharePrs, role = Role.Switch) { sharePrs = it }
                .heightIn(min = 48.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.friends_share_prs),
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            FitSwitch(sharePrs, null)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onJoin(name.trim(), username, sharePhoto, sharePrs) },
            enabled = valid && !busy,
            shape = ForgeShape.Full,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.friends_join_button),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteHeaderCard(
    me: Me,
    busy: Boolean,
    onSendInvite: suspend (String) -> String?,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val clipboard = rememberTextClipboard()
    val scope = rememberCoroutineScope()
    val handle = "@${me.username.orEmpty()}"
    val copiedMsg = stringResource(R.string.friends_username_copied)
    val shareMsg = stringResource(R.string.friends_share_message, me.username.orEmpty(), Usernames.SHARE_URL)
    val sentMsg = stringResource(R.string.friends_invite_sent)
    var nameInput by rememberSaveable { mutableStateOf("") }
    val canSend = Usernames.normalize(nameInput) != null && !busy

    fun submitInvite() {
        if (!canSend) return
        scope.launch {
            val err = onSendInvite(nameInput)
            if (err == null) {
                nameInput = ""
                context.toast(sentMsg)
            }
        }
    }

    FitCard {
        Text(
            text = stringResource(R.string.friends_your_code),
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = handle,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TonalPill(
                text = stringResource(R.string.friends_copy_code),
                icon = Icons.Rounded.ContentCopy,
                onClick = {
                    clipboard.copy(handle)
                    context.toast(copiedMsg)
                },
            )
            TonalPill(
                text = stringResource(R.string.friends_share_code),
                icon = Icons.Rounded.Share,
                onClick = { shareInviteText(context, shareMsg) },
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = c.divider)
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { raw -> nameInput = raw.filterNot { it.isWhitespace() }.take(Usernames.MAX_INPUT) },
                label = { Text(stringResource(R.string.friends_add_label)) },
                placeholder = { Text(stringResource(R.string.friends_add_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { submitInvite() }),
                shape = ForgeShape.Md,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.teal,
                    unfocusedBorderColor = c.divider,
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary,
                    cursorColor = c.teal,
                    focusedLabelColor = c.teal,
                    unfocusedLabelColor = c.textSecondary,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { submitInvite() },
                enabled = canSend,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.friends_add_button),
                    color = if (canSend) c.teal else c.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun NudgeBanner(nudge: Nudge) {
    val c = Fit.colors
    Surface(
        shape = ForgeShape.Md,
        color = c.tealTrack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ForgeSpace.CardPaddingCompact, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = c.teal,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.friends_nudged_you, nudge.name),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomingRequestCard(
    person: Person,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val c = Fit.colors
    FitCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FriendAvatar(person.name, person.photoUrl, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                person.username?.let { HandleText(it) }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = onAccept,
                enabled = !busy,
                shape = ForgeShape.Full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Text(stringResource(R.string.friends_accept), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = onDecline,
                enabled = !busy,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Text(stringResource(R.string.friends_decline), color = c.textSecondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    onClick: (() -> Unit)?,
) {
    val c = Fit.colors
    Surface(
        shape = ForgeShape.Lg,
        color = if (entry.isMe) c.cardHigh else c.card,
        border = if (entry.isMe) BorderStroke(1.dp, c.teal.copy(alpha = 0.55f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ForgeShape.Lg)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ForgeSpace.CardPaddingCompact, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = NumberFont).tabular(),
                color = if (entry.isMe) c.teal else c.textSecondary,
                modifier = Modifier.defaultMinSize(minWidth = 22.dp),
            )
            Spacer(Modifier.width(10.dp))
            FriendAvatar(entry.name, entry.photoUrl, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (entry.isMe) {
                        "${entry.name} · ${stringResource(R.string.friends_me_tag)}"
                    } else {
                        entry.name
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    BadgeChip(entry.badge)
                    if (entry.syncedThisWeek) {
                        StatMini(
                            icon = Icons.Rounded.LocalFireDepartment,
                            tint = c.gold,
                            text = stringResource(R.string.friends_stat_streak, entry.streakWeeks),
                        )
                        StatMini(
                            icon = Icons.Rounded.Restaurant,
                            tint = c.teal,
                            text = stringResource(R.string.friends_stat_protein, entry.proteinDays),
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = entry.score.toString(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = NumberFont,
                    fontWeight = FontWeight.SemiBold,
                ).tabular(),
                color = if (entry.syncedThisWeek) c.textPrimary else c.textSecondary,
            )
        }
    }
}

@Composable
private fun StatMini(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String,
) {
    val c = Fit.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = NumberFont).tabular(),
            color = c.textSecondary,
        )
    }
}

@Composable
private fun BadgeChip(badge: FriendBadge) {
    val c = Fit.colors
    val (labelRes, fg, bg) = when (badge) {
        FriendBadge.TRAINED -> Triple(R.string.friends_badge_trained, c.teal, c.tealTrack)
        FriendBadge.ON_TRACK -> Triple(R.string.friends_badge_on_track, c.blue, c.blueTrack)
        FriendBadge.SLACKING -> Triple(R.string.friends_badge_slacking, c.gold, c.gold.copy(alpha = 0.14f))
        FriendBadge.REST -> Triple(R.string.friends_badge_rest, c.textSecondary, c.divider.copy(alpha = 0.55f))
        FriendBadge.NOT_SYNCED -> Triple(R.string.friends_badge_not_synced, c.textSecondary, c.background)
    }
    Surface(
        shape = ForgeShape.Full,
        color = bg,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun FriendAvatar(name: String, photoUrl: String?, size: Dp) {
    val c = Fit.colors
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.tealTrack),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleSmall,
            color = c.teal,
        )
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun OutgoingRow(person: Person) {
    val c = Fit.colors
    Surface(
        shape = ForgeShape.Md,
        color = c.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ForgeSpace.CardPaddingCompact, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FriendAvatar(person.name, person.photoUrl, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                person.username?.let { HandleText(it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FriendDetailSheet(
    entry: LeaderboardEntry,
    unit: WeightUnit,
    nudgedToday: Boolean,
    busy: Boolean,
    onNudge: suspend () -> String?,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sentMsg = stringResource(R.string.friends_nudge_sent)
    val alreadyMsg = stringResource(R.string.friends_nudged_today)
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var alreadyNudgedNote by remember(entry.id) { mutableStateOf(false) }
    val nudgeDisabled = nudgedToday || alreadyNudgedNote || busy

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        containerColor = c.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = ForgeSpace.Gutter)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(entry.name, entry.photoUrl, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BadgeChip(entry.badge)
                        if (entry.syncedThisWeek) {
                            StatMini(
                                icon = Icons.Rounded.LocalFireDepartment,
                                tint = c.gold,
                                text = stringResource(R.string.friends_stat_streak, entry.streakWeeks),
                            )
                            StatMini(
                                icon = Icons.Rounded.Restaurant,
                                tint = c.teal,
                                text = stringResource(R.string.friends_stat_protein, entry.proteinDays),
                            )
                        }
                    }
                }
                Text(
                    text = entry.score.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = NumberFont,
                        fontWeight = FontWeight.SemiBold,
                    ).tabular(),
                    color = if (entry.syncedThisWeek) c.textPrimary else c.textSecondary,
                )
            }

            HorizontalDivider(color = c.divider)

            Text(
                text = stringResource(R.string.friends_prs_title),
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
            )
            if (entry.prs.isEmpty()) {
                Text(
                    text = stringResource(R.string.friends_prs_hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.prs.forEach { pr ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = pr.exercise,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            val prVal = if (pr.kg > 0.0) {
                                stringResource(R.string.finish_value_weight_reps, unit.format(pr.kg.toFloat()), pr.reps)
                            } else {
                                pluralStringResource(R.plurals.debrief_n_reps, pr.reps, pr.reps)
                            }
                            Text(
                                text = prVal,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NumberFont).tabular(),
                                color = c.teal,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        val code = onNudge()
                        when (code) {
                            null -> context.toast(sentMsg)
                            "already_nudged" -> alreadyNudgedNote = true
                        }
                    }
                },
                enabled = !nudgeDisabled,
                shape = ForgeShape.Full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(
                        if (nudgedToday || alreadyNudgedNote) R.string.friends_nudged_today else R.string.friends_nudge,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (alreadyNudgedNote) {
                Text(
                    text = alreadyMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { confirmRemove = true },
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.friends_remove), color = c.textSecondary)
                }
                TextButton(
                    onClick = { confirmBlock = true },
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.friends_block), color = c.coral)
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.friends_remove_confirm_title), color = c.textPrimary) },
            text = { Text(stringResource(R.string.friends_remove_confirm_body, entry.name), color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) {
                    Text(stringResource(R.string.friends_remove), color = c.coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.friends_cancel), color = c.textSecondary)
                }
            },
        )
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.friends_block_confirm_title), color = c.textPrimary) },
            text = { Text(stringResource(R.string.friends_block_confirm_body, entry.name), color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    onBlock()
                }) {
                    Text(stringResource(R.string.friends_block), color = c.coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) {
                    Text(stringResource(R.string.friends_cancel), color = c.textSecondary)
                }
            },
        )
    }
}

/** "@name" under a display name; forced LTR so the @ stays in front in Arabic. */
@Composable
private fun HandleText(username: String) {
    Text(
        text = "\u2066@$username\u2069",
        style = MaterialTheme.typography.bodySmall,
        color = Fit.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Username input with live format check (same rules as the Worker); shared by Join and Settings. */
@Composable
fun UsernameField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val c = Fit.colors
    val invalid = value.isNotBlank() && Usernames.normalize(value) == null
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filterNot { it.isWhitespace() }.take(Usernames.MAX_INPUT)) },
        label = { Text(stringResource(R.string.friends_username_label)) },
        placeholder = { Text("@") },
        supportingText = {
            Text(stringResource(if (invalid) R.string.friends_username_rules else R.string.friends_username_hint))
        },
        isError = invalid,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        shape = ForgeShape.Md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.teal,
            unfocusedBorderColor = c.divider,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            cursorColor = c.teal,
            focusedLabelColor = c.teal,
            unfocusedLabelColor = c.textSecondary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun shareInviteText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
