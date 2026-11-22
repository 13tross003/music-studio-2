package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Producer must be signed in with Google before accessing Firestore.")

    fun observeSessions(userId: String): Flow<List<StudioSession>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudioSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error !is CancellationException && auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, sessionId: String): Flow<List<StudioMessage>> = flow {
        require(userId.isNotBlank() && sessionId.isNotBlank()) { "User ID and Session ID must not be blank" }
        val path = "users/$userId/sessions/$sessionId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("sessions").document(sessionId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudioMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error !is CancellationException && auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createSession(session: StudioSession): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("sessions").document()
            docPath = docRef.path
            docRef.set(session.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSessionMix(sessionId: String, session: StudioSession): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank()) { "Session ID cannot be blank" }
            val docRef = db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
            docPath = docRef.path
            docRef.update(session.toUpdateMixMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(sessionId: String, message: StudioMessage): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions/$sessionId/messages"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank()) { "Session ID cannot be blank" }
            val docRef = db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
                .collection("messages").document()
            docPath = docRef.path
            docRef.set(message.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank()) { "Session ID cannot be blank" }
            val sessionRef = db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
            docPath = sessionRef.path
            val messages = sessionRef.collection("messages").get().await()
            if (!messages.isEmpty) {
                val batch = db.batch()
                messages.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            sessionRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
