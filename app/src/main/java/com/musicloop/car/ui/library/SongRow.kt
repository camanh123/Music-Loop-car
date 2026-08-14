package com.musicloop.car.ui.library

data class SongRow(
    val id: Long,
    val title: String,
    val artist: String,
    val durationLabel: String,
    val unplayable: Boolean
)
