package com.jinki.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update


@Entity(tableName = "queue_order")
data class QueueOrderEntry(
    @PrimaryKey val storageKey: String,
    val position: Int,
)


@Entity(tableName = "queue_removed")
data class QueueRemovedEntry(
    @PrimaryKey val storageKey: String,
)

@Dao
abstract class QueueDao {

    @Query("SELECT storageKey FROM queue_order ORDER BY position ASC")
    abstract suspend fun order(): List<String>

    @Query("SELECT storageKey FROM queue_removed")
    abstract suspend fun removed(): List<String>

    @Query("DELETE FROM queue_order")
    abstract suspend fun clearOrder()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrder(entries: List<QueueOrderEntry>)

    @Transaction
    open suspend fun replaceOrder(entries: List<QueueOrderEntry>) {
        clearOrder()
        insertOrder(entries)
    }

    @Query("DELETE FROM queue_removed")
    abstract suspend fun clearRemoved()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRemoved(entries: List<QueueRemovedEntry>)

    @Transaction
    open suspend fun replaceRemoved(entries: List<QueueRemovedEntry>) {
        clearRemoved()
        insertRemoved(entries)
    }

    @Query("DELETE FROM queue_removed WHERE storageKey = :key")
    abstract suspend fun deleteRemoved(key: String)
}

class QueueStore(private val dao: QueueDao) {

    suspend fun loadOrder(): List<String> = dao.order()

    suspend fun loadRemoved(): Set<String> = dao.removed().toHashSet()

    
    private val _queueList = MutableStateFlow<List<Track>>(emptyList())
    val queueList: StateFlow<List<Track>> = _queueList

    
    fun addToQueue(track: Track) {
        _queueList.update { current ->
            if (current.any { it.storageKey == track.storageKey }) current
            else current + track
        }
    }

    
    fun removeFromQueue(index: Int): Track? {
        var removed: Track? = null
        _queueList.update { current ->
            if (index !in current.indices) current
            else {
                removed = current[index]
                current.toMutableList().apply { removeAt(index) }
            }
        }
        return removed
    }

    
    fun insertAt(index: Int, track: Track) {
        _queueList.update { current ->
            if (current.any { it.storageKey == track.storageKey }) current
            else current.toMutableList().apply { add(index.coerceIn(0, size), track) }
        }
    }

    
    fun moveInQueue(from: Int, to: Int): Boolean {
        var moved = false
        _queueList.update { current ->
            if (from == to || from !in current.indices || to !in current.indices) {
                current
            } else {
                moved = true
                current.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
        return moved
    }

    
    fun replaceAll(tracks: List<Track>) {
        _queueList.value = tracks
    }

    suspend fun saveOrder(keys: List<String>) =
        dao.replaceOrder(keys.mapIndexed { i, k -> QueueOrderEntry(k, i) })

    suspend fun saveRemoved(keys: Set<String>) =
        dao.replaceRemoved(keys.map { QueueRemovedEntry(it) })

    suspend fun markRemoved(key: String) =
        dao.insertRemoved(listOf(QueueRemovedEntry(key)))

    suspend fun unmarkRemoved(key: String) = dao.deleteRemoved(key)
}
