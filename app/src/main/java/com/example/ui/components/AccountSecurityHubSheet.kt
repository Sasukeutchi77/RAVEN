package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AccountSecurityViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityHubSheet(
    securityViewModel: AccountSecurityViewModel,
    isFirebaseSignedIn: Boolean,
    firebaseUserEmail: String?,
    onDismiss: () -> Unit
) {
    val uiState by securityViewModel.uiState.collectAsState()
    val palette = ravenPalette()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPinConfigDialog by remember { mutableStateOf(false) }
    var renamingDevice by remember { mutableStateOf<ConnectedDevice?>(null) }
    var showBackupCodesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.oneTimeBackupCodes) {
        if (uiState.oneTimeBackupCodes.isNotEmpty()) {
            showBackupCodesDialog = true
        }
    }

    val tabs = listOf("Vue d'ensemble", "Sessions", "Activité", "Protection & 2FA")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("account_security_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DiscordBlurple.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = DiscordBlurple)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Sécurité du compte RAVEN",
                            color = palette.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isFirebaseSignedIn) (firebaseUserEmail ?: "Compte vérifié") else "Protection locale & cloud",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                if (uiState.openAlertsCount > 0) {
                    Surface(
                        color = DiscordRed.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${uiState.openAlertsCount} alerte(s)",
                            color = DiscordRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            uiState.feedbackMessage?.let { msg ->
                Surface(
                    color = DiscordGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Text(msg, color = DiscordGreen, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
            }
            uiState.errorMessage?.let { err ->
                Surface(
                    color = DiscordRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Text(err, color = DiscordRed, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = DiscordBlurple,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, fontSize = 12.sp, maxLines = 1) },
                        modifier = Modifier.testTag("security_tab_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> SecurityOverviewTab(
                    uiState = uiState,
                    palette = palette,
                    onGlobalSignOutOthers = { securityViewModel.requestGlobalSignOut(includeCurrentDevice = false) },
                    onGlobalSignOutAll = { securityViewModel.requestGlobalSignOut(includeCurrentDevice = true) },
                    onActivateLockdown = { securityViewModel.activateEmergencyLockdown() },
                    onLiftFreeze = { securityViewModel.requestLiftFreeze() },
                    onOpenPinConfig = { showPinConfigDialog = true },
                    onGoToTab = { selectedTab = it },
                    onResolveAlert = { id, status, revoke -> securityViewModel.resolveAlert(id, status, revoke) }
                )
                1 -> SessionsAndDevicesTab(
                    uiState = uiState,
                    palette = palette,
                    onRevokeSession = { securityViewModel.requestRevokeSession(it) },
                    onGlobalSignOut = { securityViewModel.requestGlobalSignOut(includeCurrentDevice = false) },
                    onRenameDevice = { renamingDevice = it },
                    onTrustDevice = { d, state -> securityViewModel.setDeviceTrust(d, state) },
                    onRemoveDevice = { securityViewModel.removeDevice(it) }
                )
                2 -> ActivityAndLogsTab(
                    uiState = uiState,
                    palette = palette,
                    onResolveAlert = { id, status, revoke -> securityViewModel.resolveAlert(id, status, revoke) },
                    onSelectLogCategory = { securityViewModel.selectLogCategory(it) }
                )
                3 -> ProtectionAndTwoFactorTab(
                    uiState = uiState,
                    palette = palette,
                    onUpdatePolicies = { p, n, u, r -> securityViewModel.updatePolicies(p, n, u, r) },
                    onOpenPinConfig = { showPinConfigDialog = true },
                    onPrepareTwoFactor = { m, lbl -> securityViewModel.requestPrepareTwoFactor(m, lbl) },
                    onRemoveTwoFactor = { id -> securityViewModel.requestRemoveTwoFactorFactor(id) },
                    onGenerateBackupCodes = { securityViewModel.requestGenerateRecoveryCodes() }
                )
            }
        }
    }

    if (showPinConfigDialog) {
        ConfigureSecurityPinDialog(
            hasExistingPin = !uiState.securityProfile.pinHash.isNullOrBlank(),
            onSavePin = { securityViewModel.requestConfigureSecurityPin(it) },
            onDismiss = { showPinConfigDialog = false }
        )
    }

    renamingDevice?.let { dev ->
        RenameDeviceDialog(
            device = dev,
            onSave = { newName -> securityViewModel.renameDevice(dev.deviceIdHash, newName) },
            onDismiss = { renamingDevice = null }
        )
    }

    if (showBackupCodesDialog && uiState.oneTimeBackupCodes.isNotEmpty()) {
        OneTimeBackupCodesDialog(
            codes = uiState.oneTimeBackupCodes,
            onDismiss = {
                showBackupCodesDialog = false
                securityViewModel.clearOneTimeBackupCodes()
            }
        )
    }
}

@Composable
private fun SecurityOverviewTab(
    uiState: AccountSecurityUiState,
    palette: RavenPalette,
    onGlobalSignOutOthers: () -> Unit,
    onGlobalSignOutAll: () -> Unit,
    onActivateLockdown: () -> Unit,
    onLiftFreeze: () -> Unit,
    onOpenPinConfig: () -> Unit,
    onGoToTab: (Int) -> Unit,
    onResolveAlert: (String, SecurityAlertStatus, Boolean) -> Unit
) {
    val profile = uiState.securityProfile
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (uiState.isSecurityFrozen) {
            item {
                Surface(
                    color = DiscordRed.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DiscordRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = DiscordRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gel de sécurité actif", color = DiscordRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            "Les modifications sensibles sont temporairement bloquées par la protection anti-changements suspects.",
                            color = palette.textPrimary,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onLiftFreeze,
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordRed),
                            modifier = Modifier.testTag("lift_freeze_btn")
                        ) {
                            Text("Déverrouiller avec confirmation", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("État de protection RAVEN", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "Epoch #${profile.sessionEpoch} • ${uiState.activeSessionsCount} session(s) active(s) • ${uiState.devices.size} appareil(s)",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            color = if (uiState.openAlertsCount == 0) DiscordGreen.copy(alpha = 0.16f) else DiscordAmber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (uiState.openAlertsCount == 0) "Sain" else "À vérifier",
                                color = if (uiState.openAlertsCount == 0) DiscordGreen else DiscordAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onGlobalSignOutOthers,
                            modifier = Modifier.weight(1f).testTag("global_signout_others_btn")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Déco. autres", fontSize = 11.sp)
                        }
                        Button(
                            onClick = onGlobalSignOutAll,
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordRed),
                            modifier = Modifier.weight(1f).testTag("global_signout_all_btn")
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Déco. globale", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onOpenPinConfig, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (profile.pinHash.isNullOrBlank()) "Créer code PIN" else "Changer PIN", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = onActivateLockdown, modifier = Modifier.weight(1f).testTag("emergency_lockdown_btn")) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gel 30 min", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        val openAlerts = uiState.securityAlerts.filter { it.status == SecurityAlertStatus.OPEN }
        if (openAlerts.isNotEmpty()) {
            item {
                Text("Alertes prioritaires", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            items(openAlerts.take(3), key = { "ov_al_${it.id}" }) { alert ->
                SecurityAlertCard(alert = alert, palette = palette, onResolve = onResolveAlert)
            }
        }

        item {
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth().clickable { onGoToTab(3) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compatibilité 2FA & Codes de secours", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            text = "Méthode : ${profile.preferredTwoFactorMethod.label} • ${profile.backupCodesRemainingCount} code(s) prêt(s)",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
                }
            }
        }
    }
}

@Composable
private fun SessionsAndDevicesTab(
    uiState: AccountSecurityUiState,
    palette: RavenPalette,
    onRevokeSession: (AccountSession) -> Unit,
    onGlobalSignOut: () -> Unit,
    onRenameDevice: (ConnectedDevice) -> Unit,
    onTrustDevice: (ConnectedDevice, DeviceTrustState) -> Unit,
    onRemoveDevice: (ConnectedDevice) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sessions (${uiState.sessions.size})", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = onGlobalSignOut) { Text("Tout déconnecter", fontSize = 11.sp, color = DiscordRed) }
            }
        }

        items(uiState.sessions, key = { it.sessionId }) { session ->
            val isMine = session.sessionId == uiState.currentSessionId || session.isCurrentDevice
            val isActive = session.status == SessionStatus.ACTIVE
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isMine) DiscordBlurple else palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val sessionIcon = when {
                        session.platformLabel.contains("PC", ignoreCase = true) ||
                            session.platformLabel.contains("ChromeOS", ignoreCase = true) ||
                            session.platformLabel.contains("Windows", ignoreCase = true) ||
                            session.platformLabel.contains("Bureau", ignoreCase = true) -> Icons.Default.Computer
                        session.platformLabel.contains("Tablette", ignoreCase = true) -> Icons.Default.TabletAndroid
                        isMine -> Icons.Default.PhoneAndroid
                        else -> Icons.Default.Devices
                    }
                    Icon(
                        imageVector = sessionIcon,
                        contentDescription = session.platformLabel,
                        tint = if (isActive) DiscordGreen else palette.textMuted
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(session.deviceName, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isMine) {
                                Surface(color = DiscordBlurple.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                    Text("Cet appareil", color = DiscordBlurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text(
                            text = "${session.platformLabel} • ${session.networkRegionLabel} • Epoch #${session.sessionEpoch}",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${session.status.label} · ${formatTimestamp(session.lastActivityMs)}",
                            color = if (isActive) DiscordGreen else DiscordRed,
                            fontSize = 11.sp
                        )
                    }
                    if (isActive && !isMine) {
                        TextButton(
                            onClick = { onRevokeSession(session) },
                            modifier = Modifier.testTag("revoke_session_${session.sessionId}")
                        ) {
                            Text("Révoquer", color = DiscordRed, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Appareils connectés (${uiState.devices.size})", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        items(uiState.devices, key = { "dev_${it.deviceIdHash}" }) { device ->
            val isMine = device.deviceIdHash == uiState.currentDeviceIdHash
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth().testTag("device_card_${device.deviceIdHash}")
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMine) "${device.displayName} (Actuel)" else device.displayName,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${device.osVersion} • Vu le ${formatTimestamp(device.lastSeenAtMs)}",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        val trustColor = when (device.trustState) {
                            DeviceTrustState.TRUSTED -> DiscordGreen
                            DeviceTrustState.UNTRUSTED -> DiscordAmber
                            DeviceTrustState.BLOCKED -> DiscordRed
                        }
                        Surface(color = trustColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                            Text(device.trustState.label, color = trustColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            AssistChip(onClick = { onRenameDevice(device) }, label = { Text("Renommer", fontSize = 11.sp) })
                        }
                        if (device.trustState != DeviceTrustState.TRUSTED) {
                            item {
                                AssistChip(onClick = { onTrustDevice(device, DeviceTrustState.TRUSTED) }, label = { Text("Faire confiance", fontSize = 11.sp) })
                            }
                        }
                        if (!isMine && device.trustState != DeviceTrustState.BLOCKED) {
                            item {
                                AssistChip(onClick = { onTrustDevice(device, DeviceTrustState.BLOCKED) }, label = { Text("Bloquer", fontSize = 11.sp, color = DiscordRed) })
                            }
                        }
                        if (!isMine) {
                            item {
                                AssistChip(onClick = { onRemoveDevice(device) }, label = { Text("Supprimer", fontSize = 11.sp) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityAndLogsTab(
    uiState: AccountSecurityUiState,
    palette: RavenPalette,
    onResolveAlert: (String, SecurityAlertStatus, Boolean) -> Unit,
    onSelectLogCategory: (SecurityLogCategory) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Alertes d'activité inhabituelle (${uiState.securityAlerts.size})", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (uiState.securityAlerts.isEmpty()) {
            item {
                Text("Aucune activité suspecte détectée.", color = palette.textMuted, fontSize = 12.sp)
            }
        } else {
            items(uiState.securityAlerts, key = { "al_${it.id}" }) { alert ->
                SecurityAlertCard(alert = alert, palette = palette, onResolve = onResolveAlert)
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Historique de connexion (${uiState.loginHistory.size})", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        items(uiState.loginHistory.take(15), key = { "lh_${it.id}" }) { entry ->
            val outcomeColor = when (entry.outcome) {
                LoginOutcome.SUCCESS -> DiscordGreen
                LoginOutcome.SUSPICIOUS_FLAGGED -> DiscordAmber
                LoginOutcome.CHALLENGE_REQUIRED -> DiscordBlurple
                LoginOutcome.BLOCKED -> DiscordRed
            }
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${entry.deviceName} · ${entry.authMethod}", color = palette.textPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        Text(entry.outcome.label, color = outcomeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${entry.regionLabel} · ${formatTimestamp(entry.timestampMs)}", color = palette.textMuted, fontSize = 11.sp)
                    entry.anomalyReason?.let { Text(it, color = DiscordAmber, fontSize = 11.sp) }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Journal de sécurité immuable", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SecurityLogCategory.entries) { cat ->
                    FilterChip(
                        selected = uiState.selectedLogCategory == cat,
                        onClick = { onSelectLogCategory(cat) },
                        label = { Text(cat.label, fontSize = 11.sp) }
                    )
                }
            }
        }

        items(uiState.securityLogs, key = { "log_${it.id}" }) { log ->
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${log.category.label} · ${log.action}", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(log.timestampLabel.ifBlank { formatTimestamp(log.timestampMs) }, color = palette.textMuted, fontSize = 10.sp)
                    }
                    Text(log.summary, color = palette.textSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ProtectionAndTwoFactorTab(
    uiState: AccountSecurityUiState,
    palette: RavenPalette,
    onUpdatePolicies: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onOpenPinConfig: () -> Unit,
    onPrepareTwoFactor: (TwoFactorMethod, String) -> Unit,
    onRemoveTwoFactor: (String) -> Unit,
    onGenerateBackupCodes: () -> Unit
) {
    val p = uiState.securityProfile
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PROTECTION CONTRE LES CHANGEMENTS SUSPECTS", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    PolicySwitchRow(
                        title = "Protection anti-changements suspects",
                        subtitle = "Gèle automatiquement le compte après une rafale de modifications sensibles",
                        checked = p.protectSuspiciousChanges,
                        onCheckedChange = { onUpdatePolicies(it, p.alertOnNewDevice, p.alertOnUnusualActivity, p.requireReauthForSensitiveActions) }
                    )
                    PolicySwitchRow(
                        title = "Confirmation sur actions sensibles",
                        subtitle = "Exiger code de sécurité ou ré-auth Google avant révocation / déconnexion",
                        checked = p.requireReauthForSensitiveActions,
                        onCheckedChange = { onUpdatePolicies(p.protectSuspiciousChanges, p.alertOnNewDevice, p.alertOnUnusualActivity, it) }
                    )
                    PolicySwitchRow(
                        title = "Alerter sur nouvel appareil",
                        subtitle = "Notifier dès qu'un appareil inconnu se connecte",
                        checked = p.alertOnNewDevice,
                        onCheckedChange = { onUpdatePolicies(p.protectSuspiciousChanges, it, p.alertOnUnusualActivity, p.requireReauthForSensitiveActions) }
                    )
                    PolicySwitchRow(
                        title = "Détection d'activité inhabituelle",
                        subtitle = "Signaler changements rapides de région ou rafales d'accès",
                        checked = p.alertOnUnusualActivity,
                        onCheckedChange = { onUpdatePolicies(p.protectSuspiciousChanges, p.alertOnNewDevice, it, p.requireReauthForSensitiveActions) }
                    )
                    OutlinedButton(onClick = onOpenPinConfig, modifier = Modifier.fillMaxWidth()) {
                        Text(if (p.pinHash.isNullOrBlank()) "Configurer un code de sécurité (PBKDF2)" else "Mettre à jour le code de sécurité")
                    }
                }
            }
        }

        item {
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ARCHITECTURE 2FA (TOTP / PASSKEY)", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Préparez votre compte pour l'authentification à deux facteurs. Seules les métadonnées publiques des facteurs et empreintes SHA-256 des codes de secours sont stockées.",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    TwoFactorMethod.entries.filter { it != TwoFactorMethod.NONE }.forEach { method ->
                        val isPreferred = p.preferredTwoFactorMethod == method
                        Surface(
                            color = if (isPreferred) DiscordBlurple.copy(alpha = 0.14f) else palette.inputBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isPreferred) DiscordBlurple else palette.border),
                            modifier = Modifier.fillMaxWidth().clickable { onPrepareTwoFactor(method, method.label) }
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(method.label, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(method.subtitle, color = palette.textMuted, fontSize = 11.sp)
                                }
                                if (isPreferred) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Sélectionné", tint = DiscordBlurple)
                                }
                            }
                        }
                    }

                    if (p.enrolledFactorsMeta.isNotEmpty()) {
                        Text("Facteurs préparés (${p.enrolledFactorsMeta.size})", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        p.enrolledFactorsMeta.forEach { f ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("• ${f.label} (${f.method.label})", color = palette.textSecondary, fontSize = 12.sp)
                                TextButton(onClick = { onRemoveTwoFactor(f.factorId) }) {
                                    Text("Retirer", color = DiscordRed, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onGenerateBackupCodes,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier.fillMaxWidth().testTag("generate_backup_codes_btn")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Générer codes de secours (${p.backupCodesRemainingCount} actif(s))",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityAlertCard(
    alert: SecurityAlert,
    palette: RavenPalette,
    onResolve: (String, SecurityAlertStatus, Boolean) -> Unit
) {
    val sevColor = when (alert.severity) {
        SecuritySeverity.CRITICAL, SecuritySeverity.HIGH -> DiscordRed
        SecuritySeverity.MEDIUM -> DiscordAmber
        else -> DiscordBlurple
    }
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (alert.status == SecurityAlertStatus.OPEN) sevColor else palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(alert.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(alert.status.label, color = sevColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(alert.description, color = palette.textSecondary, fontSize = 12.sp)
            if (alert.recommendedAction.isNotBlank()) {
                Text("Conseil : ${alert.recommendedAction}", color = palette.textMuted, fontSize = 11.sp)
            }
            if (alert.status == SecurityAlertStatus.OPEN) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onResolve(alert.id, SecurityAlertStatus.CONFIRMED_SAFE, false) }) {
                        Text("C'était moi", fontSize = 11.sp)
                    }
                    if (!alert.relatedSessionId.isNullOrBlank()) {
                        Button(
                            onClick = { onResolve(alert.id, SecurityAlertStatus.SECURED, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordRed)
                        ) {
                            Text("Révoquer session", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = ravenPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.textMuted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "Récemment"
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ms))
}
