package app.kinjar.data.sync.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * drive.file only. Interactive requests return [Outcome.NeedsResolution] for the caller to launch with
 * ActivityResultContracts.StartIntentSenderForResult; background [accessToken] calls never prompt.
 * Access tokens come from the Play services cache on every call and are never persisted here.
 */
class DriveAuthorizer(
    private val client: AuthorizationClient,
    private val account: () -> Account? = { null },
) : DriveTokenSource {
    constructor(context: Context, account: () -> Account? = { null }) : this(Identity.getAuthorizationClient(context), account)

    sealed interface Outcome {
        class Granted(val accessToken: String, val pickedFileIds: List<String>) : Outcome
        class NeedsResolution(val request: IntentSenderRequest) : Outcome
    }

    fun pickerRequest(
        mimeTypes: List<String> = listOf(DriveTransport.FOLDER_MIME),
        allowFolderSelection: Boolean = true,
        allowMultiple: Boolean = false,
    ): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE)))
        .setOptOutIncludingGrantedScopes(true)
        .setPrompt(AuthorizationRequest.Prompt.CONSENT or AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        // Value is undocumented for Android; "true" mirrors the desktop trigger_onepick=true parameter.
        .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER, "true")
        .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES, mimeTypes.joinToString(","))
        .apply {
            if (allowFolderSelection) addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_ALLOW_FOLDER_SELECTION, "true")
            if (allowMultiple) addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_ALLOW_MULTIPLE, "true")
        }
        .build()

    fun silentRequest(account: Account?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE)))
        .apply { account?.let(::setAccount) }
        .build()

    suspend fun authorize(request: AuthorizationRequest): Outcome {
        val result = try {
            client.authorize(request).await()
        } catch (e: ApiException) {
            throw DriveException.AuthorizationFailed(e.statusCode)
        }
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) {
            Outcome.NeedsResolution(IntentSenderRequest.Builder(pending.intentSender).build())
        } else {
            granted(result)
        }
    }

    /** Call from the StartIntentSenderForResult callback. */
    fun resultFromActivity(resultCode: Int, data: Intent?): Outcome.Granted {
        if (resultCode != Activity.RESULT_OK) throw DriveException.Cancelled()
        val result = try {
            client.getAuthorizationResultFromIntent(data)
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) throw DriveException.Cancelled()
            throw DriveException.AuthRequired("Drive authorization failed", "GOOGLE_RESULT_ERROR", e.statusCode)
        }
        return granted(result)
    }

    override suspend fun accessToken(): String = when (val outcome = authorize(silentRequest(account()))) {
        is Outcome.Granted -> outcome.accessToken
        is Outcome.NeedsResolution -> throw DriveException.AuthRequired("Drive access needs user approval")
    }

    override suspend fun invalidate(token: String) {
        try {
            client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A token that cannot be cleared is still retried once and then reported as AuthRequired.
        }
    }

    /** Revokes every scope this app holds for the account, not only drive.file. */
    suspend fun revoke(account: Account) {
        try {
            client.revokeAccess(RevokeAccessRequest.builder().setAccount(account).setScopes(listOf(Scope(DRIVE_FILE))).build()).await()
        } catch (e: ApiException) {
            throw IOException("Google revoke failed (status ${e.statusCode})")
        }
    }

    private fun granted(result: AuthorizationResult): Outcome.Granted {
        val token = result.accessToken
        if (token.isNullOrEmpty()) throw DriveException.AuthRequired("No access token returned", "TOKEN_MISSING")
        if (DRIVE_FILE !in result.grantedScopes) {
            throw DriveException.AuthRequired("Drive file access was not granted", "DRIVE_SCOPE_MISSING")
        }
        val picked = parsePickedFileIds(result.tokenResponseParams?.getString(PICKED_FILE_IDS))
        return Outcome.Granted(token, picked)
    }

    companion object {
        const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        private const val PICKED_FILE_IDS = "picked_file_ids"

        internal fun parsePickedFileIds(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map {
                runCatching { DriveTransport.checkId(it) }.getOrElse { throw DriveException.Malformed("Invalid picked file ID") }
            }
        }
    }
}
