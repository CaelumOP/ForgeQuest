package com.forgequest.app.ui.friends

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
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
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(nav: NavHostController) {
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
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var outOpen by rememberSaveable { mutableStateOf(false) }
    var dismissedNudges by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val sentMsg = stringResource(R.string.friends_nudge_sent)
    val nudge: (String) -> Unit = { id -> scope.launch { if (vm.nudge(id) == null) context.toast(sentMsg) } }

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
                // Title with Add and Settings beside it; refresh is pull-to-refresh.
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.friends_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (showJoined) {
                        IconButton(onClick = { addOpen = true }, modifier = Modifier.size(48.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.friends_add_title), tint = c.teal)
                            }
                        }
                        IconButton(onClick = { nav.navigate("settings/friends") }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.friends_settings), tint = c.textSecondary)
                        }
                    }
                }
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
                                MorphLoader(c.teal, Modifier.size(32.dp))
                            }
                        }
                    }
                }

                showJoined -> {
                    val data = uiData!!
                    val nudges = data.view.nudges.filter { "${it.fromId}_${it.day}" !in dismissedNudges }
                    items(nudges, key = { "nudge_${it.fromId}_${it.day}" }) { n ->
                        NudgeBanner(n, onDismiss = { dismissedNudges = dismissedNudges + "${n.fromId}_${n.day}" })
                    }
                    items(data.view.incoming, key = { "in_${it.id}" }) { person ->
                        IncomingRequestCard(
                            person = person,
                            busy = busy,
                            onAccept = { vm.accept(person.id) },
                            onDecline = { vm.decline(person.id) },
                        )
                    }

                    if (data.view.friends.isEmpty()) {
                        item { EmptyFriends(data.view.me, busy, onSendInvite = { vm.invite(it) }) }
                    } else {
                        item { WeekLine(data) }
                        item {
                            FitCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                data.leaderboard.forEachIndexed { index, entry ->
                                    if (index > 0 && !entry.isMe && !data.leaderboard[index - 1].isMe) HorizontalDivider(color = c.divider)
                                    LeaderboardRow(
                                        rank = index + 1,
                                        entry = entry,
                                        nudged = entry.id in nudgedToday,
                                        busy = busy,
                                        onOpen = { selectedId = entry.id },
                                        onNudge = { nudge(entry.id) },
                                    )
                                }
                            }
                        }
                    }

                    if (data.view.outgoing.isNotEmpty()) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(ForgeShape.Sm)
                                    .clickable(role = Role.Button) { outOpen = !outOpen }
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    pluralStringResource(R.plurals.friends_invites_waiting, data.view.outgoing.size, data.view.outgoing.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    if (outOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = c.textSecondary,
                                )
                            }
                        }
                        if (outOpen) {
                            items(data.view.outgoing, key = { "out_${it.id}" }) { person ->
                                OutgoingRow(person, busy, onCancel = { vm.decline(person.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (addOpen && uiData != null) {
        ModalBottomSheet(
            onDismissRequest = { addOpen = false },
            sheetState = rememberFullSheetState(),
            containerColor = c.card,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = ForgeSpace.Gutter)
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    stringResource(R.string.friends_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
                )
                InviteHeaderCard(
                    me = uiData!!.view.me,
                    busy = busy,
                    onSendInvite = { username -> vm.invite(username).also { if (it == null) addOpen = false } },
                )
            }
        }
    }

    val selected = selectedId?.let { id -> uiData?.leaderboard?.firstOrNull { it.id == id && !it.isMe } }
    selected?.let { entry ->
        val unit = WeightUnit.fromCodeOrLabel(settings?.unit ?: WeightUnit.KG.code)
        val mine = uiData?.mySnapshot
        val myWeekState = mine?.takeIf { s -> s.weekStart >= (uiData?.view?.weekStart ?: 0L) }?.weekState
        FriendDetailSheet(
            entry = entry,
            myWeekState = myWeekState,
            unit = unit,
            nudgedToday = entry.id in nudgedToday,
            busy = busy,
            onNudge = { vm.nudge(entry.id) },
            onRemove = {
                selectedId = null
                scope.launch { vm.remove(entry.id) }
            },
            onBlock = {
                selectedId = null
                scope.launch { vm.block(entry.id) }
            },
            onDismiss = { selectedId = null },
        )
    }
}

/** "This week · 3 days left" and "You're #2 of 5": one line of text, no card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekLine(data: FriendsUiData) {
    val c = Fit.colors
    val today = maxOf(data.view.today, com.forgequest.app.domain.Dates.today())
    val daysLeft = (data.view.weekStart + 6 - today).coerceAtLeast(0L).toInt()
    val rank = data.leaderboard.indexOfFirst { it.isMe } + 1
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row {
            Text(stringResource(R.string.friends_board_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(
                " · " + if (daysLeft == 0) stringResource(R.string.friends_last_day) else pluralStringResource(R.plurals.friends_days_left, daysLeft, daysLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
        if (rank > 0) {
            Text(
                stringResource(R.string.friends_your_rank, rank, data.leaderboard.size),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
    }
}

/** No friends yet: one encouraging line, then your username to share and the add field. */
@Composable
private fun EmptyFriends(me: Me, busy: Boolean, onSendInvite: suspend (String) -> String?) {
    val c = Fit.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        IconCircle(Icons.Rounded.Group, c.teal, c.tealTrack, size = 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.friends_empty_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.friends_empty_board),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        InviteHeaderCard(me = me, busy = busy, onSendInvite = onSendInvite)
    }
}

@Composable
private fun NudgeBanner(nudge: Nudge, onDismiss: () -> Unit) {
    val c = Fit.colors
    FitCard(padding = PaddingValues(start = ForgeSpace.CardPaddingCompact, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.tealTrack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = c.teal, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.friends_nudged_you, nudge.name),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close), tint = c.textSecondary)
            }
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
    FitCard(padding = PaddingValues(horizontal = ForgeSpace.CardPaddingCompact, vertical = 12.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).defaultMinSize(minWidth = 180.dp), verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(person.name, person.photoUrl, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(person.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Row {
                        person.username?.let { HandleText(it) }
                        Text(" " + stringResource(R.string.friends_wants_friends), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDecline, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.friends_decline), color = c.textSecondary)
                }
                Button(
                    onClick = onAccept,
                    enabled = !busy,
                    shape = ForgeShape.Full,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.friends_accept), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private val FriendBadge.labelRes: Int
    get() = when (this) {
        FriendBadge.TRAINED -> R.string.friends_badge_trained
        FriendBadge.ON_TRACK -> R.string.friends_badge_on_track
        FriendBadge.SLACKING -> R.string.friends_badge_slacking
        FriendBadge.REST -> R.string.friends_badge_rest
        FriendBadge.NOT_SYNCED -> R.string.friends_badge_not_synced
    }

/**
 * One board row: rank, avatar, name (+ gold flame with the shared streak), status line, score, then the nudge bell as a
 * SIBLING button (never nested in the row's own click target). Rows without a bell keep its space so scores line up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    nudged: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onNudge: () -> Unit,
) {
    val c = Fit.colors
    val statusWord = stringResource(entry.badge.labelRes)
    val status = if (entry.syncedThisWeek) "$statusWord · " + pluralStringResource(R.plurals.friends_done_count, entry.done, entry.done) else statusWord
    val name = if (entry.isMe) stringResource(R.string.friends_you) else entry.name
    val together = if (entry.sharedStreak > 0) pluralStringResource(R.plurals.friends_together, entry.sharedStreak, entry.sharedStreak) else null
    val scoreText = if (entry.syncedThisWeek) entry.score.toString() else "–"
    val spoken = listOfNotNull("$rank. $name", together, status, scoreText).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (entry.isMe) it.clip(ForgeShape.Sm).background(c.cardHigh).border(1.5.dp, c.teal, ForgeShape.Sm) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clip(ForgeShape.Sm)
                .let { if (!entry.isMe) it.clickable(role = Role.Button, onClick = onOpen) else it }
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (entry.syncedThisWeek) rank.toString() else "–",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = NumberFont).tabular(),
                color = if (entry.isMe) c.teal else c.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.defaultMinSize(minWidth = 20.dp),
            )
            Spacer(Modifier.width(10.dp))
            FriendAvatar(entry.name, entry.photoUrl, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    if (entry.sharedStreak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = c.gold, modifier = Modifier.size(14.dp))
                            Text(
                                entry.sharedStreak.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = NumberFont).tabular(),
                                color = c.gold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = scoreText,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = NumberFont, fontWeight = FontWeight.SemiBold).tabular(),
                color = if (entry.syncedThisWeek) c.textPrimary else c.textSecondary,
            )
        }
        val canNudge = !entry.isMe && entry.weekState != "hit"
        when {
            !canNudge -> Spacer(Modifier.width(48.dp))
            nudged -> {
                val label = stringResource(R.string.friends_nudged_name, entry.name)
                Box(Modifier.size(48.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            else -> IconButton(onClick = onNudge, enabled = !busy, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    contentDescription = stringResource(R.string.friends_nudge_name, entry.name),
                    tint = c.teal,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
private fun OutgoingRow(person: Person, busy: Boolean, onCancel: () -> Unit) {
    val c = Fit.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(person.name, person.photoUrl, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            person.username?.let { HandleText(it) }
        }
        IconButton(onClick = onCancel, enabled = !busy, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.friends_cancel_invite, person.name),
                tint = c.textSecondary,
            )
        }
    }
}

/** "You: done" / "free week" / "not yet" for the shared-streak week line. */
@Composable
private fun weekLine(name: String, state: String?): String {
    // First-strong isolate: a Latin name at the start of an Arabic line would otherwise pull the colon to the wrong side.
    val n = "\u2068$name\u2069"
    return when (state) {
        "hit" -> stringResource(R.string.friends_week_done, n)
        "free" -> stringResource(R.string.friends_week_free, n)
        else -> stringResource(R.string.friends_week_not_yet, n)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FriendDetailSheet(
    entry: LeaderboardEntry,
    myWeekState: String?,
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
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var alreadyNudgedNote by remember(entry.id) { mutableStateOf(false) }
    val nudged = nudgedToday || alreadyNudgedNote

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(entry.name, entry.photoUrl, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    entry.username?.let { HandleText(it) }
                }
            }

            // Shared streak: the one thing this sheet is about.
            Surface(shape = ForgeShape.Md, color = c.goldTrack, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = c.gold, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (entry.sharedStreak > 0) {
                                pluralStringResource(R.plurals.friends_together, entry.sharedStreak, entry.sharedStreak)
                            } else {
                                stringResource(R.string.friends_together_start)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.gold,
                        )
                    }
                    if (entry.sharedStreak > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.friends_together_sub), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        WeekState(weekLine(stringResource(R.string.friends_you), myWeekState), myWeekState == "hit")
                        WeekState(weekLine(entry.name, entry.weekState), entry.weekState == "hit")
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        when (onNudge()) {
                            null -> context.toast(sentMsg)
                            "already_nudged" -> alreadyNudgedNote = true
                        }
                    }
                },
                enabled = !nudged && !busy,
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
                    text = if (nudged) stringResource(R.string.friends_nudged_today) else stringResource(R.string.friends_nudge_name, entry.name),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Column {
                KeyValue(stringResource(R.string.friends_score_label), if (entry.syncedThisWeek) entry.score.toString() else "–")
                KeyValue(stringResource(entry.badge.labelRes), pluralStringResource(R.plurals.friends_done_count, entry.done, entry.done))
                if (entry.syncedThisWeek) {
                    KeyValue(stringResource(R.string.friends_their_streak, entry.name), pluralStringResource(R.plurals.friends_weeks, entry.streakWeeks, entry.streakWeeks))
                    KeyValue(stringResource(R.string.friends_protein_label), pluralStringResource(R.plurals.friends_days, entry.proteinDays, entry.proteinDays))
                }
            }

            Text(
                text = stringResource(R.string.friends_prs_title),
                style = MaterialTheme.typography.titleSmall,
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
                                color = c.textPrimary,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { confirmRemove = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.friends_remove), color = c.textSecondary)
                }
                TextButton(onClick = { confirmBlock = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
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

@Composable
private fun WeekState(text: String, done: Boolean) {
    val c = Fit.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (done) c.textPrimary else c.textSecondary)
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    val c = Fit.colors
    HorizontalDivider(color = c.divider)
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold).tabular(), color = c.textPrimary)
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
