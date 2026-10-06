package com.jinki.app.data.source


interface StreamResolver {

    
    val key: String

    
    fun supports(source: String): Boolean

    
    suspend fun resolve(ref: SourceRef): ResolvedStream
}
