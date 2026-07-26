package com.pxuzy.floatingpen

import android.content.res.Resources

/** Density extension properties — replaces `(X * dp).toInt()` everywhere */
val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.dp: Float get() = this * Resources.getSystem().displayMetrics.density
val Int.dpf: Float get() = this * Resources.getSystem().displayMetrics.density
