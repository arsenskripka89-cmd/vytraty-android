package ua.vytraty.app.data.db

/** Default categories and wallets inserted on first launch. */
object Seed {
    val expenseCategories = listOf(
        CategoryEntity(name = "Продукти", icon = "cart", color = 0xFF43A047, sortOrder = 0, isSystem = true),
        CategoryEntity(name = "Кафе та ресторани", icon = "restaurant", color = 0xFFFB8C00, sortOrder = 1, isSystem = true),
        CategoryEntity(name = "Транспорт", icon = "bus", color = 0xFF1E88E5, sortOrder = 2, isSystem = true),
        CategoryEntity(name = "Авто", icon = "car", color = 0xFF3949AB, sortOrder = 3, isSystem = true),
        CategoryEntity(name = "Житло та комуналка", icon = "home", color = 0xFF8E24AA, sortOrder = 4, isSystem = true),
        CategoryEntity(name = "Зв'язок та інтернет", icon = "wifi", color = 0xFF00ACC1, sortOrder = 5, isSystem = true),
        CategoryEntity(name = "Здоров'я", icon = "health", color = 0xFFE53935, sortOrder = 6, isSystem = true),
        CategoryEntity(name = "Аптека", icon = "pharmacy", color = 0xFFD81B60, sortOrder = 7, isSystem = true),
        CategoryEntity(name = "Одяг та взуття", icon = "clothes", color = 0xFF6D4C41, sortOrder = 8, isSystem = true),
        CategoryEntity(name = "Розваги", icon = "movie", color = 0xFFF4511E, sortOrder = 9, isSystem = true),
        CategoryEntity(name = "Підписки", icon = "subscriptions", color = 0xFF5E35B1, sortOrder = 10, isSystem = true),
        CategoryEntity(name = "Покупки", icon = "shopping", color = 0xFF7CB342, sortOrder = 11, isSystem = true),
        CategoryEntity(name = "Освіта", icon = "school", color = 0xFF039BE5, sortOrder = 12, isSystem = true),
        CategoryEntity(name = "Подарунки", icon = "gift", color = 0xFFEC407A, sortOrder = 13, isSystem = true),
        CategoryEntity(name = "Подорожі", icon = "flight", color = 0xFF00897B, sortOrder = 14, isSystem = true),
        CategoryEntity(name = "Краса", icon = "spa", color = 0xFFAB47BC, sortOrder = 15, isSystem = true),
        CategoryEntity(name = "Діти", icon = "child", color = 0xFFFFB300, sortOrder = 16, isSystem = true),
        CategoryEntity(name = "Тварини", icon = "pets", color = 0xFF8D6E63, sortOrder = 17, isSystem = true),
        CategoryEntity(name = "Комісії та податки", icon = "receipt", color = 0xFF546E7A, sortOrder = 18, isSystem = true),
        CategoryEntity(name = "Інше", icon = "category", color = 0xFF757575, sortOrder = 19, isSystem = true),
    )
    val incomeCategories = listOf(
        CategoryEntity(name = "Зарплата", icon = "work", color = 0xFF2E7D32, kind = TxKind.INCOME, sortOrder = 0, isSystem = true),
        CategoryEntity(name = "Фриланс", icon = "laptop", color = 0xFF00695C, kind = TxKind.INCOME, sortOrder = 1, isSystem = true),
        CategoryEntity(name = "Подарунок", icon = "gift", color = 0xFFAD1457, kind = TxKind.INCOME, sortOrder = 2, isSystem = true),
        CategoryEntity(name = "Кешбек", icon = "cashback", color = 0xFF6A1B9A, kind = TxKind.INCOME, sortOrder = 3, isSystem = true),
        CategoryEntity(name = "Інший дохід", icon = "income", color = 0xFF558B2F, kind = TxKind.INCOME, sortOrder = 4, isSystem = true),
    )
    val defaultWallets = listOf(
        WalletEntity(name = "Готівка", type = WalletType.CASH, color = 0xFF43A047, sortOrder = 0),
        WalletEntity(name = "Основна картка", type = WalletType.CARD, color = 0xFF1E88E5, isDefault = true, sortOrder = 1),
    )

    suspend fun seedIfEmpty(db: AppDatabase) {
        if (db.categoryDao().count() == 0) db.categoryDao().insertAll(expenseCategories + incomeCategories)
        if (db.walletDao().count() == 0) defaultWallets.forEach { db.walletDao().insert(it) }
    }
}
