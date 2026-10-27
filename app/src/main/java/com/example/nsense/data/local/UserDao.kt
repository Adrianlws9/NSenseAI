package com.example.nsense.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface UserDao {

    @Insert
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?
}