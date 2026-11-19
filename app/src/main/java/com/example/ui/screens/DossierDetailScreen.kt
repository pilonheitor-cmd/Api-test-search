package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ConsultaRecord
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusFoundBgDark
import com.example.ui.theme.StatusFoundBgLight
import com.example.ui.theme.StatusFoundTextDark
import com.example.ui.theme.StatusFoundTextLight
import com.example.ui.theme.StatusProtectedBgDark
import com.example.ui.theme.StatusProtectedBgLight
import com.example.ui.theme.StatusProtectedTextDark
import com.example.ui.theme.StatusProtectedTextLight
import com.example.ui.viewmodel.ThinkingUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DossierDetailScreen(
    record: ConsultaRecord,
    thinkingState: ThinkingUiState,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onRunDeepThinking: () -> Unit,
    onDelete: () -> Unit
) {
    BackHandler { onBack() }

    val uriHandler = LocalUriHandler.current
    val dark = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = record.queryName.ifBlank { record.queryCpf.ifBlank { "Dossiê Público" } },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = record.resolvedSearchMode().labelPt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("dossier_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.testTag("dossier_pin_button")
                    ) {
                        Icon(
                            imageVector = if (record.pinned) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (record.pinned) "Remover dos favoritos" else "Fixar nos favoritos",
                            tint = if (record.pinned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("dossier_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Excluir consulta",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Resumo Gemini Grounding",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Síntese das Fontes Públicas (Google Search)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = record.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Official Brazilian APIs Snapshot (CPF Math, DDD, IBGE)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Validações Oficiais e Censo",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        MetricLine("Região Fiscal CPF", record.cpfRegion.ifBlank { "Não informado" })
                        MetricLine("Validade Dígitos CPF", if (record.queryCpf.isBlank()) "Não informado" else if (record.cpfValid) "Válido (Módulo 11)" else "Inválido")
                        MetricLine("DDD (BrasilAPI)", record.dddRegion.ifBlank { "Não informado" })
                        MetricLine(
                            "Censo IBGE (1º nome)",
                            if (record.ibgeFrequency > 0L) "${record.ibgeFrequency} registros no Brasil" else "Sem contagem IBGE"
                        )
                    }
                }

                // 19 Requested Fields grouped clearly
                val d = record.dossier
                DossierSectionCard(
                    title = "Identificação & Registro Civil",
                    fields = listOf(
                        "Nome" to d.nome,
                        "CPF" to d.cpf,
                        "RG" to d.rg,
                        "Nascimento" to d.nascimento,
                        "Sexo" to d.sexo,
                        "Estado Civil" to d.estadoCivil,
                        "Escolaridade" to d.escolaridade
                    ),
                    dark = dark
                )

                DossierSectionCard(
                    title = "Filiação & Vínculos",
                    fields = listOf(
                        "Nome da Mãe" to d.nomeMae,
                        "Nome do Pai" to d.nomePai,
                        "Parentes" to d.parentes,
                        "Vizinhos" to d.vizinhos
                    ),
                    dark = dark
                )

                DossierSectionCard(
                    title = "Contato & Localização",
                    fields = listOf(
                        "Telefone" to d.telefone,
                        "E-mail" to d.email,
                        "Endereço Principal" to d.enderecoPrincipal,
                        "Endereço Secundário" to d.enderecoSecundario
                    ),
                    dark = dark
                )

                DossierSectionCard(
                    title = "Financeiro, Profissão & Patrimônio",
                    fields = listOf(
                        "Score" to d.score,
                        "Renda Mensal" to d.rendaMensal,
                        "Profissão" to d.profissao,
                        "Veículo" to d.veiculo
                    ),
                    dark = dark
                )

                // Deep Thinking Section (Gemini 4 Carbon · ThinkingLevel.HIGH)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = "Análise profunda com raciocínio alto",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Análise Profunda (Gemini 4 Carbon · Thinking HIGH)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val displayedAnalysis = when (thinkingState) {
                            is ThinkingUiState.Streaming -> thinkingState.text
                            is ThinkingUiState.Success -> thinkingState.text
                            else -> record.thinkingAnalysis
                        }

                        if (displayedAnalysis.isNotBlank()) {
                            Text(
                                text = displayedAnalysis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "Execute o cruzamento profundo com ThinkingLevel.HIGH para verificar coerência entre região fiscal do CPF, DDD, IBGE e orientações oficiais Gov.br.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (thinkingState is ThinkingUiState.Error) {
                            Text(
                                text = thinkingState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        val isStreaming = thinkingState is ThinkingUiState.Streaming
                        Button(
                            onClick = onRunDeepThinking,
                            enabled = !isStreaming,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_thinking_button")
                        ) {
                            if (isStreaming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Raciocinando em nível HIGH...")
                            } else {
                                Text(
                                    if (displayedAnalysis.isBlank()) "Gerar Análise Profunda (Thinking HIGH)"
                                    else "Atualizar Análise Profunda"
                                )
                            }
                        }
                    }
                }

                // Grounded Sources Card
                val sources = record.parsedSources()
                if (sources.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Fontes Verificadas (${sources.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            sources.forEach { source ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            runCatching { uriHandler.openUri(source.url) }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = source.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = source.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Abrir fonte externa",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun DossierSectionCard(
    title: String,
    fields: List<Pair<String, String>>,
    dark: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            fields.forEachIndexed { idx, (label, value) ->
                val isProtected = value.contains("Não localizado", ignoreCase = true) ||
                    value.contains("Protegido", ignoreCase = true) ||
                    value.contains("Restrito", ignoreCase = true) ||
                    value.contains("LGPD", ignoreCase = true) ||
                    value.contains("sigilo", ignoreCase = true)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = when {
                                isProtected -> if (dark) StatusProtectedBgDark else StatusProtectedBgLight
                                else -> if (dark) StatusFoundBgDark else StatusFoundBgLight
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isProtected) "LGPD / Restrito" else "Localizado",
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isProtected -> if (dark) StatusProtectedTextDark else StatusProtectedTextLight
                                    else -> if (dark) StatusFoundTextDark else StatusFoundTextLight
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isProtected) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                if (idx < fields.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
