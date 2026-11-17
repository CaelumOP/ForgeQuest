package com.forgequest.app.ui.friends

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.forgequest.app.R
import com.forgequest.app.data.friends.FriendsUiData
import com.forgequest.app.data.friends.Inbox
import com.forgequest.app.data.friends.InboxActivity
import com.forgequest.app.data.friends.InboxPing
import com.forgequest.app.data.friends.Me
import com.forgequest.app.data.friends.NudgeGate
import com.forgequest.app.data.friends.Person
import com.forgequest.app.data.friends.findActivity
import com.forgequest.app.domain.FriendBadge
import com.forgequest.app.domain.LeaderboardEntry
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.LocalSnackbar
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
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.LocalReducedMotion
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

private const val ME_SCORE_ID = "__me__"

private enum class NudgeButtonState { READY, SENT, COOLDOWN }

private enum class InviteButtonState { IDLE, SENDING, SENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(nav: NavHostController) {
    val vm = appViewModel { FriendsViewModel(it) }
    val uiData by vm.state.collectAsState()
    val signedIn by vm.signedIn.collectAsState()
    val me by vm.me.collectAsState()
    val error by vm.error.collectAsState()
    val nudgeNextAt by vm.nudgeNextAt.collectAsState()
    val trainDayNudgeIds by vm.trainDayNudgeIds.collectAsState()
    val pending by vm.pending.collectAsState()
    val hidden by vm.hidden.collectAsState()
    val nudgeSent by vm.nudgeSent.collectAsState()
    val settings by vm.settings.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val busy by vm.busy.collectAsState()

    val c = Fit.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val reduced = LocalReducedMotion.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var scoreTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var inboxOpen by rememberSaveable { mutableStateOf(false) }
    var outOpen by rememberSaveable { mutableStateOf(false) }

    var notifCheckTick by remember { mutableIntStateOf(0) }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifCheckTick++
                nowSec = System.currentTimeMillis() / 1000L
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val nextExpiry = remember(nudgeNextAt, nowSec) {
        nudgeNextAt.values.filter { it > nowSec }.minOrNull()
    }
    LaunchedEffect(nextExpiry, nudgeNextAt) {
        val current = System.currentTimeMillis() / 1000L
        nowSec = current
        val target = nextExpiry ?: return@LaunchedEffect
        val waitMs = ((target - current) * 1000L + 250L).coerceAtLeast(250L)
        delay(waitMs)
        nowSec = System.currentTimeMillis() / 1000L
    }

