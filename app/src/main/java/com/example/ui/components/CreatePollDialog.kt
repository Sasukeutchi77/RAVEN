package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun CreatePollDialog(
    onDismiss: () -> Unit,
    onCreatePoll: (question: String, options: List<String>, allowsMultiple: Boolean) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var option3 by remember { mutableStateOf("") }
    var option4 by remember { mutableStateOf("") }
    var allowsMultiple by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Créer un sondage", color = DiscordTextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("QUESTION DU SONDAGE", color = DiscordTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("Posez votre question...", color = DiscordTextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DiscordDarkInput,
                        unfocusedContainerColor = DiscordDarkInput,
                        focusedBorderColor = DiscordBlurple,
                        focusedTextColor = DiscordTextWhite,
                        unfocusedTextColor = DiscordTextLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("CHOIX DU SONDAGE", color = DiscordTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                PollOptionField(value = option1, onValueChange = { option1 = it }, placeholder = "Option 1 (ex. Oui)")
                Spacer(modifier = Modifier.height(6.dp))
                PollOptionField(value = option2, onValueChange = { option2 = it }, placeholder = "Option 2 (ex. Non)")
                Spacer(modifier = Modifier.height(6.dp))
                PollOptionField(value = option3, onValueChange = { option3 = it }, placeholder = "Option 3 (Optionnel)")
                Spacer(modifier = Modifier.height(6.dp))
                PollOptionField(value = option4, onValueChange = { option4 = it }, placeholder = "Option 4 (Optionnel)")

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Autoriser les choix multiples", color = DiscordTextLight, fontSize = 13.sp)
                    Switch(
                        checked = allowsMultiple,
                        onCheckedChange = { allowsMultiple = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DiscordBlurple)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validOptions = listOf(option1, option2, option3, option4).filter { it.isNotBlank() }
                    if (question.isNotBlank() && validOptions.size >= 2) {
                        onCreatePoll(question.trim(), validOptions, allowsMultiple)
                        onDismiss()
                    }
                },
                enabled = question.isNotBlank() && option1.isNotBlank() && option2.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)
            ) {
                Text("Publier le sondage", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = DiscordTextMuted)
            }
        }
    )
}

@Composable
private fun PollOptionField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = DiscordTextMuted, fontSize = 13.sp) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DiscordDarkInput,
            unfocusedContainerColor = DiscordDarkInput,
            focusedBorderColor = DiscordBlurple,
            focusedTextColor = DiscordTextWhite,
            unfocusedTextColor = DiscordTextLight
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
