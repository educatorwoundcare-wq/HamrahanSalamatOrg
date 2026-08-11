package com.example

import androidx.room.RoomDatabase

fun test(builder: RoomDatabase.Builder<*>) {
    builder.fallbackToDestructiveMigration(true)
}
