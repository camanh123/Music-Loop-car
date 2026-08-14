package com.musicloop.car.storage

interface MusicFolderRepository {
    fun load(): MusicFolderRecord?
    fun save(record: MusicFolderRecord)
}