    LaunchedEffect(vm) {
        val failedMsg = context.getString(R.string.v42_friends_action_failed)
        val nowFriendsMsg = context.getString(R.string.v42_friends_now_friends)
        vm.events.collect { event ->
            when (event) {
                is FriendsUiEvent.ActionFailed -> {
                    if (event.overSheet) {
                        Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch { snackbar.showSnackbar(failedMsg) }
                    }
                }
                FriendsUiEvent.NowFriends -> {
                    Toast.makeText(context, nowFriendsMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifCheckTick++
        if (granted) {
            scope.launch { vm.app.friends.onNotificationsAllowed() }
        }
    }

    val friendsPrefs = remember(context) { context.getSharedPreferences("friends_prefs", Context.MODE_PRIVATE) }

    fun maybeAutoAskNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !friendsPrefs.getBoolean("push_asked", false)
        ) {
            friendsPrefs.edit().putBoolean("push_asked", true).apply()
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestNotificationsExplicitly() {
        val permGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (permGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val activity = context.findActivity()
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permGranted &&
            (!friendsPrefs.getBoolean("push_asked", false) ||
                (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)))
        if (canAsk) {
            friendsPrefs.edit().putBoolean("push_asked", true).apply()
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                },
            )
        }
    }

    var wasJoined by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(me?.joined) {
        val nowJoined = me?.joined == true
        if (wasJoined == false && nowJoined) {
            maybeAutoAskNotifications()
        }
        wasJoined = nowJoined
    }

    LaunchedEffect(signedIn) {
        if (signedIn || vm.isDemo) vm.refresh()
    }

    val activeDemo = vm.isDemo
    val showJoined = uiData != null && (signedIn || activeDemo)
    val showJoinForm = signedIn && !activeDemo && uiData == null && me?.joined != true
    val showLoadingJoined = signedIn && !activeDemo && uiData == null && me?.joined == true

    LaunchedEffect(showJoined, FriendsInboxLink.pending) {
        if (showJoined && FriendsInboxLink.pending) {
            inboxOpen = true
            FriendsInboxLink.pending = false
        }
    }

    var unseenPingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(inboxOpen) {
        if (inboxOpen) {
            unseenPingKeys = uiData?.view?.inbox?.pings
                ?.filter { !it.seen }
                ?.map { "${it.fromId}_${it.day}_${it.createdAt}" }
                ?.toSet()
                ?: emptySet()
            vm.markInboxSeen()
        }
    }

    var listTopY by remember { mutableFloatStateOf(0f) }
    var listBottomY by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var meRowVisible by remember { mutableStateOf(true) }
    val leaderboard = uiData?.leaderboard.orEmpty()
    val meIndex = leaderboard.indexOfFirst { it.isMe }
    val showStickyMe = showJoined &&
        uiData?.view?.friends?.isNotEmpty() == true &&
        leaderboard.size > 6 &&
        meIndex >= 0 &&
        !meRowVisible

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
                .statusBarsPadding()
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    listTopY = pos.y
                    listBottomY = pos.y + coords.size.height
                },
            contentPadding = PaddingValues(
                start = ForgeSpace.Gutter,
                end = ForgeSpace.Gutter,
                bottom = if (showStickyMe) 104.dp else 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ForgeSpace.CardGap),
        ) {
            item(key = "header") {
                // Title with Add, Inbox bell, and Settings beside it; refresh is pull-to-refresh.
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.friends_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (showJoined) {
                        ForgeScoreInfoButton(onClick = { scoreTargetId = ME_SCORE_ID })
                        IconButton(onClick = { addOpen = true }, modifier = Modifier.size(48.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.friends_add_title), tint = c.teal)
                            }
                        }
                        val badgeCount = uiData?.view?.badgeCount ?: 0
                        val bellCd = if (badgeCount > 0) {
                            stringResource(R.string.friends_inbox_badge_cd, badgeCount)
                        } else {
                            stringResource(R.string.friends_inbox_title)
                        }
                        IconButton(
                            onClick = {
                                inboxOpen = true
                                maybeAutoAskNotifications()
                            },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = bellCd },
                        ) {
                            BadgedBox(
                                badge = {
                                    if (badgeCount > 0) {
                                        Badge(
                                            containerColor = c.teal,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ) {
                                            Text(badgeCount.toString())
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = c.teal)
                            }
                        }
                        IconButton(onClick = { nav.navigate("settings/friends") }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.friends_settings), tint = c.textSecondary)
                        }
                    }
                }
            }

            error?.let { code ->
                item(key = "error") {
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
                    item(key = "signed_out") {
                        SignedOutCard(
                            busy = busy,
                            onSignIn = { context.findActivity()?.let(vm::signIn) },
                        )
                    }
                }

                showJoinForm -> {
                    item(key = "join_form") {
                        JoinFormCard(
                            busy = busy,
                            onJoin = vm::join,
                        )
                    }
                }

                showLoadingJoined -> {
                    item(key = "loading") {
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
                    if (data.view.friends.isEmpty()) {
                        item(key = "empty") { EmptyFriends(data.view.me, onSendInvite = { vm.invite(it) }) }
                    } else {
                        item(key = "week_line") { WeekLine(data) }
                        item(key = "leaderboard") {
                            val enter = if (reduced) EnterTransition.None else expandVertically(ForgeMotion.spatial(false)) + fadeIn(ForgeMotion.effects(false))
                            val exit = if (reduced) ExitTransition.None else shrinkVertically(ForgeMotion.spatial(false)) + fadeOut(ForgeMotion.effects(false))
                            FitCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                data.leaderboard.forEachIndexed { index, entry ->
                                    key(entry.id) {
                                        val rowVisible = entry.id !in hidden
                                        val hasVisibleAbove = data.leaderboard.take(index).any { it.id !in hidden && !it.isMe }
                                        AnimatedVisibility(visible = rowVisible, enter = enter, exit = exit) {
                                            Column {
                                                if (index > 0 && !entry.isMe && hasVisibleAbove) HorizontalDivider(color = c.divider)
                                                val rowModifier = if (entry.isMe) {
                                                    Modifier.onGloballyPositioned { coords ->
                                                        if (coords.isAttached) {
                                                            val pos = coords.positionInRoot()
                                                            val top = pos.y
                                                            val bottom = top + coords.size.height
                                                            meRowVisible = bottom > listTopY && top < listBottomY
                                                        }
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                                val blockedUntil = NudgeGate.blockedUntil(null, nudgeNextAt[entry.id], nowSec)
                                                val cooldownLabel = rememberCooldownLabel(blockedUntil, nowSec)
                                                LeaderboardRow(
                                                    rank = index + 1,
                                                    total = data.leaderboard.size,
                                                    entry = entry,
                                                    justSent = entry.id in nudgeSent,
                                                    cooldownLabel = cooldownLabel,
                                                    showTrainDayHint = cooldownLabel != null && entry.id in trainDayNudgeIds,
                                                    onOpen = { selectedId = entry.id },
                                                    onNudge = { vm.nudge(entry.id) },
                                                    onScore = { scoreTargetId = if (entry.isMe) ME_SCORE_ID else entry.id },
                                                    modifier = rowModifier,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val visibleOutgoing = data.view.outgoing.filter { it.id !in hidden }
                    if (data.view.outgoing.isNotEmpty()) {
                        item(key = "outgoing_header") {
                            Row(
                                friendsItemMotion(
                                    reduced = reduced,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(ForgeShape.Sm)
                                        .clickable(role = Role.Button) { outOpen = !outOpen }
                                        .padding(horizontal = 4.dp),
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    pluralStringResource(R.plurals.friends_invites_waiting, visibleOutgoing.size, visibleOutgoing.size),
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
                            items(visibleOutgoing, key = { "out_${it.id}" }) { person ->
                                Box(friendsItemMotion(reduced)) {
                                    OutgoingRow(
                                        person = person,
                                        inProgress = person.id in pending,
                                        onCancel = { vm.cancelInvite(person.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showStickyMe,
            enter = if (reduced) EnterTransition.None else fadeIn(ForgeMotion.effects(false)),
            exit = if (reduced) ExitTransition.None else fadeOut(ForgeMotion.effects(false)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (meIndex >= 0) {
                val meEntry = leaderboard[meIndex]
                Surface(
                    shape = ForgeShape.Lg,
                    color = c.card,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = ForgeSpace.Gutter, vertical = 12.dp),
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        LeaderboardRow(
                            rank = meIndex + 1,
                            total = leaderboard.size,
                            entry = meEntry,
                            justSent = false,
                            cooldownLabel = null,
                            showTrainDayHint = false,
                            onOpen = {},
                            onNudge = {},
                            onScore = { scoreTargetId = ME_SCORE_ID },
                        )
                    }
                }
            }
        }
    }

    scoreTargetId?.let { targetId ->
        val data = uiData
        if (targetId == ME_SCORE_ID) {
            val own = data?.mySnapshot?.takeIf { it.weekStart == data.view.weekStart }
            ForgeScoreSheet(
                done = own?.done ?: 0,
                target = own?.target ?: own?.due ?: 0,
                prCount = own?.prCount ?: 0,
                proteinDays = own?.proteinDays ?: 0,
                breakdown = own?.breakdown,
                score = own?.score,
                onDismiss = { scoreTargetId = null },
            )
        } else {
            val entry = data?.leaderboard?.firstOrNull { it.id == targetId }
            if (entry != null) {
                ForgeScoreSheet(
                    done = entry.done,
                    target = entry.target,
                    prCount = entry.prCount,
                    proteinDays = entry.proteinDays,
                    breakdown = if (entry.syncedThisWeek) entry.breakdown else null,
                    score = if (entry.syncedThisWeek) entry.score else null,
                    onDismiss = { scoreTargetId = null },
                )
            } else {
                scoreTargetId = null
            }
        }
    }

    if (inboxOpen && uiData != null) {
        val permGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val notifsAllowed = remember(notifCheckTick, permGranted) {
            permGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        InboxSheet(
            incoming = uiData!!.view.incoming,
            inbox = uiData!!.view.inbox,
            unseenPingKeys = unseenPingKeys,
            notifsAllowed = notifsAllowed,
            pending = pending,
            hidden = hidden,
            onRequestNotifs = ::requestNotificationsExplicitly,
            onAccept = vm::accept,
            onDecline = vm::decline,
            onDismiss = { inboxOpen = false },
        )
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
                    onSendInvite = { username -> vm.invite(username) },
                    onInviteSuccess = { addOpen = false },
                )
            }
        }
    }

    val selected = selectedId?.let { id -> uiData?.leaderboard?.firstOrNull { it.id == id && !it.isMe } }
    selected?.let { entry ->
        val unit = WeightUnit.fromCodeOrLabel(settings?.unit ?: WeightUnit.KG.code)
        val mine = uiData?.mySnapshot
        val myWeekState = mine?.takeIf { s -> s.weekStart >= (uiData?.view?.weekStart ?: 0L) }
            ?.let { s -> com.forgequest.app.domain.FriendsLeaderboard.effectiveWeekState(s.weekState, s.done, s.due) }
        val blockedUntil = NudgeGate.blockedUntil(null, nudgeNextAt[entry.id], nowSec)
        val cooldownLabel = rememberCooldownLabel(blockedUntil, nowSec)
        FriendDetailSheet(
            entry = entry,
            myWeekState = myWeekState,
            unit = unit,
            justSent = entry.id in nudgeSent,
            cooldownLabel = cooldownLabel,
            showTrainDayHint = cooldownLabel != null && entry.id in trainDayNudgeIds,
            pending = entry.id in pending,
            onSetMuted = { muted -> vm.setMuted(entry.id, muted) },
            onNudge = { vm.nudge(entry.id, overSheet = true) },
            onRemove = {
                selectedId = null
                vm.remove(entry.id)
            },
            onBlock = {
                selectedId = null
                vm.block(entry.id)
            },
            onDismiss = { selectedId = null },
        )
    }
}

@Composable
private fun LazyItemScope.friendsItemMotion(reduced: Boolean, modifier: Modifier = Modifier): Modifier = modifier.animateItem(
    fadeInSpec = if (reduced) snap() else ForgeMotion.effects(false),
    placementSpec = if (reduced) snap() else ForgeMotion.spatial(false, visibilityThreshold = IntOffset.VisibilityThreshold),
    fadeOutSpec = if (reduced) snap() else ForgeMotion.effects(false),
)

@Composable
private fun rememberCooldownLabel(blockedUntil: Long?, nowSec: Long): String? {
    if (blockedUntil == null || nowSec >= blockedUntil) return null
    val context = LocalContext.current
    return when (NudgeGate.labelKind(blockedUntil, nowSec)) {
        NudgeGate.LabelKind.TOMORROW -> stringResource(R.string.v42_friends_nudge_tomorrow)
        NudgeGate.LabelKind.AGAIN_AT -> {
            val formatted = remember(blockedUntil) {
                DateFormat.getTimeFormat(context).format(Date(blockedUntil * 1000L))
            }
            stringResource(R.string.v42_friends_nudge_again_at, formatted)
        }
    }
}

/** "This week · 3 days left" with compact rank badge at the trailing end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekLine(data: FriendsUiData) {
    val c = Fit.colors
    val today = maxOf(data.view.today, com.forgequest.app.domain.Dates.today())
    val daysLeft = (data.view.weekStart + 6 - today).coerceAtLeast(0L).toInt()
    val meIndex = data.leaderboard.indexOfFirst { it.isMe }
    val meEntry = data.leaderboard.getOrNull(meIndex)
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.friends_board_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(
                " · " + if (daysLeft == 0) stringResource(R.string.friends_last_day) else pluralStringResource(R.plurals.friends_days_left, daysLeft, daysLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
        if (meEntry != null && meEntry.syncedThisWeek) {
            RankBadge(rank = meIndex + 1, total = data.leaderboard.size)
        }
    }
}

/** No friends yet: one encouraging line, then your username to share and the add field. */
@Composable
private fun EmptyFriends(me: Me, onSendInvite: suspend (String) -> String?) {
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
        InviteHeaderCard(me = me, onSendInvite = onSendInvite)
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
 * One board row: rank, avatar, name (with shared streak under it), status line, score, and the "Go train 💪" pill
 * on its own line under the status text (never squeezes the name).
 */
@Composable
private fun LeaderboardRow(
    rank: Int,
    total: Int,
    entry: LeaderboardEntry,
    justSent: Boolean,
    cooldownLabel: String?,
    showTrainDayHint: Boolean,
    onOpen: () -> Unit,
    onNudge: () -> Unit,
    onScore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val haptic = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val statusWord = stringResource(entry.badge.labelRes)
    val status = if (entry.syncedThisWeek) "$statusWord · " + pluralStringResource(R.plurals.friends_done_count, entry.done, entry.done) else statusWord
    val name = if (entry.isMe) stringResource(R.string.friends_you) else entry.name
    val together = if (entry.sharedStreak > 0) pluralStringResource(R.plurals.friends_together, entry.sharedStreak, entry.sharedStreak) else null
    val scoreText = if (entry.syncedThisWeek) entry.score.toString() else "–"
    val rankSpoken = if (entry.syncedThisWeek) rankContentDescription(rank, total, isArabic) else null
    val spoken = listOfNotNull(rankSpoken, name, together, status, scoreText).joinToString(", ")
    Column(
        modifier
            .fillMaxWidth()
            .let { if (entry.isMe) it.clip(ForgeShape.Sm).background(c.cardHigh).border(1.5.dp, c.teal, ForgeShape.Sm) else it },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(ForgeShape.Sm)
                .let { if (!entry.isMe) it.clickable(role = Role.Button, onClick = onOpen) else it }
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(
                rank = if (entry.syncedThisWeek) rank else null,
                total = total,
                includeSemantics = false,
            )
            Spacer(Modifier.width(10.dp))
            FriendAvatar(entry.name, entry.photoUrl, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                if (together != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = c.gold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            together,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.gold,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            ForgeScoreButton(if (entry.syncedThisWeek) entry.score else null, entry.done, entry.target, onClick = onScore)
        }
        val canNudge = !entry.isMe && entry.weekState != "hit"
        if (canNudge) {
            val nudgeStart = if (isArabic) 84.dp else 94.dp
            val nudgeState = when {
                justSent -> NudgeButtonState.SENT
                cooldownLabel != null -> NudgeButtonState.COOLDOWN
                else -> NudgeButtonState.READY
            }
            Column(Modifier.padding(start = nudgeStart, end = 4.dp, bottom = 4.dp)) {
                val clickMod = if (nudgeState == NudgeButtonState.READY) {
                    Modifier.clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onNudge()
                    }
                } else {
                    Modifier
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(ForgeShape.Full)
                        .then(clickMod)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AnimatedContent(
                        targetState = nudgeState,
                        transitionSpec = {
                            if (reduced) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                fadeIn(tween(ForgeMotion.Duration.SHORT4, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(ForgeMotion.Duration.SHORT3, easing = FastOutSlowInEasing))
                            }
                        },
                        label = "nudgePill",
                    ) { state ->
                        when (state) {
                            NudgeButtonState.READY -> Surface(
                                shape = ForgeShape.Full,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, c.teal),
                            ) {
                                Text(
                                    text = stringResource(R.string.friends_go_train),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.teal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                            NudgeButtonState.SENT -> Surface(
                                shape = ForgeShape.Full,
                                color = c.tealTrack,
                                border = BorderStroke(1.dp, c.teal),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = c.teal,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.friends_nudge_sent),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = c.teal,
                                    )
                                }
                            }
                            NudgeButtonState.COOLDOWN -> Surface(
                                shape = ForgeShape.Full,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, c.divider),
                            ) {
                                Text(
                                    text = cooldownLabel.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.textSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                if (nudgeState == NudgeButtonState.COOLDOWN && showTrainDayHint) {
                    Text(
                        text = stringResource(R.string.v42_friends_nudge_train_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InboxSheet(
    incoming: List<Person>,
    inbox: Inbox,
    unseenPingKeys: Set<String>,
    notifsAllowed: Boolean,
    pending: Set<String>,
    hidden: Set<String>,
    onRequestNotifs: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    val reduced = LocalReducedMotion.current
    val enter = if (reduced) EnterTransition.None else expandVertically(ForgeMotion.spatial(false)) + fadeIn(ForgeMotion.effects(false))
    val exit = if (reduced) ExitTransition.None else shrinkVertically(ForgeMotion.spatial(false)) + fadeOut(ForgeMotion.effects(false))
    val pings = remember(inbox.pings) { inbox.pings.sortedByDescending { it.createdAt } }
    val activity = remember(inbox.activity) { inbox.activity.sortedByDescending { it.at } }
    val visibleIncoming = incoming.filter { it.id !in hidden }
    val allEmpty = visibleIncoming.isEmpty() && pings.isEmpty() && activity.isEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        containerColor = c.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ForgeSpace.Gutter)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.friends_inbox_title),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                modifier = Modifier.semantics { heading() },
            )

            if (!notifsAllowed) {
                Surface(
                    shape = ForgeShape.Md,
                    color = c.cardHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.friends_turn_on_notifs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onRequestNotifs,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.friends_turn_on_action), color = c.teal)
                        }
                    }
                }
            }

            if (allEmpty) {
                Text(
                    text = stringResource(R.string.friends_inbox_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (incoming.isNotEmpty()) {
                AnimatedVisibility(visible = visibleIncoming.isNotEmpty(), enter = enter, exit = exit) {
                    Text(
                        text = stringResource(R.string.friends_incoming_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textSecondary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                incoming.forEach { person ->
                    key(person.id) {
                        val itemPending = person.id in pending
                        AnimatedVisibility(visible = person.id !in hidden, enter = enter, exit = exit) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    Modifier.weight(1f).defaultMinSize(minWidth = 180.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FriendAvatar(person.shownName, person.photoUrl, size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(person.shownName, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                        Text(
                                            stringResource(R.string.friends_wants_friends),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.textSecondary,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { if (!itemPending) onDecline(person.id) },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text(stringResource(R.string.friends_decline), color = c.textSecondary)
                                    }
                                    Button(
                                        onClick = { if (!itemPending) onAccept(person.id) },
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
                }
            }

            if (pings.isNotEmpty()) {
                if (visibleIncoming.isNotEmpty()) HorizontalDivider(color = c.divider)
                Text(
                    text = stringResource(R.string.friends_inbox_pings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textSecondary,
                    modifier = Modifier.semantics { heading() },
                )
                pings.forEach { ping ->
                    val isUnseen = "${ping.fromId}_${ping.day}_${ping.createdAt}" in unseenPingKeys
                    InboxMessageRow(
                        text = stringResource(R.string.friends_inbox_ping_line, Usernames.ltr(ping.shownName)),
                        timeMs = ping.createdAt * 1000L, // Worker sends epoch seconds
                        unseen = isUnseen,
                    )
                }
            }

            if (activity.isNotEmpty()) {
                if (visibleIncoming.isNotEmpty() || pings.isNotEmpty()) HorizontalDivider(color = c.divider)
                Text(
                    text = stringResource(R.string.friends_inbox_activity_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textSecondary,
                    modifier = Modifier.semantics { heading() },
                )
                activity.forEach { act ->
                    InboxMessageRow(
                        text = stringResource(R.string.friends_inbox_activity_line, Usernames.ltr(act.shownName)),
                        timeMs = act.at * 1000L,
                        unseen = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxMessageRow(text: String, timeMs: Long, unseen: Boolean) {
    val c = Fit.colors
    val relTime = if (timeMs > 0L) {
        DateUtils.getRelativeTimeSpanString(timeMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    } else {
        ""
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (unseen) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.teal))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            if (relTime.isNotEmpty()) {
                Text(relTime, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
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
    busy: Boolean,
    onJoin: (String, Boolean, Boolean) -> Unit,
) {
    val c = Fit.colors
    var username by rememberSaveable { mutableStateOf("") }
    var sharePhoto by rememberSaveable { mutableStateOf(true) }
    var sharePrs by rememberSaveable { mutableStateOf(true) }

    val valid = Usernames.normalize(username) != null

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
            onClick = { onJoin(username, sharePhoto, sharePrs) },
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
    onSendInvite: suspend (String) -> String?,
    onInviteSuccess: () -> Unit = {},
) {
    val c = Fit.colors
    val context = LocalContext.current
    val clipboard = rememberTextClipboard()
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val handle = "@${me.username.orEmpty()}"
    val copiedMsg = stringResource(R.string.friends_username_copied)
    val shareMsg = stringResource(R.string.friends_share_message, me.username.orEmpty(), Usernames.SHARE_URL)
    var nameInput by rememberSaveable { mutableStateOf("") }
    var inviteState by remember { mutableStateOf(InviteButtonState.IDLE) }
    val validName = Usernames.normalize(nameInput) != null

    fun submitInvite() {
        if (!validName || inviteState != InviteButtonState.IDLE) return
        inviteState = InviteButtonState.SENDING
        scope.launch {
            val err = onSendInvite(nameInput)
            if (err == null) {
                nameInput = ""
                inviteState = InviteButtonState.SENT
                delay(if (reduced) 300L else 850L)
                inviteState = InviteButtonState.IDLE
                onInviteSuccess()
            } else {
                inviteState = InviteButtonState.IDLE
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
                enabled = validName || inviteState != InviteButtonState.IDLE,
                modifier = Modifier.defaultMinSize(minWidth = 76.dp, minHeight = 48.dp),
            ) {
                AnimatedContent(
                    targetState = inviteState,
                    transitionSpec = {
                        if (reduced) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(tween(ForgeMotion.Duration.SHORT4, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(ForgeMotion.Duration.SHORT3, easing = FastOutSlowInEasing))
                        }
                    },
                    contentAlignment = Alignment.Center,
                    label = "inviteButton",
                ) { state ->
                    when (state) {
                        InviteButtonState.IDLE -> Text(
                            text = stringResource(R.string.friends_add_button),
                            color = if (validName) c.teal else c.textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        InviteButtonState.SENDING -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = c.teal,
                        )
                        InviteButtonState.SENT -> Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.friends_invite_sent),
                            tint = c.teal,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = inviteState == InviteButtonState.SENT,
            enter = if (reduced) EnterTransition.None else expandVertically(ForgeMotion.spatial(false)) + fadeIn(ForgeMotion.effects(false)),
            exit = if (reduced) ExitTransition.None else shrinkVertically(ForgeMotion.spatial(false)) + fadeOut(ForgeMotion.effects(false)),
        ) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.teal, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.friends_invite_sent),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.teal,
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
private fun OutgoingRow(person: Person, inProgress: Boolean, onCancel: () -> Unit) {
    val c = Fit.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(person.shownName, person.photoUrl, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = person.shownName,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { if (!inProgress) onCancel() }, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.friends_cancel_invite, Usernames.ltr(person.shownName)),
                tint = c.textSecondary,
            )
        }
    }
}

/** "You: done" / "free week" / "not yet" for the shared-streak week line. */
@Composable
private fun weekLine(name: String, state: String?, isMe: Boolean = false): String {
    if (isMe) {
        return when (state) {
            "hit" -> stringResource(R.string.friends_week_you_done)
            "free" -> stringResource(R.string.friends_week_you_free)
            else -> stringResource(R.string.friends_week_you_not_yet)
        }
    }
    // LTR isolate (not first-strong): an all-digit username has no strong letter, so it must be forced LTR.
    val n = Usernames.ltr(name)
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
    justSent: Boolean,
    cooldownLabel: String?,
    showTrainDayHint: Boolean,
    pending: Boolean,
    onSetMuted: (Boolean) -> Unit,
    onNudge: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    val haptic = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val nudgeState = when {
        justSent -> NudgeButtonState.SENT
        cooldownLabel != null -> NudgeButtonState.COOLDOWN
        else -> NudgeButtonState.READY
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        containerColor = c.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ForgeSpace.Gutter)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(entry.name, entry.photoUrl, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
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
                        WeekState(weekLine(stringResource(R.string.friends_you), myWeekState, isMe = true), myWeekState == "hit")
                        WeekState(weekLine(entry.name, entry.weekState), entry.weekState == "hit")
                    }
                }
            }

            if (entry.weekState != "hit") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            if (nudgeState == NudgeButtonState.READY) {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onNudge()
                            }
                        },
                        enabled = nudgeState != NudgeButtonState.COOLDOWN,
                        shape = ForgeShape.Full,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        AnimatedContent(
                            targetState = nudgeState,
                            transitionSpec = {
                                if (reduced) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(tween(ForgeMotion.Duration.SHORT4, easing = FastOutSlowInEasing)) togetherWith
                                        fadeOut(tween(ForgeMotion.Duration.SHORT3, easing = FastOutSlowInEasing))
                                }
                            },
                            label = "detailNudge",
                        ) { state ->
                            when (state) {
                                NudgeButtonState.READY -> Text(
                                    text = stringResource(R.string.friends_go_train),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                NudgeButtonState.SENT -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = stringResource(R.string.friends_nudge_sent),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                NudgeButtonState.COOLDOWN -> Text(
                                    text = cooldownLabel.orEmpty(),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    if (nudgeState == NudgeButtonState.COOLDOWN && showTrainDayHint) {
                        Text(
                            text = stringResource(R.string.v42_friends_nudge_train_day),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
            }

            val alertsEnabled = !entry.muted
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = alertsEnabled, role = Role.Switch) { checked ->
                        onSetMuted(!checked)
                    }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.friends_alert_when_trains, Usernames.ltr(entry.name)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                FitSwitch(alertsEnabled, null)
            }

            Column {
                KeyValue(stringResource(R.string.friends_score_label), if (entry.syncedThisWeek) entry.score.toString() else "–")
                KeyValue(stringResource(entry.badge.labelRes), pluralStringResource(R.plurals.friends_done_count, entry.done, entry.done))
                if (entry.syncedThisWeek) {
                    KeyValue(stringResource(R.string.friends_their_streak, Usernames.ltr(entry.name)), pluralStringResource(R.plurals.friends_weeks, entry.streakWeeks, entry.streakWeeks))
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
                                text = com.forgequest.app.data.seed.ExerciseNamesAr.displayName(pr.exercise),
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
                TextButton(onClick = { confirmRemove = true }, enabled = !pending, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.friends_remove), color = c.textSecondary)
                }
                TextButton(onClick = { confirmBlock = true }, enabled = !pending, modifier = Modifier.heightIn(min = 48.dp)) {
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
            text = { Text(stringResource(R.string.friends_remove_confirm_body, Usernames.ltr(entry.name)), color = c.textSecondary) },
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
            text = { Text(stringResource(R.string.friends_block_confirm_body, Usernames.ltr(entry.name)), color = c.textSecondary) },
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
