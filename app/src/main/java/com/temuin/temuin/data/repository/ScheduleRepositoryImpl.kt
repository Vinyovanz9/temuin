package com.temuin.temuin.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.temuin.temuin.data.model.Schedule
import com.temuin.temuin.domain.repository.ScheduleRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
): ScheduleRepository {
    private val schedulesCollection = firestore.collection("schedules")

    // Schedule operations
    override suspend fun createSchedule(schedule: Schedule): Result<Schedule> = try {
        val docRef = schedulesCollection.document()
        val newSchedule = schedule.copy(id = docRef.id)
        docRef.set(newSchedule).await()
        Result.success(newSchedule)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateSchedule(schedule: Schedule): Result<Schedule> = try {
        val updatedSchedule = schedule.copy(updatedAt = System.currentTimeMillis())
        
        println("ScheduleRepository: Updating schedule ${updatedSchedule.id}")
        println("ScheduleRepository: New status = ${updatedSchedule.status}")
        
        // Convert participant status map to string values
        val participantStatusMap = updatedSchedule.participantStatus.mapValues { it.value.name }
        
        val scheduleMap = mapOf(
            "userId" to updatedSchedule.userId,
            "title" to updatedSchedule.title,
            "description" to updatedSchedule.description,
            "location" to updatedSchedule.location,
            "startTime" to updatedSchedule.startTime,
            "endTime" to updatedSchedule.endTime,
            "reminderHours" to updatedSchedule.reminderHours,
            "allowReschedule" to updatedSchedule.allowReschedule,
            "participants" to updatedSchedule.participants,
            "participantStatus" to participantStatusMap,
            "status" to updatedSchedule.status.name,
            "updatedAt" to updatedSchedule.updatedAt
        )
        
        println("ScheduleRepository: Schedule map created, attempting Firestore update...")
        
        try {
            schedulesCollection.document(updatedSchedule.id)
                .set(scheduleMap)  // Using set instead of update to ensure all fields are written
                .await()
            println("ScheduleRepository: Firestore update successful for schedule: ${updatedSchedule.id}")
            Result.success(updatedSchedule)
        } catch (e: Exception) {
            println("ScheduleRepository: Firestore update failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    } catch (e: Exception) {
        println("ScheduleRepository: General error in updateSchedule: ${e.message}")
        e.printStackTrace()
        Result.failure(e)
    }

    override fun getSchedulesForDate(userId: String, startOfDay: Long, endOfDay: Long): Flow<List<Schedule>> = callbackFlow {
        // Create two queries: one for creator and one for participant
        val creatorQuery = schedulesCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("startTime", startOfDay)
            .whereLessThanOrEqualTo("startTime", endOfDay)

        val participantQuery = schedulesCollection
            .whereArrayContains("participants", userId)
            .whereGreaterThanOrEqualTo("startTime", startOfDay)
            .whereLessThanOrEqualTo("startTime", endOfDay)

        var creatorSchedules = listOf<Schedule>()
        var participantSchedules = listOf<Schedule>()

        // Function to combine and send schedules
        fun sendCombinedSchedules() {
            val allSchedules = (creatorSchedules + participantSchedules)
                .distinctBy { it.id }
                .sortedBy { it.startTime }
            trySend(allSchedules)
        }

        // Listen to creator schedules
        val creatorListener = creatorQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("Error in creator query: ${error.message}")
                return@addSnapshotListener
            }
            
            creatorSchedules = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Schedule::class.java)?.copy(id = doc.id)
            } ?: emptyList()

            sendCombinedSchedules()
        }

        // Listen to participant schedules
        val participantListener = participantQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("Error in participant query: ${error.message}")
                return@addSnapshotListener
            }

            participantSchedules = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Schedule::class.java)?.copy(id = doc.id)
            } ?: emptyList()

            sendCombinedSchedules()
        }
        
        awaitClose { 
            creatorListener.remove()
            participantListener.remove()
        }
    }

    override suspend fun getScheduleById(scheduleId: String): Result<Schedule> = try {
        val doc = schedulesCollection.document(scheduleId).get().await()
        val schedule = doc.toObject(Schedule::class.java)?.copy(id = doc.id)
        if (schedule != null) {
            Result.success(schedule)
        } else {
            Result.failure(Exception("Schedule not found"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun getSchedulesForMonth(userId: String, startOfMonth: Long, endOfMonth: Long): Flow<List<Schedule>> = callbackFlow {
        // Create two queries: one for creator and one for participant
        val creatorQuery = schedulesCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("startTime", startOfMonth)
            .whereLessThanOrEqualTo("startTime", endOfMonth)

        val participantQuery = schedulesCollection
            .whereArrayContains("participants", userId)
            .whereGreaterThanOrEqualTo("startTime", startOfMonth)
            .whereLessThanOrEqualTo("startTime", endOfMonth)

        var creatorSchedules = listOf<Schedule>()
        var participantSchedules = listOf<Schedule>()

        // Function to combine and send schedules
        fun sendCombinedSchedules() {
            val allSchedules = (creatorSchedules + participantSchedules)
                .distinctBy { it.id }
                .sortedBy { it.startTime }
            trySend(allSchedules)
        }

        // Listen to creator schedules
        val creatorListener = creatorQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("Error in creator query: ${error.message}")
                return@addSnapshotListener
            }
            
            creatorSchedules = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Schedule::class.java)?.copy(id = doc.id)
            } ?: emptyList()

            sendCombinedSchedules()
        }

        // Listen to participant schedules
        val participantListener = participantQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("Error in participant query: ${error.message}")
                return@addSnapshotListener
            }

            participantSchedules = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Schedule::class.java)?.copy(id = doc.id)
            } ?: emptyList()

            sendCombinedSchedules()
        }
        
        awaitClose { 
            creatorListener.remove()
            participantListener.remove()
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Result<Unit> = try {
        schedulesCollection.document(scheduleId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}