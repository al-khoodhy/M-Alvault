@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvault.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvault.app.core.model.VaultCategories
import com.alvault.app.core.vault.VaultQuery
import com.alvault.app.ui.UiLogic
import com.alvault.app.ui.components.ConfirmDialog
import com.alvault.app.ui.components.SecretField
import com.alvault.app.ui.components.StrengthMeter
import com.alvault.app.ui.vm.VaultViewModel

/**
 * Formulir tambah/ubah entri. [id] null = entri baru.
 * Isian hanya hidup di `remember` (bukan `rememberSaveable`) supaya password tidak pernah masuk ke saved-state Android.
 */
@Composable
fun ItemEditScreen(vaultVm: VaultViewModel, id: String?, onClose: () -> Unit) {
    val ui by vaultVm.ui.collectAsStateWithLifecycle()
    val existing = remember(id) { id?.let { vaultVm.find(it) } }
    // Web menyimpan literal "Kustom..." sebagai kategori; di formulir ini diperlakukan sebagai "belum dipilih".
    val initialCategory = existing?.category?.takeIf { it.isNotBlank() && it != VaultCategories.CUSTOM_LABEL }

    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var customCategory by remember { mutableStateOf("") }

    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    val categoryOptions = remember(ui.items) {
        VaultQuery.categoryOptions(ui.items).filter { it != VaultCategories.CUSTOM_LABEL } + VaultCategories.CUSTOM_LABEL
    }

    val dirty = title != existing?.title.orEmpty() ||
        username != existing?.username.orEmpty() ||
        password != existing?.password.orEmpty() ||
        url != existing?.url.orEmpty() ||
        notes != existing?.notes.orEmpty() ||
        selectedCategory != initialCategory ||
        customCategory.isNotEmpty()

    fun requestClose() {
        if (dirty && !saving) showDiscard = true else onClose()
    }

    fun save() {
        showErrors = true
        if (UiLogic.itemError(title) != null) return
        saving = true
        val item = UiLogic.buildItem(
            existing = existing,
            title = title,
            username = username,
            password = password,
            category = UiLogic.resolveCategory(selectedCategory, customCategory),
            url = url,
            notes = notes,
            now = System.currentTimeMillis()
        )
        vaultVm.save(item) { ok ->
            saving = false
            if (ok) onClose()
        }
    }

    BackHandler { requestClose() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (existing == null) "Entri baru" else "Ubah entri") },
            navigationIcon = {
                IconButton(onClick = { requestClose() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup")
                }
            },
            actions = {
                TextButton(onClick = { save() }, enabled = !saving) { Text("Simpan") }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Judul") },
                singleLine = true,
                isError = showErrors && UiLogic.itemError(title) != null,
                supportingText = {
                    val error = if (showErrors) UiLogic.itemError(title) else null
                    if (error != null) Text(error)
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username / email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            SecretField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                modifier = Modifier.fillMaxWidth(),
                monospace = true,
                trailingExtra = {
                    IconButton(onClick = { showGenerator = true }) {
                        Icon(Icons.Filled.Casino, contentDescription = "Buat password acak")
                    }
                }
            )
            StrengthMeter(password)

            // Pemilih kategori: kolom baca-saja + lapisan transparan penangkap ketukan.
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedCategory.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kategori") },
                    placeholder = { Text("Tanpa kategori") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { categoryMenu = true }
                )
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Tanpa kategori") },
                        onClick = {
                            selectedCategory = null
                            categoryMenu = false
                        }
                    )
                    categoryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedCategory = option
                                categoryMenu = false
                            }
                        )
                    }
                }
            }
            if (selectedCategory == VaultCategories.CUSTOM_LABEL) {
                OutlinedTextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama kategori kustom") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL (opsional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Catatan (opsional)") },
                minLines = 3,
                maxLines = 8
            )
        }
    }

    if (showGenerator) {
        AlertDialog(
            onDismissRequest = { showGenerator = false },
            title = { Text("Generator password") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    GeneratorPanel(
                        onUse = {
                            password = it
                            showGenerator = false
                        },
                        onCopy = { vaultVm.copyPassword(it) }
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGenerator = false }) { Text("Tutup") } }
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Buang perubahan?",
            text = "Perubahan pada entri ini belum disimpan.",
            confirmLabel = "Buang",
            destructive = true,
            onConfirm = {
                showDiscard = false
                onClose()
            },
            onDismiss = { showDiscard = false }
        )
    }
}
