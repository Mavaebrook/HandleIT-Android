package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.data.learning.ReviewEvidence
import com.ai.assistance.operit.data.learning.ReviewTriggerPolicy
import com.ai.assistance.operit.data.repository.PendingReviewRepository

/**
 * Bounded bridge from runtime signals to the pending-review queue. Enqueues a review only when
 * ReviewTriggerPolicy decides one is warranted (no every-turn call).
 */
object ReviewTriggerEnqueuer {

    fun maybeEnqueue(
        context: Context,
        profileId: String,
        chatId: String,
        signals: ReviewTriggerPolicy.ReviewSignals,
        taskSummary: String,
        contextSummary: String,
        outcome: String,
        evidence: ReviewEvidence
    ): Boolean {
        val decision = ReviewTriggerPolicy.decide(signals)
        if (!decision.shouldReview) return false
        PendingReviewRepository(context, profileId).enqueue(
            chatId = chatId,
            triggerReason = decision.reason,
            taskSummary = taskSummary,
            contextSummary = contextSummary,
            outcome = outcome,
            evidenceJson = ReviewEvidence.encode(evidence)
        )
        return true
    }
}
