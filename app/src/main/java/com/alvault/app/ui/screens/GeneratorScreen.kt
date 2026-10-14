@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alvault.app.core.security.GeneratorOptions
import com.alvault.app.core.security.PasswordGenerator
import com.alvault.app.ui.components.StrengthMeter
import com.alvault.app.ui.theme.PasswordTextStyle
import com.alvault.app.ui.vm.VaultViewModel
import kotlin.math.roundToInt

/** Tab "Generator": membuat password acak (CSPRNG, rejection sampling) tanpa menyimpan apa pun. */
@Composable
fun GeneratorScreen(vaultVm: VaultViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Generator password") })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            GeneratorPanel(onUse = null, onCopy = { vaultVm.copyPassword(it) })
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Password dibuat di perangkat ini dan tidak disimpan atau dikirim ke mana pun. " +
                    "Salinan di clipboard dibersihkan otomatis setelah 30 detik.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Panel generator yang dipakai di tab Generator dan di dialog formulir entri.
 * [onUse] null = tombol "Gunakan" disembunyikan.
 */
@Composable
fun GeneratorPanel(
    onUse: ((String) -> Unit)?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var nonce by remember { mutableIntStateOf(0) }
    // Dihitung ulang hanya saat opsi berubah atau "Acak ulang" ditekan, bukan di setiap recomposition.
    val password = remember(options, nonce) { PasswordGenerator.generate(options) }
    val enabledCount = options.enabledSets.size

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = password,
                    style = PasswordTextStyle,
                    modifier = Modifier.fillMaxWidth()
                )
                StrengthMeter(password)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onCopy(password) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Salin")
                    }
                    OutlinedButton(onClick = { nonce++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Acak ulang")
                    }
                }
                if (onUse != null) {
                    Button(onClick = { onUse(password) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Gunakan password ini")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Panjang", style = MaterialTheme.typography.titleSmall)
            Text("${options.length} karakter", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = options.length.toFloat(),
            onValueChange = { options = options.copy(length = it.roundToInt()) },
            valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
            // Satu langkah per karakter: (42 - 8) selang berarti 33 titik di antara ujung-ujungnya.
            steps = PasswordGenerator.MAX_LENGTH - PasswordGenerator.MIN_LENGTH - 1
        )

        // Minimal satu jenis karakter harus aktif: sakelar terakhir yang menyala tidak bisa dimatikan.
        SetSwitch("Huruf besar", "A–Z", options.upper, enabled = !options.upper || enabledCount > 1) {
            options = options.copy(upper = it)
        }
        SetSwitch("Huruf kecil", "a–z", options.lower, enabled = !options.lower || enabledCount > 1) {
            options = options.copy(lower = it)
        }
        SetSwitch("Angka", "0–9", options.numbers, enabled = !options.numbers || enabledCount > 1) {
            options = options.copy(numbers = it)
        }
        SetSwitch(
            "Simbol aman",
            PasswordGenerator.SAFE_SYMBOLS,
            options.safeSymbols,
            enabled = !options.safeSymbols || enabledCount > 1
        ) { options = options.copy(safeSymbols = it) }
        SetSwitch(
            "Simbol tambahan",
            PasswordGenerator.EXTENDED_SYMBOLS,
            options.extendedSymbols,
            enabled = !options.extendedSymbols || enabledCount > 1
        ) { options = options.copy(extendedSymbols = it) }
    }
}

@Composable
private fun SetSwitch(
    title: String,
    sample: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = sample,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
