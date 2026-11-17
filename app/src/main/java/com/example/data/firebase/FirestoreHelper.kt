package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

object FirestoreProvider {

    private const val TAG = "FirestoreProvider"
    private const val CACHE_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB offline cache for Android reliability

    @Volatile
    private var configuredInstance: FirebaseFirestore? = null

    fun getInstance(context: Context): FirebaseFirestore {
        configuredInstance?.let { return it }
        return synchronized(this) {
            configuredInstance?.let { return it }
            val appContext = context.applicationContext
            val databaseId = try {
                appContext.getString(R.string.firestore_database_id).trim()
            } catch (e: Exception) {
                Log.w(TAG, "firestore_database_id resource unavailable: ${e.message}")
                ""
            }

            val instance = if (databaseId.isNotBlank()) {
                FirebaseFirestore.getInstance(FirebaseApp.getInstance(), databaseId)
            } else {
                FirebaseFirestore.getInstance()
            }

            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(CACHE_SIZE_BYTES)
                            .build()
                    )
                    .build()
                instance.firestoreSettings = settings
            } catch (e: Exception) {
                // Ignore if Firestore settings were already locked earlier in the process lifecycle
                Log.d(TAG, "Firestore settings already initialized: ${e.message}")
            }

            configuredInstance = instance
            instance
        }
    }
}

fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    return generateSequence(this) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
}

fun Throwable.isTransientNetworkFirestoreError(): Boolean {
    val fsEx = unwrapFirestoreException() ?: return false
    return fsEx.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
        fsEx.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
        fsEx.code == FirebaseFirestoreException.Code.ABORTED
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
