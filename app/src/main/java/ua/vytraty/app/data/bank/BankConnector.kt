package ua.vytraty.app.data.bank

/**
 * Contract for a bank integration that can pull accounts and statements.
 * Monobank is implemented; PrivatBank, Raiffeisen and Revolut have no public personal API with
 * simple token auth, so they are captured through notifications only. Implement this interface to add one.
 */
interface BankConnector {
    val code: String
    val displayName: String

    /** Returns null when the credentials are valid, otherwise a user-facing error message. */
    suspend fun validate(): String?

    /** Imports accounts as wallets and recent statements as transactions; returns a short summary. */
    suspend fun sync(): SyncResult
}

data class SyncResult(val walletsCreated: Int, val transactionsImported: Int, val error: String? = null)
