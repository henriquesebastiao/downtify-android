package com.henriquesebastiao.downtify.feature.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.BuildConfig
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DowntifyTheme {
                Surface(Modifier.fillMaxSize()) {
                    val model: DiagnosticsViewModel = hiltViewModel()
                    val state by model.state.collectAsStateWithLifecycle()
                    val save =
                        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                            uri?.let(model::export)
                        }
                    DiagnosticsScreen(state, model::run, {
                        save.launch("downtify-diagnostic-${BuildConfig.VERSION_NAME}.txt")
                    }, ::finish)
                }
            }
        }
    }
}

internal data class DiagnosticsUiState(
    val running: Boolean = false,
    val exporting: Boolean = false,
    val report: DiagnosticReport? = null,
    val failed: Boolean = false,
    val exported: Boolean = false,
)

@HiltViewModel
internal class DiagnosticsViewModel @Inject constructor(private val runner: DiagnosticsRunner) : ViewModel() {
    private val mutable = MutableStateFlow(DiagnosticsUiState())
    val state = mutable.asStateFlow()

    @Suppress("TooGenericExceptionCaught")
    fun run() {
        if (mutable.value.running || mutable.value.exporting) return
        mutable.value = DiagnosticsUiState(running = true)
        viewModelScope.launch {
            try {
                mutable.value = DiagnosticsUiState(report = runner.run())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutable.value = DiagnosticsUiState(failed = true)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun export(uri: android.net.Uri) {
        val report = mutable.value.report ?: return
        if (mutable.value.running || mutable.value.exporting) return
        mutable.value = mutable.value.copy(exporting = true, exported = false, failed = false)
        viewModelScope.launch {
            try {
                runner.export(uri, report)
                mutable.value = mutable.value.copy(exporting = false, exported = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(exporting = false, failed = true)
            }
        }
    }
}

@Composable
internal fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onRun: () -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
        Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.diagnostics_description))
        Button(onClick = onRun, enabled = !state.running && !state.exporting) {
            Text(stringResource(if (state.running) R.string.diagnostics_running else R.string.diagnostics_run))
        }
        state.report?.let { report ->
            report.checks.forEach { check ->
                val label = when (check.kind) {
                    DiagnosticKind.UI -> R.string.diagnostics_ui
                    DiagnosticKind.STORAGE -> R.string.diagnostics_storage
                    DiagnosticKind.SERVER -> R.string.diagnostics_server
                }
                Text("${stringResource(label)} : ${check.status} (${check.elapsedMs} ms)")
                check.metrics.forEach { (metric, value) -> Text("${metric.name} : $value") }
            }
            report.metrics.forEach { (metric, value) -> Text("${metric.name} : $value") }
        }
        OutlinedButton(onClick = onExport, enabled = state.report != null && !state.running && !state.exporting) {
            Text(stringResource(R.string.diagnostics_export))
        }
        if (state.failed) Text(stringResource(R.string.diagnostics_failed))
        if (state.exported) Text(stringResource(R.string.diagnostics_saved))
        Text(stringResource(R.string.diagnostics_privacy), style = MaterialTheme.typography.bodySmall)
    }
}
