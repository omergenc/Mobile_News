package com.omergenc.mobilenews.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.omergenc.mobilenews.model.SourceConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = vm.settings
    var anthropicKey by remember { mutableStateOf(s.anthropicApiKey) }
    var youtubeKey by remember { mutableStateOf(s.youtubeApiKey) }
    var telegramBridge by remember { mutableStateOf(s.telegramBridgeUrl) }
    var notifications by remember { mutableStateOf(s.notificationsEnabled) }
    var interval by remember { mutableStateOf(s.refreshIntervalMinutes) }

    fun saveAndClose() {
        vm.saveSettings(anthropicKey, youtubeKey, telegramBridge, notifications, interval)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("API anahtarları cihazda şifreli saklanır (Android Keystore).", style = MaterialTheme.typography.bodySmall)
            }
            item {
                OutlinedTextField(
                    value = anthropicKey,
                    onValueChange = { anthropicKey = it },
                    label = { Text("Anthropic API anahtarı (özetler için, opsiyonel)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = youtubeKey,
                    onValueChange = { youtubeKey = it },
                    label = { Text("YouTube Data API anahtarı (opsiyonel)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = telegramBridge,
                    onValueChange = { telegramBridge = it },
                    label = { Text("Telegram RSS köprüsü (ör. https://rsshub.ornek.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Önemli gelişmeler için bildirim", modifier = Modifier.weight(1f))
                    Switch(checked = notifications, onCheckedChange = { notifications = it })
                }
            }
            item {
                Column {
                    Text("Arka plan tarama aralığı")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { m ->
                            FilterChip(selected = interval == m, onClick = { interval = m }, label = { Text("$m dk") })
                        }
                    }
                }
            }
            item {
                Button(onClick = { saveAndClose() }, modifier = Modifier.fillMaxWidth()) { Text("Kaydet") }
            }
            item {
                HorizontalDivider()
                Text("Kaynaklar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Liste app/src/main/assets/sources.json dosyasından gelir. Açma/kapama hemen uygulanır.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(vm.sources, key = { it.id }) { source -> SourceRow(vm, source) }
        }
    }
}

@Composable
private fun SourceRow(vm: MainViewModel, source: SourceConfig) {
    var enabled by remember(source.id) { mutableStateOf(vm.settings.isSourceEnabled(source)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(source.name)
            Text(
                listOfNotNull(source.type.uppercase(), source.note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                vm.settings.setSourceEnabled(source, it)
            },
        )
    }
}
