package com.jinki.app.data.source


interface SearchSource {

    
    val key: String

    
    fun supports(source: String): Boolean

    
    suspend fun search(query: String, page: Int = 0): List<SearchHit>
}
