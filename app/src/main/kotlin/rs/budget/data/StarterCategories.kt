package rs.budget.data

import rs.budget.domain.Category

object StarterCategories {
    val all = listOf(
        Category("cat-groceries", "Groceries", "#3D4BD6"),
        Category("cat-bills", "Bills", "#D06A1E"),
        Category("cat-transport", "Transport", "#1A8A7B"),
        Category("cat-eating-out", "Eating out", "#7B55C7"),
        Category("cat-household", "Household", "#6E747D"),
        Category("cat-kids", "Kids", "#B8467F"),
        Category("cat-fun", "Fun", "#A88412"),
        Category("cat-health", "Health", "#2A7FC0"),
        Category("cat-other", "Other", "#6E747D"),
    )
}
