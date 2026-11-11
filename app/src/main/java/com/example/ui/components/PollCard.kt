package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Poll
import com.example.ui.theme.*

@Composable
fun PollCard(
    poll: Poll,
    currentUserId: String,
    onVoteOption: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalVotes = poll.options.sumOf { it.votes.size }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DiscordDarkCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, DiscordDarkBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SONDAGE",
                    color = DiscordBlurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = poll.question,
                color = DiscordTextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            poll.options.forEach { option ->
                val hasVotedThis = option.votes.contains(currentUserId)
                val percentage = if (totalVotes > 0) (option.votes.size.toFloat() / totalVotes) else 0f
                val percentInt = (percentage * 100).toInt()

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasVotedThis) DiscordBlurple.copy(alpha = 0.2f) else DiscordDarkInput,
                    border = if (hasVotedThis) androidx.compose.foundation.BorderStroke(1.dp, DiscordBlurple) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onVoteOption(option.id) }
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Progress bar fill
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(percentage)
                                .background(if (hasVotedThis) DiscordBlurple.copy(alpha = 0.35f) else Color(0x22FFFFFF))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (hasVotedThis) DiscordBlurple else DiscordTextMuted,
                                            shape = CircleShape
                                        )
                                        .background(if (hasVotedThis) DiscordBlurple else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasVotedThis) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = option.text,
                                    color = if (hasVotedThis) DiscordTextWhite else DiscordTextLight,
                                    fontSize = 13.sp,
                                    fontWeight = if (hasVotedThis) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }

                            Text(
                                text = "$percentInt% (${option.votes.size})",
                                color = DiscordTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalVotes vote${if (totalVotes > 1) "s" else ""} • ${poll.durationLabel}",
                    color = DiscordTextMuted,
                    fontSize = 11.sp
                )
                if (poll.allowsMultiple) {
                    Text("Choix multiples", color = DiscordTextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
