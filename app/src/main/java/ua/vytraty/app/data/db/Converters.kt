package ua.vytraty.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun walletTypeToString(v: WalletType): String = v.name
    @TypeConverter fun stringToWalletType(v: String): WalletType = WalletType.valueOf(v)
    @TypeConverter fun txKindToString(v: TxKind): String = v.name
    @TypeConverter fun stringToTxKind(v: String): TxKind = TxKind.valueOf(v)
    @TypeConverter fun txSourceToString(v: TxSource): String = v.name
    @TypeConverter fun stringToTxSource(v: String): TxSource = TxSource.valueOf(v)
    @TypeConverter fun recurrenceToString(v: Recurrence): String = v.name
    @TypeConverter fun stringToRecurrence(v: String): Recurrence = Recurrence.valueOf(v)
}
