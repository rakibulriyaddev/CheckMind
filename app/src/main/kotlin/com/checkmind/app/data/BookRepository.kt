package com.checkmind.app.data

import android.content.Context
import android.util.Log
import com.checkmind.chess.book.BookCodec
import com.checkmind.chess.book.OpeningBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Anything that can supply the opening book. `null` means "still loading". */
interface BookSource {
    val book: StateFlow<OpeningBook?>
}

/** Loads assets/book.bin once, off the main thread. On failure the app runs with an empty book. */
class BookRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BookSource {
    private val _book = MutableStateFlow<OpeningBook?>(null)
    override val book: StateFlow<OpeningBook?> = _book.asStateFlow()

    fun start() {
        scope.launch {
            _book.value = try {
                context.assets.open(ASSET_NAME).use { BookCodec.read(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not load $ASSET_NAME, hints disabled", e)
                OpeningBook.EMPTY
            }
        }
    }

    private companion object {
        const val ASSET_NAME = "book.bin"
        const val TAG = "BookRepository"
    }
}
