package rs.budget.data

import android.content.Context
import androidx.core.content.edit
import rs.budget.domain.newId

/** Stable random id per install. Not the hardware id: it must not identify the phone to anyone reading Drive. */
fun deviceId(context: Context): String {
    val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    return prefs.getString("id", null) ?: newId().also { prefs.edit { putString("id", it) } }
}
