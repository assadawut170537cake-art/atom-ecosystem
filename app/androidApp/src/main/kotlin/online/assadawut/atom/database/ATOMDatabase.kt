package online.assadawut.atom.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryRecord::class,
        QueueRecord::class,
        PersonaRecord::class,
        SyncRecordEntity::class,
        TelemetryRecord::class,
        ProfileRecord::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ATOMDatabase : RoomDatabase() {
    abstract fun dao(): ATOMDao

    companion object {
        fun create(context: Context): ATOMDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ATOMDatabase::class.java,
                "atom_db"
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
