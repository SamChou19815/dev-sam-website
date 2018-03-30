package com.developersam.scheduler

import com.developersam.auth.FirebaseUser
import com.developersam.util.set
import com.developersam.util.setString
import com.developersam.util.upsertEntity
import com.developersam.util.yesterday
import com.google.cloud.Timestamp
import com.google.cloud.datastore.Key
import java.util.Date

/**
 * A simple data class of scheduler item that can be easily written into
 * database. It is also a simplified object for JSON transmission.
 */
class SchedulerItemData private constructor() {

    /**
     * An optional field that that essentially tells the difference between
     * a new scheduler item or an old one.
     */
    private val keyString: String? = null
    /**
     * Description of the item.
     */
    private val description: String? = null
    /**
     * Deadline of the item.
     */
    private val deadline: Date? = null
    /**
     * The optional deadline hour.
     */
    private val deadlineHour: Long? = null
    /**
     * Optional detail of the item.
     */
    private var detail: String? = null

    /**
     * [sanityCheck] returns whether the given inputs are all valid.
     */
    private fun sanityCheck(): Boolean {
        if (description == null || deadline == null
                || description.trim().isEmpty()
                || deadline < yesterday) {
            return false
        }
        if (deadlineHour != null && deadlineHour !in 1..24) {
            // Deadline hours must be in range.
            return false
        }
        return true
    }

    /**
     * [writeToDatabase] updates the corresponding scheduler item with the new
     * given data if the request is legit (i.e. no missing info and the item
     * really belongs to the given [user]).
     * It returns whether the operation was successful.
     */
    fun writeToDatabase(user: FirebaseUser): Boolean {
        if (!sanityCheck()) {
            return false
        }
        val userEmail = user.email
        val key = keyString?.let(Key::fromUrlSafe)
        return upsertEntity(kind = "SchedulerItem", key = key, validator = {
            it.getString("userEmail") == userEmail
        }) {
            it.apply {
                set("userEmail", userEmail)
                set("description", description)
                set("deadline", Timestamp.of(deadline))
                set("deadlineHour", deadlineHour)
                set("completed", false)
                setString("detail", detail)
            }
        }
    }

}