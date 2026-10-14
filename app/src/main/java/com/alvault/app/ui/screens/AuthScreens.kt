package com.alvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withResumed
import com.alvault.app.ui.UiLogic
import com.alvault.app.ui.components.BrandHeader
import com.alvault.app.ui.components.BusyButton
import com.alvault.app.ui.components.ResetAllDialog
import com.alvault.app.ui.components.SecretField
import com.alvault.app.ui.components.StatusBanner
import com.alvault.app.ui.components.StrengthMeter
import com.alvault.app.ui.vm.AuthUi
import com.alvault.app.ui.vm.AuthViewModel
import com.alvault.app.ui.vm.StatusKind
import kotlinx.coroutines.delay

/** Registrasi lokal pertama kali: email + master password. Tanpa server, tanpa internet. */
@Composable
fun RegisterScreen(vm: AuthViewModel, ui: AuthUi) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun submit() {
        focus.clearFocus()
        vm.register(email, password, confirm)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        BrandHeader(
            subtitle = "Buat brankas lokal. Semua data dienkripsi di perangkat ini dan tidak butuh server."
        )
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                if (ui.error != null) vm.clearError()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !ui.busy,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            supportingText = { Text("Hanya dipakai sebagai bagian dari kunci enkripsi dan identitas sinkronisasi.") }
        )
        SecretField(
            value = password,
            onValueChange = {
                password = it
                if (ui.error != null) vm.clearError()
            },
            label = "Master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !ui.busy,
            supportingText = "Minimal 8 karakter. Makin panjang makin baik."
        )
        StrengthMeter(password)
        SecretField(
            value = confirm,
            onValueChange = {
                confirm = it
                if (ui.error != null) vm.clearError()
            },
            label = "Ulangi master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !ui.busy,
            imeAction = ImeAction.Done,
            onImeAction = { if (understood) submit() }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !ui.busy) { understood = !understood },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = understood, onCheckedChange = { understood = it }, enabled = !ui.busy)
            Text(
                text = "Saya paham: master password tidak disimpan di mana pun dan tidak bisa dipulihkan. " +
                    "Jika lupa, brankas tidak bisa dibuka lagi.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        ui.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        BusyButton(
            text = "Buat brankas",
            busyText = "Menurunkan kunci…",
            busy = ui.busy,
            enabled = understood && email.isNotBlank() && password.isNotEmpty() && confirm.isNotEmpty(),
            onClick = { submit() }
        )
    }
}

/** Buka kunci dengan master password atau biometrik. */
@Composable
fun UnlockScreen(vm: AuthViewModel, ui: AuthUi, activity: FragmentActivity) {
    var password by remember { mutableStateOf("") }
    var showReset by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // Hitung mundur jeda setelah terlalu banyak salah password.
    val lockedUntil = ui.lockedUntil
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val remaining = (lockedUntil - now).coerceAtLeast(0L)
    val lockedOut = remaining > 0L

    // Sekali per tampilan layar: minta biometrik otomatis begitu aplikasi benar-benar terlihat (RESUMED).
    var autoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(ui.biometricReady) {
        if (ui.biometricReady && !autoPrompted) {
            autoPrompted = true
            activity.withResumed { }
            vm.unlockWithBiometric(activity)
        }
    }

    fun submit() {
        focus.clearFocus()
        vm.unlock(password)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        BrandHeader(
            subtitle = if (ui.email.isNotEmpty()) {
                "Brankas terkunci untuk ${UiLogic.maskEmail(ui.email)}"
            } else {
                "Brankas terkunci"
            }
        )
        Spacer(Modifier.height(8.dp))

        SecretField(
            value = password,
            onValueChange = {
                password = it
                if (ui.error != null) vm.clearError()
            },
            label = "Master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !ui.busy,
            isError = ui.error != null,
            imeAction = ImeAction.Done,
            onImeAction = { if (!lockedOut && password.isNotEmpty()) submit() }
        )

        ui.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (lockedOut) {
            StatusBanner(
                text = "Terlalu banyak percobaan. Coba lagi dalam ${UiLogic.formatRemaining(remaining)}.",
                kind = StatusKind.WARN
            )
        }

        BusyButton(
            text = "Buka brankas",
            busyText = "Memeriksa…",
            busy = ui.busy,
            enabled = !lockedOut && password.isNotEmpty(),
            onClick = { submit() }
        )

        if (ui.biometricReady) {
            OutlinedButton(
                onClick = { vm.unlockWithBiometric(activity) },
                enabled = !ui.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text("Buka dengan biometrik")
            }
        }

        TextButton(
            onClick = { showReset = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("Lupa master password?") }
    }

    if (showReset) {
        ResetAllDialog(
            onConfirm = {
                showReset = false
                vm.resetAll()
            },
            onDismiss = { showReset = false }
        )
    }
}
