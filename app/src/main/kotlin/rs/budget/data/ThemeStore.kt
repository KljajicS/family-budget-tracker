package rs.budget.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Appearance choice, kept on this device only: it is not family data, so it never enters the event log. */
class ThemeStore(private val read: () -> String?, private val write: (String) -> Unit) {
    private val _mode = MutableStateFlow(read()?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode

    fun set(mode: ThemeMode) {
        write(mode.name)
        _mode.value = mode
    }

    companion object {
        fun fromPreferences(context: Context): ThemeStore {
            val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            return ThemeStore({ prefs.getString("theme", null) }, { prefs.edit { putString("theme", it) } })
        }
    }
}
