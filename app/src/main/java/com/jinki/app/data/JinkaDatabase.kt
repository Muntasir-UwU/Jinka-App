package com.jinki.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM offline_tracks ORDER BY savedAt DESC")
    fun observeOffline(): Flow<List<OfflineTrack>>

    @Query("SELECT * FROM offline_tracks WHERE source = :source AND id = :id LIMIT 1")
    suspend fun find(source: String, id: String): OfflineTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: OfflineTrack)

    @Query("DELETE FROM offline_tracks WHERE source = :source AND id = :id")
    suspend fun delete(source: String, id: String)

    @Query("SELECT localPath FROM offline_tracks")
    suspend fun allLocalPaths(): List<String>
}

@Dao
interface PlaybackDao {

    @Query("SELECT * FROM playback_state WHERE id = 0")
    fun observe(): Flow<PlaybackState?>

    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun get(): PlaybackState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: PlaybackState)
}

@Dao
interface ListenDao {

    @Query("SELECT * FROM listen_history WHERE date >= :since ORDER BY date ASC")
    fun observeSince(since: String): Flow<List<ListenDay>>

    @Query("SELECT * FROM listen_history WHERE date = :date LIMIT 1")
    suspend fun getDay(date: String): ListenDay?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: ListenDay)
}

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics_cache WHERE storeKey = :storeKey LIMIT 1")
    suspend fun find(storeKey: String): LyricsCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LyricsCacheEntry)

    @Query("DELETE FROM lyrics_cache WHERE savedAt < :cutoff")
    suspend fun prune(cutoff: Long)
}

@Dao
interface PlaylistDao {

    @Query(
        """SELECT p.*, (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS entryCount
           FROM playlists p ORDER BY p.createdAt ASC"""
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntry>>

    @Query("SELECT playlistId FROM playlist_entries WHERE source = :source AND trackId = :trackId")
    fun observeContainers(source: String, trackId: String): Flow<List<Long>>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun deleteEntries(id: Long)

    @Insert
    suspend fun insertEntry(entry: PlaylistEntry): Long

    @Query("DELETE FROM playlist_entries WHERE entryId = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("SELECT MAX(position) FROM playlist_entries WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int?

    @Query(
        """SELECT * FROM playlist_entries
           WHERE playlistId = :playlistId AND source = :source AND trackId = :trackId LIMIT 1"""
    )
    suspend fun findEntry(playlistId: Long, source: String, trackId: String): PlaylistEntry?
}


val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lyrics_cache` (
                `storeKey` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `savedAt` INTEGER NOT NULL,
                PRIMARY KEY(`storeKey`)
            )
            """.trimIndent()
        )
    }
}


val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlists` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlist_entries` (
                `entryId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `playlistId` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `trackId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `thumbnailUrl` TEXT NOT NULL,
                `localPath` TEXT,
                `position` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}


val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `queue_order` (
                `storageKey` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                PRIMARY KEY(`storageKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `queue_removed` (
                `storageKey` TEXT NOT NULL,
                PRIMARY KEY(`storageKey`)
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [
        OfflineTrack::class,
        PlaybackState::class,
        ListenDay::class,
        LyricsCacheEntry::class,
        Playlist::class,
        PlaylistEntry::class,
        QueueOrderEntry::class,
        QueueRemovedEntry::class
    ],
    version = 4,
    exportSchema = false
)
abstract class JinkaDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun listenDao(): ListenDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao

    companion object {
        private const val NAME = "jinki.db"

        @Volatile
        private var instance: JinkaDatabase? = null

        
        fun get(context: Context): JinkaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JinkaDatabase::class.java,
                    NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
                    .setTransactionExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
                    .build()
                    .also { instance = it }
            }
    }
}
