package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.model.Server
import com.example.ui.theme.*

@Composable
fun ServerOnboardingRulesOverlay(
    server: Server,
    onAcceptRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var checkedRules by remember(server.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.chatBg)
            .padding(20.dp)
            .testTag("server_onboarding_rules_gate"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = palette.cardBg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(server.iconBgColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(server.iconText.take(2), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Bienvenue sur ${server.name}", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Acceptez le règlement pour débloquer les salons", color = palette.textMuted, fontSize = 12.sp)
                    }
                }

                Text(server.homeWelcomeMessage, color = palette.textSecondary, fontSize = 13.sp)

                Surface(color = palette.inputBg, shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RÈGLEMENT OFFICIEL", color = DiscordBlurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        server.serverRulesList.forEachIndexed { idx, rule ->
                            Text("${idx + 1}. $rule", color = palette.textPrimary, fontSize = 13.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { checkedRules = !checkedRules }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checkedRules, onCheckedChange = { checkedRules = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("J'ai lu et j'accepte le règlement de cette communauté", color = palette.textPrimary, fontSize = 13.sp)
                }

                Button(
                    onClick = onAcceptRules,
                    enabled = checkedRules,
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("accept_server_rules_btn")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accéder aux salons (+20 XP)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MessageTipOrbsDialog(
    message: Message,
    currentOrbsBalance: Int,
    onTipOrbs: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val amounts = listOf(10, 25, 50, 100)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Toll, contentDescription = null, tint = DiscordAmber)
                Text("Offrir des Orbs", color = DiscordTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Récompensez ${message.sender.name} pour ce message utile (Votre solde : $currentOrbsBalance Orbs).",
                    color = DiscordTextMuted,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    amounts.forEach { amt ->
                        Button(
                            onClick = {
                                onTipOrbs(amt)
                                onDismiss()
                            },
                            enabled = currentOrbsBalance >= amt,
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$amt", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer", color = DiscordTextMuted) }
        }
    )
}

@Composable
fun ServerOrbsTreasuryDialog(
    server: Server,
    currentOrbsBalance: Int,
    onDonateOrbs: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val amounts = listOf(25, 50, 100, 250)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = DiscordAmber)
                Text("Banque d'Orbs • ${server.name}", color = DiscordTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = DiscordDarkInput, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cagnotte du serveur", color = DiscordTextMuted, fontSize = 11.sp)
                            Text("${server.treasuryOrbs} Orbs", color = DiscordAmber, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Surface(color = DiscordBlurple.copy(alpha = 0.2f), shape = CircleShape) {
                            Text(
                                "Niveau ${server.boostLevel} (${server.boostCount} boosts)",
                                color = DiscordBlurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Text("Votre solde : $currentOrbsBalance Orbs. Chaque tranche de 100 Orbs finance +1 Boost serveur !", color = DiscordTextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    amounts.forEach { amt ->
                        Button(
                            onClick = {
                                onDonateOrbs(amt)
                                onDismiss()
                            },
                            enabled = currentOrbsBalance >= amt,
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+$amt", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer", color = DiscordTextMuted) }
        }
    )
}
