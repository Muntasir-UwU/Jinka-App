package com.jinki.app.data.source

import com.jinki.app.data.source.newpipe.NewPipeBackend
import kotlinx.coroutines.CancellationException


object SourceRegistry {

    
    private val newPipe: NewPipeBackend by lazy { NewPipeBackend() }

    
    private val searchChain: List<SearchSource> get() = listOf(newPipe)

    private val resolvers: List<StreamResolver> get() = listOf(newPipe)

    
    suspend fun search(query: String, page: Int = 0): List<SearchHit> {
        var lastFailure: SourceException? = null
        for (backend in searchChain) {
            val hits = try {
                backend.search(query, page)
            } catch (c: CancellationException) {
                throw c
            } catch (e: SourceException) {
                lastFailure = lastFailure ?: e
                continue
            }
            if (hits.isNotEmpty()) return hits
        }
        throw lastFailure ?: SourceException("No search backend is available.")
    }

    
    suspend fun resolve(ref: SourceRef): ResolvedStream {
        val resolver = resolvers.firstOrNull { it.supports(ref.source) }
            ?: throw SourceException("Nothing can play ${ref.source} tracks.")
        return resolver.resolve(ref)
    }
}
