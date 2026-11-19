package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ConsultaRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ConsultaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("É necessário entrar com a conta Google antes de acessar o histórico.")
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val docPath = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "Sessão divergente ao sincronizar perfil." }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val safeName = (user.displayName?.takeIf { it.isNotBlank() } ?: user.email ?: "Usuário Google").take(120)
            val safeEmail = (user.email?.takeIf { it.length >= 3 } ?: "usuario@google.com").take(200)
            val safePhoto = user.photoUrl?.toString()?.take(500)

            if (!snapshot.exists()) {
                val createMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safePhoto != null) {
                    createMap["photoUrl"] = safePhoto
                }
                docRef.set(createMap).await()
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safePhoto != null) {
                    updateMap["photoUrl"] = safePhoto
                }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeConsultas(userId: String): Flow<List<ConsultaRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Usuário autenticado diferente do solicitado." }
        val path = "users/$uid/consultas"
        emitAll(
            db.collection("users").document(uid).collection("consultas")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConsultaRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveConsulta(record: ConsultaRecord): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/consultas"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("consultas").document()
            docPath = docRef.path

            val boundedSources = record.sourceEntries
                .map { it.take(600) }
                .take(15)

            // Match firestore.rules keys().hasOnly(...) and bounds strictly; NEVER include "id"
            val payload = mapOf(
                "userId" to uid,
                "searchMode" to record.resolvedSearchMode().name,
                "queryName" to record.queryName.take(160),
                "queryCpf" to record.queryCpf.take(25),
                "queryPhone" to record.queryPhone.take(35),
                "cpfValid" to record.cpfValid,
                "cpfRegion" to record.cpfRegion.take(160),
                "dddRegion" to record.dddRegion.take(240),
                "ibgeFrequency" to record.ibgeFrequency.coerceAtLeast(0L),
                "summary" to record.summary.take(6000),
                "thinkingAnalysis" to record.thinkingAnalysis.take(12000),
                "dossier" to record.dossier.toFirestoreMap(),
                "sourceEntries" to boundedSources,
                "pinned" to record.pinned,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun togglePinned(userId: String, consultaId: String, pinned: Boolean): Result<Unit> {
        val path = "users/$userId/consultas/$consultaId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Tentativa de alterar registro de outro usuário." }
            require(consultaId.isNotBlank()) { "ID da consulta inválido." }
            db.collection("users").document(uid)
                .collection("consultas").document(consultaId)
                .update(
                    mapOf(
                        "pinned" to pinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateThinkingAnalysis(
        userId: String,
        consultaId: String,
        summary: String,
        thinkingAnalysis: String
    ): Result<Unit> {
        val path = "users/$userId/consultas/$consultaId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Tentativa de alterar registro de outro usuário." }
            require(consultaId.isNotBlank()) { "ID da consulta inválido." }
            db.collection("users").document(uid)
                .collection("consultas").document(consultaId)
                .update(
                    mapOf(
                        "summary" to summary.take(6000),
                        "thinkingAnalysis" to thinkingAnalysis.take(12000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConsulta(userId: String, consultaId: String): Result<Unit> {
        val path = "users/$userId/consultas/$consultaId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Tentativa de excluir registro de outro usuário." }
            require(consultaId.isNotBlank()) { "ID da consulta inválido." }
            db.collection("users").document(uid)
                .collection("consultas").document(consultaId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
