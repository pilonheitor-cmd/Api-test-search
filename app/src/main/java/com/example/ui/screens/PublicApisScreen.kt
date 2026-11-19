package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.remote.CepResponse
import com.example.data.remote.CnpjResponse
import com.example.data.remote.DddResponse
import com.example.data.remote.IbgeNomeResponse
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.PublicToolState

@Composable
fun PublicApisScreen(
    cnpjState: PublicToolState<CnpjResponse>,
    cepState: PublicToolState<CepResponse>,
    dddState: PublicToolState<DddResponse>,
    ibgeState: PublicToolState<List<IbgeNomeResponse>>,
    onLookupCnpj: (String) -> Unit,
    onLookupCep: (String) -> Unit,
    onLookupDdd: (String) -> Unit,
    onLookupIbge: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var cnpjInput by rememberSaveable { mutableStateOf("") }
    var cepInput by rememberSaveable { mutableStateOf("") }
    var dddInput by rememberSaveable { mutableStateOf("") }
    var ibgeInput by rememberSaveable { mutableStateOf("") }

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
            Text(
                text = "Consultas Diretas às APIs Públicas do Brasil",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Conecte-se em tempo real à BrasilAPI (Receita Federal CNPJ, CEP v2, DDD) e ao Censo de Nomes do IBGE.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. CNPJ Tool
            PublicToolCard(
                title = "CNPJ / MEI (BrasilAPI · Receita Federal)",
                icon = Icons.Filled.Business,
                input = cnpjInput,
                onInputChange = { cnpjInput = it },
                label = "CNPJ (14 dígitos)",
                placeholder = "Ex.: 00000000000191",
                keyboardType = KeyboardType.Number,
                isLoading = cnpjState is PublicToolState.Loading,
                buttonTag = "tool_cnpj_button",
                inputTag = "tool_cnpj_input",
                onSubmit = { onLookupCnpj(cnpjInput) }
            ) {
                when (cnpjState) {
                    is PublicToolState.Success -> {
                        val c = cnpjState.data
                        Text(
                            text = c.razao_social ?: "Razão Social não informada",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!c.nome_fantasia.isNullOrBlank()) {
                            Text("Nome Fantasia: ${c.nome_fantasia}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Situação: ${c.descricao_situacao_cadastral ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text("Atividade (CNAE): ${c.cnae_fiscal_descricao ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Endereço: ${c.logradouro.orEmpty()}, ${c.numero.orEmpty()} — ${c.municipio.orEmpty()}/${c.uf.orEmpty()} (CEP ${c.cep.orEmpty()})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!c.ddd_telefone_1.isNullOrBlank()) {
                            Text("Telefone público: ${c.ddd_telefone_1}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!c.email.isNullOrBlank()) {
                            Text("E-mail público: ${c.email}", style = MaterialTheme.typography.bodySmall)
                        }
                        val socios = c.qsa.orEmpty().mapNotNull { it.nome_socio }.take(4)
                        if (socios.isNotEmpty()) {
                            Text("Sócios (QSA): ${socios.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is PublicToolState.Error -> {
                        Text(cnpjState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
            }

            // 2. CEP Tool
            PublicToolCard(
                title = "CEP & Endereço (BrasilAPI v2)",
                icon = Icons.Filled.LocationOn,
                input = cepInput,
                onInputChange = { cepInput = it },
                label = "CEP (8 dígitos)",
                placeholder = "Ex.: 01001000",
                keyboardType = KeyboardType.Number,
                isLoading = cepState is PublicToolState.Loading,
                buttonTag = "tool_cep_button",
                inputTag = "tool_cep_input",
                onSubmit = { onLookupCep(cepInput) }
            ) {
                when (cepState) {
                    is PublicToolState.Success -> {
                        val cep = cepState.data
                        Text(
                            text = "${cep.street.orEmpty()} — ${cep.neighborhood.orEmpty()}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${cep.city.orEmpty()} / ${cep.state.orEmpty()} · CEP ${cep.cep.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily)
                        )
                    }
                    is PublicToolState.Error -> {
                        Text(cepState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
            }

            // 3. DDD Tool
            PublicToolCard(
                title = "DDD & Municípios (BrasilAPI)",
                icon = Icons.Filled.PhoneInTalk,
                input = dddInput,
                onInputChange = { dddInput = it },
                label = "DDD (2 dígitos)",
                placeholder = "Ex.: 11",
                keyboardType = KeyboardType.Number,
                isLoading = dddState is PublicToolState.Loading,
                buttonTag = "tool_ddd_button",
                inputTag = "tool_ddd_input",
                onSubmit = { onLookupDdd(dddInput) }
            ) {
                when (dddState) {
                    is PublicToolState.Success -> {
                        val d = dddState.data
                        Text(
                            text = "Estado: ${d.state ?: "-"} (${d.cities?.size ?: 0} municípios)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = d.cities.orEmpty().take(12).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is PublicToolState.Error -> {
                        Text(dddState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
            }

            // 4. IBGE Nomes Tool
            PublicToolCard(
                title = "Censo de Nomes (IBGE)",
                icon = Icons.Filled.People,
                input = ibgeInput,
                onInputChange = { ibgeInput = it },
                label = "Primeiro nome",
                placeholder = "Ex.: Maria",
                keyboardType = KeyboardType.Text,
                isLoading = ibgeState is PublicToolState.Loading,
                buttonTag = "tool_ibge_button",
                inputTag = "tool_ibge_input",
                onSubmit = { onLookupIbge(ibgeInput) }
            ) {
                when (ibgeState) {
                    is PublicToolState.Success -> {
                        val first = ibgeState.data.firstOrNull()
                        if (first == null) {
                            Text("Sem ocorrências no Censo IBGE.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            val total = first.res.orEmpty().sumOf { it.frequencia ?: 0L }
                            Text(
                                text = "${first.nome ?: ibgeInput}: $total registros no Brasil",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            first.res.orEmpty().takeLast(4).forEach { periodo ->
                                Text(
                                    text = "Período ${periodo.periodo.orEmpty()}: ${periodo.frequencia ?: 0L}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily)
                                )
                            }
                        }
                    }
                    is PublicToolState.Error -> {
                        Text(ibgeState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PublicToolCard(
    title: String,
    icon: ImageVector,
    input: String,
    onInputChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    isLoading: Boolean,
    buttonTag: String,
    inputTag: String,
    onSubmit: () -> Unit,
    resultContent: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(inputTag)
                )
                Button(
                    onClick = onSubmit,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag(buttonTag)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Consultar"
                        )
                    }
                }
            }
            resultContent()
        }
    }
}
