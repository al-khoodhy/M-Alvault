@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.vault.VaultQuery
import com.alvault.app.ui.components.Avatar
import com.alvault.app.ui.components.EmptyState
import com.alvault.app.ui.components.StatusBanner
import com.alvault.app.ui.vm.StatusKind
import com.alvault.app.ui.vm.VaultViewModel

/** Tab "Brankas": pencarian, filter kategori, dan daftar entri. */
@Composable
fun VaultListScreen(
    vaultVm: VaultViewModel,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onLock: () -> Unit
) {
    val ui by vaultVm.ui.collectAsStateWithLifecycle()
    // Teks pencarian disimpan lokal agar kursor tidak melompat; VM hanya menerima salinannya.
    var query by remember { mutableStateOf(ui.query) }
    // distinctBy: kunci LazyColumn harus unik, dan id dari berkas impor/cloud tidak dijamin unik.
    val visible = remember(ui.items, ui.query, ui.category) { ui.visible.distinctBy { it.id } }
    val categories = remember(ui.items) { VaultQuery.usedCategories(ui.items) }
    val loadError = ui.loadError

    // Kategori yang terpilih tetapi sudah tidak dipakai entri mana pun (mis. setelah hapus): kembali ke "Semua".
    LaunchedEffect(categories, ui.category) {
        val selected = ui.category
        if (selected != null && selected !in categories) vaultVm.setCategory(null)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Brankas") },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Filled.Lock, contentDescription = "Kunci sekarang")
                    }
                }
            )

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    vaultVm.setQuery(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Cari judul, username, atau URL") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            vaultVm.setQuery("")
                        }) { Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )

            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = ui.category == null,
                            onClick = { vaultVm.setCategory(null) },
                            label = { Text("Semua") }
                        )
                    }
                    items(categories) { category ->
                        FilterChip(
                            selected = ui.category == category,
                            onClick = { vaultVm.setCategory(if (ui.category == category) null else category) },
                            label = { Text(category) }
                        )
                    }
                }
            }

            when {
                !ui.loaded && ui.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                loadError != null -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusBanner(text = loadError, kind = StatusKind.ERROR)
                        Button(onClick = { vaultVm.load() }) { Text("Coba lagi") }
                    }
                }

                ui.items.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.Shield,
                        title = "Brankas masih kosong",
                        body = "Ketuk Tambah untuk menyimpan akun pertama, atau impor backup .aegis dari menu Pengaturan."
                    )
                }

                visible.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "Tidak ada hasil",
                        body = "Coba kata kunci lain atau pilih kategori \"Semua\"."
                    )
                }

                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(visible, key = { it.id }) { item ->
                            ItemRow(
                                item = item,
                                onOpen = { onOpen(item.id) },
                                onCopyPassword = { vaultVm.copyPassword(item.password) }
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Tambah") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ItemRow(item: VaultItem, onOpen: () -> Unit, onCopyPassword: () -> Unit) {
    val subtitle = listOfNotNull(
        item.username.takeIf { it.isNotBlank() },
        item.category?.takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifEmpty { "—" }

    ListItem(
        headlineContent = {
            Text(
                text = item.title.ifBlank { "(tanpa judul)" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(text = subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Avatar(title = item.title, seed = item.category ?: item.title) },
        trailingContent = {
            if (item.password.isNotEmpty()) {
                IconButton(onClick = onCopyPassword) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Salin password ${item.title}")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onOpen)
    )
}
