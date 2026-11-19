package com.example.data.repository

import android.util.Log
import com.example.data.model.ConsultaRecord
import com.example.data.model.DossierFields
import com.example.data.model.GroundedSource
import com.example.data.model.SearchMode
import com.example.data.remote.CepResponse
import com.example.data.remote.CnpjResponse
import com.example.data.remote.CpfValidator
import com.example.data.remote.DddResponse
import com.example.data.remote.IbgeNomeResponse
import com.example.data.remote.PublicApiClient
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class PublicDataLookupRepository {

    companion object {
        private const val TAG = "PublicDataLookupRepo"
        private const val PROTECTED_DEFAULT = "Não localizado em fontes públicas (protegido por LGPD)"
    }

    // 1. Google Search Grounding model (gemini-4-hydrogen with googleSearch tool)
    private val groundedSearchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-hydrogen",
            tools = listOf(Tool.googleSearch())
        )
    }

    // 2. Fast structured extraction model (gemini-3.1-flash-lite with strict Schema.obj)
    private val dossierSchema = Schema.obj(
        properties = mapOf(
            "nome" to Schema.string(description = "Nome completo localizado ou informado"),
            "cpf" to Schema.string(description = "CPF mascarado ou validado se encontrado em fonte pública"),
            "nascimento" to Schema.string(description = "Data de nascimento se pública, ou status LGPD"),
            "score" to Schema.string(description = "Status de score de crédito ou indicação oficial"),
            "nomeMae" to Schema.string(description = "Nome da mãe se público, ou status LGPD"),
            "nomePai" to Schema.string(description = "Nome do pai se público, ou status LGPD"),
            "sexo" to Schema.string(description = "Sexo se público ou inferido de registro oficial público"),
            "estadoCivil" to Schema.string(description = "Estado civil se constar em registro público"),
            "escolaridade" to Schema.string(description = "Formação/escolaridade se pública (Lattes, LinkedIn, etc.)"),
            "rendaMensal" to Schema.string(description = "Renda/remuneração se pública (Portal da Transparência/QSA), ou status LGPD"),
            "rg" to Schema.string(description = "Status RG (sigilo LGPD)"),
            "enderecoPrincipal" to Schema.string(description = "Cidade/UF ou endereço comercial público (CNPJ/MEI/OAB)"),
            "enderecoSecundario" to Schema.string(description = "Endereço secundário público se houver"),
            "telefone" to Schema.string(description = "Telefone público/DDD ou informado"),
            "email" to Schema.string(description = "E-mail público comercial/acadêmico se encontrado"),
            "profissao" to Schema.string(description = "Profissão, cargo público ou atividade MEI/CNPJ encontrada"),
            "veiculo" to Schema.string(description = "Informação pública se houver ou indicação Detran/Senatran"),
            "parentes" to Schema.string(description = "Sócios ou vínculos públicos se houver, ou status LGPD"),
            "vizinhos" to Schema.string(description = "Status vizinhos (sigilo residencial LGPD)"),
            "summary" to Schema.string(description = "Resumo executivo objetivo das fontes encontradas em português")
        )
    )

    private val fastStructuringModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = dossierSchema
            }
        )
    }

    // 3. Deep reasoning model (gemini-4-carbon with ThinkingLevel.HIGH and NO maxOutputTokens)
    private val deepThinkingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-carbon",
            generationConfig = generationConfig {
                thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        )
    }

    suspend fun performFullConsulta(
        userId: String,
        mode: SearchMode,
        nameInput: String,
        cpfInput: String,
        phoneInput: String,
        onStepUpdate: (String, Float) -> Unit
    ): ConsultaRecord = coroutineScope {
        val cleanName = nameInput.trim()
        val cpfValidation = if (cpfInput.isNotBlank()) CpfValidator.validate(cpfInput) else null
        val formattedCpf = cpfValidation?.formatted ?: ""
        val formattedPhone = if (phoneInput.isNotBlank()) CpfValidator.formatPhone(phoneInput) else ""
        val ddd = CpfValidator.extractDdd(phoneInput)

        onStepUpdate("Consultando APIs oficiais (BrasilAPI & IBGE)...", 0.25f)

        val dddDeferred = async {
            if (ddd != null) {
                runCatching { PublicApiClient.brasilApi.getDdd(ddd) }.getOrNull()
            } else null
        }

        val firstName = cleanName.split(" ").firstOrNull()?.trim().orEmpty()
        val ibgeDeferred = async {
            if (firstName.length >= 2) {
                runCatching { PublicApiClient.ibgeNomes.getNomeFrequencia(firstName).firstOrNull() }.getOrNull()
            } else null
        }

        val dddData = dddDeferred.await()
        val ibgeData = ibgeDeferred.await()

        val dddRegionText = if (dddData?.state != null) {
            val sampleCities = dddData.cities.orEmpty().take(5).joinToString(", ")
            "DDD $ddd (${dddData.state}) — $sampleCities"
        } else if (ddd != null) {
            "DDD $ddd (região não retornada pela BrasilAPI)"
        } else {
            "Não informado"
        }

        val ibgeTotalFreq = ibgeData?.res.orEmpty().sumOf { it.frequencia ?: 0L }
        val ibgeSummary = if (ibgeTotalFreq > 0L) {
            "Nome '${ibgeData?.nome ?: firstName}' possui $ibgeTotalFreq registros históricos no Censo IBGE."
        } else {
            "Sem dados censitários do IBGE para este termo."
        }

        onStepUpdate("Buscando registros públicos via Google Search (Gemini 4 Hydrogen)...", 0.55f)

        val groundedPrompt = buildGroundedSearchPrompt(
            mode = mode,
            name = cleanName,
            cpfFormatted = formattedCpf,
            cpfValid = cpfValidation?.valid ?: false,
            cpfRegion = cpfValidation?.fiscalRegion.orEmpty(),
            phone = formattedPhone,
            dddRegion = dddRegionText,
            ibgeSummary = ibgeSummary
        )

        var groundedReportText = ""
        val extractedSources = mutableListOf<GroundedSource>()

        try {
            val searchResponse = withTimeout(60_000L) {
                groundedSearchModel.generateContent(groundedPrompt)
            }
            groundedReportText = searchResponse.text.orEmpty()

            val chunks = searchResponse.candidates.firstOrNull()?.groundingMetadata?.groundingChunks.orEmpty()
            chunks.forEach { chunk ->
                val web = chunk.web
                val uri = web?.uri
                if (!uri.isNullOrBlank()) {
                    val title = web.title?.takeIf { it.isNotBlank() } ?: "Fonte Pública Web"
                    if (extractedSources.none { it.url == uri }) {
                        extractedSources.add(GroundedSource(title = title, url = uri))
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Google Search grounding timed out", e)
            throw IllegalStateException("A busca pública no Google excedeu o tempo limite (60s). Tente novamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Search grounding failed", e)
            throw IllegalStateException("Falha ao consultar fontes públicas via Gemini: ${e.localizedMessage ?: "Erro desconhecido"}")
        }

        if (extractedSources.none { it.url.contains("brasilapi.com.br") } && dddData != null) {
            extractedSources.add(GroundedSource("BrasilAPI — Consulta DDD", "https://brasilapi.com.br/docs#tag/DDD"))
        }
        if (extractedSources.none { it.url.contains("ibge.gov.br") } && ibgeTotalFreq > 0L) {
            extractedSources.add(GroundedSource("IBGE — Nomes no Brasil", "https://servicodados.ibge.gov.br/api/docs/censos/nomes"))
        }

        onStepUpdate("Estruturando os 19 campos do dossiê (Gemini 3.1 Flash-Lite)...", 0.80f)

        val structurePrompt = buildStructuringPrompt(
            cleanName = cleanName,
            cpfFormatted = formattedCpf,
            cpfMasked = cpfValidation?.masked.orEmpty(),
            cpfValid = cpfValidation?.valid ?: false,
            cpfRegion = cpfValidation?.fiscalRegion.orEmpty(),
            phone = formattedPhone,
            dddRegion = dddRegionText,
            ibgeSummary = ibgeSummary,
            groundedReport = groundedReportText
        )

        val (dossierFields, summary) = try {
            val jsonResp = withTimeout(45_000L) {
                fastStructuringModel.generateContent(structurePrompt)
            }
            val rawJson = jsonResp.text ?: throw IllegalStateException("Resposta estruturada vazia.")
            parseDossierJson(rawJson, cleanName, cpfValidation?.masked.orEmpty(), formattedPhone, dddRegionText)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Structured extraction timed out", e)
            fallbackDossier(cleanName, cpfValidation?.masked.orEmpty(), formattedPhone, dddRegionText, groundedReportText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Structured extraction failed, using fallback parser", e)
            fallbackDossier(cleanName, cpfValidation?.masked.orEmpty(), formattedPhone, dddRegionText, groundedReportText)
        }

        onStepUpdate("Finalizando registro na nuvem...", 0.95f)

        val sourceEntries = extractedSources.take(15).map { "${it.title.take(120)} | ${it.url.take(450)}" }

        ConsultaRecord(
            userId = userId,
            searchMode = mode.name,
            queryName = cleanName.take(160),
            queryCpf = (cpfValidation?.masked ?: formattedCpf).take(25),
            queryPhone = formattedPhone.take(35),
            cpfValid = cpfValidation?.valid ?: false,
            cpfRegion = (cpfValidation?.fiscalRegion ?: "CPF não informado").take(160),
            dddRegion = dddRegionText.take(240),
            ibgeFrequency = ibgeTotalFreq.coerceAtLeast(0L),
            summary = summary.take(6000),
            thinkingAnalysis = "",
            dossier = dossierFields,
            sourceEntries = sourceEntries,
            pinned = false
        )
    }

    fun streamDeepThinkingAnalysis(record: ConsultaRecord): Flow<String> = flow {
        val prompt = """
            Você é um analista sênior de dados públicos brasileiros e conformidade LGPD.
            Analise profundamente esta consulta pública e produza um parecer técnico claro em português (BR) com:
            1. **Coerência dos Dados Informados**: Verifique compatibilidade entre Nome, Região Fiscal do CPF (${record.cpfRegion}), Validade Matemática do CPF (${record.cpfValid}), Região do Telefone/DDD (${record.dddRegion}) e estatísticas do IBGE (${record.ibgeFrequency} ocorrências).
            2. **Síntese dos Registros Públicos Localizados**: Destaque o que realmente foi encontrado em fontes abertas (Diário Oficial, Portal da Transparência, CNPJ/MEI, conselhos de classe, perfis públicos).
            3. **Mapa de Sigilo LGPD & Portais Oficiais**: Para os campos protegidos (Score, Renda, RG, Nascimento, Filiação, Veículos, Parentes, Vizinhos), indique exatamente qual portal oficial do Governo Federal ou órgão autorizado permite ao próprio titular consultar (ex.: Gov.br, Meu CPF Receita Federal, Registrato/Banco Central, Serasa/Boa Vista, Senatran/Detran, ConecteSUS).

            Dados da Consulta:
            - Modo: ${record.resolvedSearchMode().labelPt}
            - Nome: ${record.queryName.ifBlank { "Não informado" }}
            - CPF: ${record.queryCpf.ifBlank { "Não informado" }} (Válido: ${record.cpfValid})
            - Telefone: ${record.queryPhone.ifBlank { "Não informado" }}
            - Resumo da Busca Google: ${record.summary}
            - Campos Extraídos: Nome=${record.dossier.nome}, Profissão=${record.dossier.profissao}, Endereço=${record.dossier.enderecoPrincipal}, Email=${record.dossier.email}, Renda=${record.dossier.rendaMensal}
        """.trimIndent()

        val sb = StringBuilder()
        try {
            withTimeout(90_000L) {
                deepThinkingModel.generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        sb.append(chunkText)
                        emit(sb.toString())
                    }
                }
            }
            if (sb.isBlank()) {
                throw IllegalStateException("O modelo de raciocínio retornou uma análise vazia.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Deep thinking timed out", e)
            throw IllegalStateException("Análise profunda excedeu o tempo limite (90s). Tente novamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Deep thinking failed", e)
            throw IllegalStateException("Erro na análise Gemini 4 Carbon: ${e.localizedMessage ?: "Erro desconhecido"}")
        }
    }

    // Direct Public API tools for the APIs BR tab
    suspend fun lookupCnpj(cnpjRaw: String): Result<CnpjResponse> = runCatching {
        val digits = CpfValidator.digitsOnly(cnpjRaw)
        require(digits.length == 14) { "Informe um CNPJ válido com 14 dígitos." }
        PublicApiClient.brasilApi.getCnpj(digits)
    }

    suspend fun lookupCep(cepRaw: String): Result<CepResponse> = runCatching {
        val digits = CpfValidator.digitsOnly(cepRaw)
        require(digits.length == 8) { "Informe um CEP válido com 8 dígitos." }
        PublicApiClient.brasilApi.getCep(digits)
    }

    suspend fun lookupDdd(dddRaw: String): Result<DddResponse> = runCatching {
        val digits = CpfValidator.digitsOnly(dddRaw).take(2)
        require(digits.length == 2) { "Informe um DDD válido com 2 dígitos." }
        PublicApiClient.brasilApi.getDdd(digits)
    }

    suspend fun lookupIbgeName(nameRaw: String): Result<List<IbgeNomeResponse>> = runCatching {
        val first = nameRaw.trim().split(" ").firstOrNull().orEmpty()
        require(first.length >= 2) { "Informe um nome com ao menos 2 letras." }
        PublicApiClient.ibgeNomes.getNomeFrequencia(first)
    }

    private fun buildGroundedSearchPrompt(
        mode: SearchMode,
        name: String,
        cpfFormatted: String,
        cpfValid: Boolean,
        cpfRegion: String,
        phone: String,
        dddRegion: String,
        ibgeSummary: String
    ): String {
        return """
            Você é um assistente de consulta a fontes públicas abertas do Brasil (Portal da Transparência, Diário Oficial da União/Estados, Receita Federal CNPJ/MEI público, TSE, Lattes/CNPq, OAB/CRM/CREA, redes profissionais públicas e Google Search).
            
            Parâmetros informados pelo usuário (Modo: ${mode.labelPt}):
            - Nome: ${name.ifBlank { "Não informado" }}
            - CPF: ${cpfFormatted.ifBlank { "Não informado" }} (Validade matemática: $cpfValid, Região fiscal: $cpfRegion)
            - Telefone: ${phone.ifBlank { "Não informado" }} (Localização DDD via BrasilAPI: $dddRegion)
            - IBGE Censo Nomes: $ibgeSummary

            INSTRUÇÕES RÍGIDAS DE PRECISÃO E LGPD:
            1. Use a ferramenta de Google Search para buscar APENAS informações verdadeiras e publicamente indexadas sobre a pessoa ou registros públicos ligados ao termo consultado (como empresas MEI/CNPJ públicas, cargos no Portal da Transparência, publicações em Diário Oficial, registros profissionais públicos ou perfil acadêmico/profissional público).
            2. NUNCA invente, fabrique ou adivinhe dados pessoais privados (como número de RG, data de nascimento privada, nome da mãe/pai, renda privada, score de crédito, endereço residencial privado, placa de veículo, parentes ou vizinhos) se não constarem explicitamente em fontes públicas abertas.
            3. Se uma informação não estiver disponível publicamente ou for protegida pela LGPD (Lei 13.709/2018), diga explicitamente "Não localizado em fontes públicas (protegido por LGPD)".
            4. Resuma em português do Brasil os achados públicos reais e as fontes correspondentes.
        """.trimIndent()
    }

    private fun buildStructuringPrompt(
        cleanName: String,
        cpfFormatted: String,
        cpfMasked: String,
        cpfValid: Boolean,
        cpfRegion: String,
        phone: String,
        dddRegion: String,
        ibgeSummary: String,
        groundedReport: String
    ): String {
        return """
            Extraia os fatos reais encontrados no relatório público abaixo para preencher exatamente os 19 campos solicitados, SEM inventar nenhum dado ausente.
            
            Dados base verificados localmente / APIs oficiais:
            - Nome informado: ${cleanName.ifBlank { "Não informado" }}
            - CPF informado: ${if (cpfFormatted.isNotBlank()) "$cpfMasked (Válido: $cpfValid — $cpfRegion)" else "Não informado"}
            - Telefone informado: ${phone.ifBlank { "Não informado" }} ($dddRegion)
            - IBGE: $ibgeSummary
            
            Relatório obtido via Google Search Grounding:
            $groundedReport
            
            Regras obrigatórias:
            - Para campos que NÃO foram encontrados explicitamente nas fontes públicas ou são protegidos por sigilo/LGPD, preencha com "$PROTECTED_DEFAULT" (ou para Score/Veículo/Renda indique o portal oficial como Serasa/Registrato, Senatran/Detran ou Portal da Transparência se aplicável).
            - Se o telefone ou endereço comercial/cidade DDD foi informado/obtido via BrasilAPI, inclua no campo correspondente indicando "(Via DDD BrasilAPI)" ou "(Informado)".
            - No campo "summary", escreva um resumo objetivo de 2 a 4 frases em português.
        """.trimIndent()
    }

    private fun parseDossierJson(
        jsonString: String,
        fallbackName: String,
        cpfMasked: String,
        phone: String,
        dddRegion: String
    ): Pair<DossierFields, String> {
        val obj = JSONObject(jsonString)
        fun str(key: String, default: String = PROTECTED_DEFAULT): String =
            obj.optString(key, "").takeIf { it.isNotBlank() }?.take(600) ?: default

        val fields = DossierFields(
            nome = str("nome", fallbackName.ifBlank { PROTECTED_DEFAULT }),
            cpf = str("cpf", cpfMasked.ifBlank { PROTECTED_DEFAULT }),
            nascimento = str("nascimento"),
            score = str("score", "Protegido por sigilo bancário (consulte Serasa ou BCB Registrato)"),
            nomeMae = str("nomeMae"),
            nomePai = str("nomePai"),
            sexo = str("sexo"),
            estadoCivil = str("estadoCivil"),
            escolaridade = str("escolaridade"),
            rendaMensal = str("rendaMensal"),
            rg = str("rg"),
            enderecoPrincipal = str("enderecoPrincipal", if (dddRegion != "Não informado") dddRegion else PROTECTED_DEFAULT),
            enderecoSecundario = str("enderecoSecundario"),
            telefone = str("telefone", phone.ifBlank { PROTECTED_DEFAULT }),
            email = str("email"),
            profissao = str("profissao"),
            veiculo = str("veiculo", "Restrito por LGPD (consulte Portal Senatran / Detran)"),
            parentes = str("parentes"),
            vizinhos = str("vizinhos")
        )
        val summary = obj.optString("summary", "").takeIf { it.isNotBlank() }
            ?: "Consulta pública concluída via APIs oficiais e Google Search."
        return fields to summary
    }

    private fun fallbackDossier(
        cleanName: String,
        cpfMasked: String,
        phone: String,
        dddRegion: String,
        groundedReport: String
    ): Pair<DossierFields, String> {
        val fields = DossierFields(
            nome = cleanName.ifBlank { PROTECTED_DEFAULT },
            cpf = cpfMasked.ifBlank { PROTECTED_DEFAULT },
            nascimento = PROTECTED_DEFAULT,
            score = "Protegido por sigilo bancário (consulte Serasa ou BCB Registrato)",
            nomeMae = PROTECTED_DEFAULT,
            nomePai = PROTECTED_DEFAULT,
            sexo = PROTECTED_DEFAULT,
            estadoCivil = PROTECTED_DEFAULT,
            escolaridade = PROTECTED_DEFAULT,
            rendaMensal = PROTECTED_DEFAULT,
            rg = PROTECTED_DEFAULT,
            enderecoPrincipal = if (dddRegion != "Não informado") dddRegion else PROTECTED_DEFAULT,
            enderecoSecundario = PROTECTED_DEFAULT,
            telefone = phone.ifBlank { PROTECTED_DEFAULT },
            email = PROTECTED_DEFAULT,
            profissao = PROTECTED_DEFAULT,
            veiculo = "Restrito por LGPD (consulte Portal Senatran / Detran)",
            parentes = PROTECTED_DEFAULT,
            vizinhos = PROTECTED_DEFAULT
        )
        val summary = groundedReport.take(1200).ifBlank {
            "Consulta processada com validação local, APIs públicas e proteção LGPD."
        }
        return fields to summary
    }
}
