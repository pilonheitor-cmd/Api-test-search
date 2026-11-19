package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.LookupUiState
import com.example.data.model.SearchMode
import com.example.data.remote.CpfValidator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    lookupState: LookupUiState,
    userDisplayName: String,
    onExecuteSearch: (SearchMode, String, String, String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by rememberSaveable { mutableStateOf(SearchMode.COMBINED) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var cpfInput by rememberSaveable { mutableStateOf("") }
    var phoneInput by rememberSaveable { mutableStateOf("") }

    val cpfPreview = remember(cpfInput) {
        if (CpfValidator.digitsOnly(cpfInput).isNotEmpty()) CpfValidator.validate(cpfInput) else null
    }
    val phoneDdd = remember(phoneInput) {
        CpfValidator.extractDdd(phoneInput)
    }

    val isSearching = lookupState is LookupUiState.Searching

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting & Integrity Header
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Escudo de consulta pública",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Olá, ${userDisplayName.substringBefore(" ")}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Consulta integrada: BrasilAPI, IBGE, Google Search e Gemini 4",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Mode Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Escolha o tipo de consulta",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchMode.entries.forEach { mode ->
                        val selected = selectedMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!isSearching) {
                                    selectedMode = mode
                                    onClearError()
                                }
                            },
                            label = { Text(mode.labelPt) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("mode_chip_${mode.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = selectedMode.subtitlePt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Search Form Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (selectedMode == SearchMode.NAME || selectedMode == SearchMode.COMBINED) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                onClearError()
                            },
                            label = { Text("Nome completo") },
                            placeholder = { Text("Ex.: João Carlos da Silva") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.PersonSearch,
                                    contentDescription = "Nome completo"
                                )
                            },
                            singleLine = true,
                            enabled = !isSearching,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("name_input")
                        )
                    }

                    if (selectedMode == SearchMode.CPF || selectedMode == SearchMode.COMBINED) {
                        OutlinedTextField(
                            value = cpfInput,
                            onValueChange = { raw ->
                                val digits = CpfValidator.digitsOnly(raw).take(11)
                                cpfInput = CpfValidator.formatCpf(digits)
                                onClearError()
                            },
                            label = { Text("CPF (11 dígitos)") },
                            placeholder = { Text("000.000.000-00") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Badge,
                                    contentDescription = "CPF"
                                )
                            },
                            singleLine = true,
                            enabled = !isSearching,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = if (selectedMode == SearchMode.COMBINED) ImeAction.Next else ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cpf_input")
                        )

                        AnimatedVisibility(visible = cpfPreview != null) {
                            cpfPreview?.let { res ->
                                val isComplete = res.digitsOnly.length == 11
                                Surface(
                                    color = if (res.valid) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (res.valid) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                            contentDescription = "Status de validação do CPF",
                                            tint = if (res.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = when {
                                                !isComplete -> "${res.digitsOnly.length}/11 dígitos informados"
                                                res.valid -> "CPF matematicamente válido · ${res.fiscalRegion}"
                                                else -> "Dígitos verificadores inválidos · ${res.fiscalRegion}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedMode == SearchMode.COMBINED) {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { raw ->
                                phoneInput = CpfValidator.formatPhone(raw)
                                onClearError()
                            },
                            label = { Text("Telefone com DDD") },
                            placeholder = { Text("(11) 99999-9999") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Phone,
                                    contentDescription = "Telefone com DDD"
                                )
                            },
                            supportingText = {
                                if (phoneDdd != null) {
                                    Text("DDD $phoneDdd será consultado na BrasilAPI")
                                } else {
                                    Text("Informe DDD + número para cruzar região geográfica")
                                }
                            },
                            singleLine = true,
                            enabled = !isSearching,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_input")
                        )
                    }

                    Button(
                        onClick = {
                            onExecuteSearch(selectedMode, nameInput, cpfInput, phoneInput)
                        },
                        enabled = !isSearching,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 15.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("search_submit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Iniciar consulta"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSearching) "Consultando fontes..." else "Consultar Dados Públicos",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Progress State
            AnimatedVisibility(visible = lookupState is LookupUiState.Searching) {
                if (lookupState is LookupUiState.Searching) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Processamento em tempo real",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { lookupState.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )
                            Text(
                                text = lookupState.currentStep,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error State
            AnimatedVisibility(visible = lookupState is LookupUiState.Error) {
                if (lookupState is LookupUiState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Erro na consulta",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = lookupState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // LGPD & Public Data Scope Disclosure
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Fontes conectadas e transparência (LGPD)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "O app consulta exclusivamente APIs abertas oficiais (BrasilAPI, Censo IBGE) e registros públicos indexados no Google Search. Campos protegidos por sigilo fiscal, bancário ou residencial (como Score, RG, Renda privada, Mãe/Pai, Veículos, Parentes e Vizinhos) nunca são inventados e exibem o status real ou o portal Gov.br/oficial indicado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
