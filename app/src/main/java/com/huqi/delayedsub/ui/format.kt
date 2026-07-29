package com.huqi.delayedsub.ui

import java.util.concurrent.TimeUnit

internal fun formatMs(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}
