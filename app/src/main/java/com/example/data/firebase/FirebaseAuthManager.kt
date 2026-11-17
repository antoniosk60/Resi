package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.data.model.AccessPolicy
import com.example.data.model.AppUser
import com.example.data.model.AuthorizedAccount
import com.example.data.model.UserRole
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface AuthSessionState {
    data object Unauthenticated : AuthSessionState
    data object Checking : AuthSessionState
    data class Unauthorized(
        val email: String,
        val reason: String
    ) : AuthSessionState

    data class NetworkError(
        val email: String,
        val reason: String
    ) : AuthSessionState

    data class Authorized(
        val user: AppUser,
        val firebaseUser: FirebaseUser
    ) : AuthSessionState
}

class FirebaseAuthManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "FirebaseAuthManager"
    private val appContext = context.applicationContext
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(appContext)
    private val verifyMutex = Mutex()

    private val _firebaseUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val firebaseUser: StateFlow<FirebaseUser?> = _firebaseUser.asStateFlow()

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _authSessionState = MutableStateFlow<AuthSessionState>(
        if (auth.currentUser != null) AuthSessionState.Checking else AuthSessionState.Unauthenticated
    )
    val authSessionState: StateFlow<AuthSessionState> = _authSessionState.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val firestore: FirebaseFirestore by lazy {
        FirestoreProvider.getInstance(appContext)
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _firebaseUser.value = user
        if (user != null) {
            if (_authSessionState.value !is AuthSessionState.Authorized ||
                (_authSessionState.value as? AuthSessionState.Authorized)?.firebaseUser?.uid != user.uid
            ) {
                _authSessionState.value = AuthSessionState.Checking
            }
            scope.launch(Dispatchers.IO) {
                verifyAndSyncAuthorizedUser(user)
            }
        } else {
            _currentUser.value = null
            _authSessionState.value = AuthSessionState.Unauthenticated
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    fun dispose() {
        auth.removeAuthStateListener(authStateListener)
    }

    fun retryVerification() {
        val user = auth.currentUser
        if (user == null) {
            _authSessionState.value = AuthSessionState.Unauthenticated
            return
        }
        _authSessionState.value = AuthSessionState.Checking
        scope.launch(Dispatchers.IO) {
            verifyAndSyncAuthorizedUser(user)
        }
    }

    /**
     * Real-time stream of authorized Google accounts (only active for the 1 Administrador).
     */
    val authorizedAccountsFlow: Flow<List<AuthorizedAccount>> = callbackFlow {
        if (auth.currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val path = "authorized_users"
        val listener = firestore.collection(path)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser != null) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val accounts = snapshot?.documents
                    ?.mapNotNull { it.toAuthorizedAccount() }
                    ?.sortedWith(
                        compareBy<AuthorizedAccount> { if (it.role == UserRole.ADMINISTRADOR) 0 else 1 }
                            .thenBy { it.slotId }
                            .thenBy { it.email }
                    ) ?: emptyList()
                trySend(accounts)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Interactive Google Sign-In button using single-option GetSignInWithGoogleOption.
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        onResult: (Boolean, String?) -> Unit
    ) {
        _isAuthenticating.value = true
        try {
            val serverClientId = try {
                appContext.getString(R.string.default_web_client_id)
            } catch (_: Exception) {
                _isAuthenticating.value = false
                onResult(false, "Configuración de Google Sign-In incompleta (default_web_client_id).")
                return
            }

            val resolvedActivity = activityContext.findActivity() ?: activityContext
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = resolvedActivity
            )

            handleCredentialResponse(result, onResult)
        } catch (e: GetCredentialCancellationException) {
            Log.w(tag, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            _isAuthenticating.value = false
            onResult(false, "Inicio de sesión cancelado")
        } catch (e: GetCredentialException) {
            Log.e(tag, "Credential error: ${e.message}", e)
            _isAuthenticating.value = false
            val isNoAccount = e.message?.contains("16") == true ||
                e.message?.contains("No credentials", ignoreCase = true) == true ||
                e.message?.contains("matching", ignoreCase = true) == true

            val friendlyMsg = if (isNoAccount) {
                "No se encontró una cuenta de Google en este dispositivo. Agrega tu cuenta Google en Ajustes e intenta de nuevo."
            } else {
                "No se pudo completar el acceso con Google: ${e.localizedMessage ?: "verifica tu conexión"}"
            }
            onResult(false, friendlyMsg)
        } catch (e: CancellationException) {
            _isAuthenticating.value = false
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Sign in exception: ${e.message}", e)
            _isAuthenticating.value = false
            onResult(false, "Error al iniciar sesión con Google: ${e.localizedMessage ?: "error desconocido"}")
        }
    }

    /**
     * Silent auto-sign-in attempt on startup using single-option GetGoogleIdOption.
     */
    suspend fun attemptAutoSignIn(activityContext: Context) {
        if (auth.currentUser != null) return
        try {
            val serverClientId = appContext.getString(R.string.default_web_client_id)
            val resolvedActivity = activityContext.findActivity() ?: activityContext
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = resolvedActivity
            )
            handleCredentialResponse(result) { _, _ -> }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silent check; stay on AuthScreen if no previously authorized credential exists
        }
    }

    private suspend fun handleCredentialResponse(
        response: GetCredentialResponse,
        onResult: (Boolean, String?) -> Unit
    ) {
        when (val credential = response.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = auth.signInWithCredential(firebaseCredential).await()
                        val user = authResult.user
                        if (user != null) {
                            verifyAndSyncAuthorizedUser(user)
                            _isAuthenticating.value = false
                            when (val state = _authSessionState.value) {
                                is AuthSessionState.Authorized -> onResult(true, null)
                                is AuthSessionState.Unauthorized -> onResult(false, state.reason)
                                is AuthSessionState.NetworkError -> onResult(false, state.reason)
                                else -> onResult(false, "No se pudo verificar la autorización.")
                            }
                        } else {
                            _isAuthenticating.value = false
                            onResult(false, "No se pudo obtener el usuario de Firebase")
                        }
                    } catch (e: CancellationException) {
                        _isAuthenticating.value = false
                        throw e
                    } catch (e: Exception) {
                        Log.e(tag, "FirebaseAuth with token failed: ${e.message}", e)
                        _isAuthenticating.value = false
                        onResult(false, e.localizedMessage ?: "Error al autenticar con Firebase")
                    }
                } else {
                    _isAuthenticating.value = false
                    onResult(false, "Tipo de credencial no soportado")
                }
            }
            else -> {
                _isAuthenticating.value = false
                onResult(false, "Credencial inesperada")
            }
        }
    }

    /**
     * Reads the authorized user doc with bounded timeout and local Firestore cache fallback
     * so weak Wi-Fi / 4G in the caseta never freezes login or falsely blocks already-authorized staff.
     */
    private suspend fun getDocumentResilient(docRef: DocumentReference): DocumentSnapshot? {
        return try {
            withTimeout(8_000L) {
                docRef.get(Source.DEFAULT).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = try {
                docRef.get(Source.CACHE).await()
            } catch (_: Exception) {
                null
            }
            if (cached != null && cached.exists()) {
                Log.w(tag, "Using cached authorization doc while offline: ${docRef.path}")
                cached
            } else {
                throw e
            }
        }
    }

    /**
     * Serialized verification of whether the signed-in Google account is the 1 Administrador
     * or one of the max 5 Capturistas authorized in Firestore.
     */
    private suspend fun verifyAndSyncAuthorizedUser(user: FirebaseUser) = verifyMutex.withLock {
        if (auth.currentUser?.uid != user.uid) return@withLock

        val email = user.email?.trim()?.lowercase().orEmpty()
        if (email.isBlank()) {
            _currentUser.value = null
            _authSessionState.value = AuthSessionState.Unauthorized(
                email = "Sin correo",
                reason = "La cuenta de Google no proporcionó un correo electrónico válido."
            )
            return@withLock
        }

        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val photoUrl = user.photoUrl?.toString() ?: ""
        val now = System.currentTimeMillis()
        val docPath = "authorized_users/$email"

        try {
            val docRef = firestore.collection("authorized_users").document(email)

            if (email == AccessPolicy.ADMIN_EMAIL) {
                val snapshot = try {
                    getDocumentResilient(docRef)
                } catch (e: Exception) {
                    // For the bootstrapped single Admin, allow offline resilience if transient network failure occurs
                    if (e.isTransientNetworkFirestoreError() || e is kotlinx.coroutines.TimeoutCancellationException) {
                        Log.w(tag, "Admin doc lookup offline/timeout; authorizing Admin locally and queuing bootstrap sync")
                        val offlineAdmin = AppUser(
                            id = user.uid,
                            name = displayName,
                            email = email,
                            role = UserRole.ADMINISTRADOR
                        )
                        _currentUser.value = offlineAdmin
                        _authSessionState.value = AuthSessionState.Authorized(offlineAdmin, user)
                        return@withLock
                    }
                    throw e
                }

                if (snapshot == null || !snapshot.exists()) {
                    val adminData = mapOf(
                        "email" to email,
                        "displayName" to displayName.take(120),
                        "role" to UserRole.ADMINISTRADOR.name,
                        "slotId" to "0",
                        "uid" to user.uid,
                        "photoUrl" to photoUrl,
                        "addedBy" to AccessPolicy.ADMIN_EMAIL,
                        "addedAtMillis" to now,
                        "lastActiveMillis" to now
                    )
                    // Non-blocking cloud sync so offline startups don't hang waiting for server ACK
                    docRef.set(adminData, SetOptions.merge()).addOnFailureListener { err ->
                        handleFirestoreError(err, OperationType.WRITE, docPath)
                    }
                } else {
                    docRef.update(
                        mapOf(
                            "uid" to user.uid,
                            "displayName" to displayName.take(120),
                            "photoUrl" to photoUrl,
                            "lastActiveMillis" to now
                        )
                    ).addOnFailureListener { err ->
                        Log.w(tag, "Non-critical admin metadata update deferred: ${err.message}")
                    }
                }

                val appUser = AppUser(
                    id = user.uid,
                    name = displayName,
                    email = email,
                    role = UserRole.ADMINISTRADOR
                )
                _currentUser.value = appUser
                _authSessionState.value = AuthSessionState.Authorized(appUser, user)
                return@withLock
            }

            // Non-admin Google accounts must be pre-authorized as one of the 5 Capturistas
            val snapshot = getDocumentResilient(docRef)
            val roleStr = snapshot?.getString("role")
            val slotId = snapshot?.getString("slotId") ?: ""

            if (snapshot != null && snapshot.exists() &&
                roleStr == UserRole.CAPTURISTA.name &&
                slotId in AccessPolicy.CAPTURISTA_SLOTS
            ) {
                // Non-blocking session metadata refresh (does not freeze offline logins)
                docRef.update(
                    mapOf(
                        "uid" to user.uid,
                        "displayName" to displayName.take(120),
                        "photoUrl" to photoUrl,
                        "lastActiveMillis" to now
                    )
                ).addOnFailureListener { err ->
                    Log.w(tag, "Non-critical capturista metadata update deferred: ${err.message}")
                }

                val appUser = AppUser(
                    id = user.uid,
                    name = displayName,
                    email = email,
                    role = UserRole.CAPTURISTA
                )
                _currentUser.value = appUser
                _authSessionState.value = AuthSessionState.Authorized(appUser, user)
            } else {
                _currentUser.value = null
                _authSessionState.value = AuthSessionState.Unauthorized(
                    email = email,
                    reason = "La cuenta $email no está autorizada. Solo tienen acceso 1 Administrador y hasta 5 Capturistas registrados."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            val fsException = e.unwrapFirestoreException()
            val isPermissionDenied = fsException?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

            _currentUser.value = null
            if (!isPermissionDenied && (e.isTransientNetworkFirestoreError() || e is kotlinx.coroutines.TimeoutCancellationException)) {
                _authSessionState.value = AuthSessionState.NetworkError(
                    email = email,
                    reason = "Sin conexión estable a Internet para verificar la cuenta $email por primera vez. Verifica tu red e intenta de nuevo."
                )
            } else {
                _authSessionState.value = AuthSessionState.Unauthorized(
                    email = email,
                    reason = "La cuenta $email no está autorizada o fue revocada por el Administrador."
                )
            }
        }
    }

    /**
     * Adds a Capturista Google email into one of the 5 fixed slots (Admin only).
     */
    suspend fun addCapturistaAccount(rawEmail: String, rawDisplayName: String): Result<String> {
        val current = _currentUser.value
        if (current == null || !current.isAdmin) {
            return Result.failure(IllegalStateException("Solo el Administrador puede autorizar Capturistas."))
        }

        val email = rawEmail.trim().lowercase()
        if (!EMAIL_REGEX.matches(email)) {
            return Result.failure(IllegalArgumentException("Ingresa un correo de Google válido."))
        }
        if (email == AccessPolicy.ADMIN_EMAIL) {
            return Result.failure(IllegalArgumentException("Este correo ya corresponde al Administrador único."))
        }

        val displayName = rawDisplayName.trim().ifBlank {
            email.substringBefore("@").replaceFirstChar { it.uppercase() }
        }.take(120)

        return try {
            val existingUser = withTimeout(10_000L) {
                firestore.collection("authorized_users").document(email).get().await()
            }
            if (existingUser.exists()) {
                return Result.failure(IllegalStateException("La cuenta $email ya se encuentra autorizada."))
            }

            val slotsSnapshot = withTimeout(10_000L) {
                firestore.collection("capturista_slots").get().await()
            }
            val occupiedSlots = slotsSnapshot.documents.map { it.id }.toSet()
            val freeSlot = AccessPolicy.CAPTURISTA_SLOTS.firstOrNull { it !in occupiedSlots }
                ?: return Result.failure(
                    IllegalStateException("Límite alcanzado: solo se permiten ${AccessPolicy.MAX_CAPTURISTAS} Capturistas autorizados.")
                )

            val now = System.currentTimeMillis()
            val batch = firestore.batch()
            val slotRef = firestore.collection("capturista_slots").document(freeSlot)
            val userRef = firestore.collection("authorized_users").document(email)

            batch.set(
                slotRef,
                mapOf(
                    "email" to email,
                    "slotId" to freeSlot,
                    "assignedAtMillis" to now
                )
            )
            batch.set(
                userRef,
                mapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "role" to UserRole.CAPTURISTA.name,
                    "slotId" to freeSlot,
                    "uid" to "",
                    "photoUrl" to "",
                    "addedBy" to AccessPolicy.ADMIN_EMAIL,
                    "addedAtMillis" to now,
                    "lastActiveMillis" to 0L
                )
            )
            withTimeout(10_000L) {
                batch.commit().await()
            }
            Result.success(freeSlot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "authorized_users/$email")
            Result.failure(
                if (e.isTransientNetworkFirestoreError() || e is kotlinx.coroutines.TimeoutCancellationException) {
                    IllegalStateException("Se requiere conexión a Internet para autorizar una cuenta nueva.")
                } else {
                    e
                }
            )
        }
    }

    /**
     * Revokes a Capturista Google email and frees its slot (Admin only).
     */
    suspend fun removeCapturistaAccount(account: AuthorizedAccount): Result<Unit> {
        val current = _currentUser.value
        if (current == null || !current.isAdmin) {
            return Result.failure(IllegalStateException("Solo el Administrador puede revocar accesos."))
        }
        val normalizedEmail = account.email.trim().lowercase()
        if (normalizedEmail == AccessPolicy.ADMIN_EMAIL || account.role == UserRole.ADMINISTRADOR) {
            return Result.failure(IllegalArgumentException("La cuenta del Administrador único no puede eliminarse."))
        }

        return try {
            val batch = firestore.batch()
            batch.delete(firestore.collection("authorized_users").document(normalizedEmail))
            if (account.slotId in AccessPolicy.CAPTURISTA_SLOTS) {
                batch.delete(firestore.collection("capturista_slots").document(account.slotId))
            }
            withTimeout(10_000L) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "authorized_users/$normalizedEmail")
            Result.failure(
                if (e.isTransientNetworkFirestoreError() || e is kotlinx.coroutines.TimeoutCancellationException) {
                    IllegalStateException("Se requiere conexión a Internet para revocar el acceso.")
                } else {
                    e
                }
            )
        }
    }

    fun signOut() {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(tag, "clearCredentialState failed: ${e.message}")
                }
                auth.signOut()
            }
            _firebaseUser.value = null
            _currentUser.value = null
            _authSessionState.value = AuthSessionState.Unauthenticated
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun DocumentSnapshot.toAuthorizedAccount(): AuthorizedAccount? {
    if (!exists()) return null
    val email = getString("email") ?: id
    val roleName = getString("role") ?: UserRole.CAPTURISTA.name
    val role = if (roleName == UserRole.ADMINISTRADOR.name) UserRole.ADMINISTRADOR else UserRole.CAPTURISTA
    return AuthorizedAccount(
        email = email,
        displayName = getString("displayName") ?: email.substringBefore("@"),
        role = role,
        slotId = getString("slotId") ?: "",
        uid = getString("uid") ?: "",
        addedBy = getString("addedBy") ?: AccessPolicy.ADMIN_EMAIL,
        addedAtMillis = getLong("addedAtMillis") ?: 0L,
        lastActiveMillis = getLong("lastActiveMillis") ?: 0L
    )
}
