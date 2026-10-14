package rs.budget.ui

import androidx.annotation.StringRes
import rs.budget.R

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
