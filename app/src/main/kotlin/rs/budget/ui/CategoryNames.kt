package rs.budget.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import rs.budget.R
import rs.budget.data.StarterCategories

/** Starter categories are translated; user-created ones show their stored name. */
@StringRes
fun categoryLabel(id: String): Int? = when (id) {
    "cat-groceries" -> R.string.cat_groceries
    "cat-bills" -> R.string.cat_bills
    "cat-transport" -> R.string.cat_transport
    "cat-eating-out" -> R.string.cat_eating_out
    "cat-household" -> R.string.cat_household
    "cat-kids" -> R.string.cat_kids
    "cat-fun" -> R.string.cat_fun
    "cat-health" -> R.string.cat_health
    "cat-other" -> R.string.cat_other
    else -> null
}

/** Translated while a starter category keeps its seeded name; once renamed, the stored name wins. */
@Composable
fun categoryDisplayName(id: String, storedName: String?): String {
    val starter = StarterCategories.all.firstOrNull { it.id == id }
    val label = categoryLabel(id)
    return if (starter != null && label != null && (storedName == null || storedName == starter.name)) stringResource(label) else storedName ?: id
}

/** Fixed palette for user categories; the first eight are the starter colors. */
val CategoryPalette = listOf(
    "#3D4BD6", "#D06A1E", "#1A8A7B", "#7B55C7", "#6E747D", "#B8467F", "#A88412", "#2A7FC0",
    "#C0392B", "#16A085", "#8E44AD", "#2C3E50",
)
