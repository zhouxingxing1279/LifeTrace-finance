package com.lifetrace.finance.ui

/** Keeps percentage calculations explicit when finance totals are stored as Long cents. */
internal operator fun Float.div(other: Long): Float = this / other.toFloat()
