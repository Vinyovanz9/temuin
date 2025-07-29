package com.temuin.temuin.domain.repository

import com.temuin.temuin.data.model.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {

    suspend fun createSchedule(schedule: Schedule): Result<Schedule>
    suspend fun updateSchedule(schedule: Schedule): Result<Schedule>
    fun getSchedulesForDate(userId: String, startOfDay: Long, endOfDay: Long): Flow<List<Schedule>>
    suspend fun getScheduleById(scheduleId: String): Result<Schedule>
    fun getSchedulesForMonth(userId: String, startOfMonth: Long, endOfMonth: Long): Flow<List<Schedule>>
}