package com.lime.touchlab.session

/** Подписка на снимки состояния сессии. Приходят на главном потоке. */
fun interface SessionStateListener {
    fun onStateChanged(state: SessionUiState)
}
