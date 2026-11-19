package com.example.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class DddResponse(
    val state: String? = null,
    val cities: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class CepResponse(
    val cep: String? = null,
    val state: String? = null,
    val city: String? = null,
    val neighborhood: String? = null,
    val street: String? = null,
    val service: String? = null
)

@JsonClass(generateAdapter = true)
data class QsaSocio(
    val nome_socio: String? = null,
    val qualificacao_socio: String? = null
)

@JsonClass(generateAdapter = true)
data class CnpjResponse(
    val cnpj: String? = null,
    val razao_social: String? = null,
    val nome_fantasia: String? = null,
    val descricao_situacao_cadastral: String? = null,
    val data_inicio_atividade: String? = null,
    val cnae_fiscal_descricao: String? = null,
    val logradouro: String? = null,
    val numero: String? = null,
    val bairro: String? = null,
    val municipio: String? = null,
    val uf: String? = null,
    val cep: String? = null,
    val ddd_telefone_1: String? = null,
    val email: String? = null,
    val capital_social: Double? = null,
    val qsa: List<QsaSocio>? = null
)

@JsonClass(generateAdapter = true)
data class IbgePeriodo(
    val periodo: String? = null,
    val frequencia: Long? = null
)

@JsonClass(generateAdapter = true)
data class IbgeNomeResponse(
    val nome: String? = null,
    val localidade: String? = null,
    val res: List<IbgePeriodo>? = null
)

interface BrasilApiService {
    @GET("ddd/v1/{ddd}")
    suspend fun getDdd(@Path("ddd") ddd: String): DddResponse

    @GET("cep/v2/{cep}")
    suspend fun getCep(@Path("cep") cep: String): CepResponse

    @GET("cnpj/v1/{cnpj}")
    suspend fun getCnpj(@Path("cnpj") cnpj: String): CnpjResponse
}

interface IbgeNomesService {
    @GET("{nome}")
    suspend fun getNomeFrequencia(@Path("nome") nome: String): List<IbgeNomeResponse>
}

object PublicApiClient {
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val brasilApi: BrasilApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://brasilapi.com.br/api/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(BrasilApiService::class.java)
    }

    val ibgeNomes: IbgeNomesService by lazy {
        Retrofit.Builder()
            .baseUrl("https://servicodados.ibge.gov.br/api/v2/censos/nomes/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(IbgeNomesService::class.java)
    }
}
