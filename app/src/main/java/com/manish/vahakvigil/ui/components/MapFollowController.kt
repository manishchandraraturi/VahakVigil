package com.manish.vahakvigil.ui.components

/** Camera-follow policy separated from Maps SDK objects for deterministic tests. */
class MapFollowController {
    var isFollowing: Boolean = true
        private set

    fun onGestureStarted() { isFollowing = false }
    fun onRecenterPressed() { isFollowing = true }
    fun shouldFollowNewFix(hasFix: Boolean): Boolean = isFollowing && hasFix
}