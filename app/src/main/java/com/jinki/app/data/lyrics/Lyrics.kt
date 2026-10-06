package com.jinki.app.data.lyrics


sealed interface Lyrics {

    
    data object Loading : Lyrics

    
    data object Empty : Lyrics

    
    data class Plain(val text: String) : Lyrics

    
    data class Synced(val lines: List<LyricLine>) : Lyrics
}


val Lyrics.isLoading: Boolean get() = this is Lyrics.Loading


val Lyrics.hasContent: Boolean get() = this is Lyrics.Plain || this is Lyrics.Synced
