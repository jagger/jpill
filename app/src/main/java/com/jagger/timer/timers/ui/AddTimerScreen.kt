package com.jagger.timer.timers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.core.data.local.entity.TimerType
import com.jagger.timer.timers.viewmodel.AddTimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTimerScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddTimerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Timer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Timer Name") },
                modifier = Modifier.fillMaxWidth()
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimerType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TimerType.entries.size),
                        onClick = { viewModel.updateType(type) },
                        selected = uiState.type == type
                    ) {
                        Text(if (type == TimerType.COUNTDOWN) "One-shot" else "Repeating")
                    }
                }
            }

            OutlinedTextField(
                value = uiState.durationMinutes,
                onValueChange = { viewModel.updateDuration(it) },
                label = { Text("Duration (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { viewModel.saveTimer() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Start Tracking Timer")
            }
        }
    }
}
