package evaluationpatterns.jobqueue;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages interactive Q&amp;A for SUSPICIOUS functional dependencies.
 *
 * When the lattice algorithm classifies an FD as SUSPICIOUS, the worker thread
 * calls waitForAnswer(), which blocks until the frontend submits an answer via
 * the /jobs/{id}/answer endpoint (handled by submitAnswer()).
 */
@Service
public class SuspiciousAnswerService {

    private final ConcurrentHashMap<UUID, BlockingQueue<Boolean>> pending = new ConcurrentHashMap<>();

    /**
     * Block the calling thread until the frontend submits an answer for this job.
     * Returns true (genuine) or false (fake). Times out after 10 minutes → fake.
     */
    public boolean waitForAnswer(UUID jobId) throws InterruptedException {
        BlockingQueue<Boolean> queue = new LinkedBlockingQueue<>();
        pending.put(jobId, queue);
        try {
            Boolean answer = queue.poll(10, TimeUnit.MINUTES);
            return answer != null && answer;
        } finally {
            pending.remove(jobId);
        }
    }

    /**
     * Submit the user's answer for a pending question.
     * Returns true if there was a pending question, false otherwise.
     */
    public boolean submitAnswer(UUID jobId, boolean genuine) {
        BlockingQueue<Boolean> queue = pending.get(jobId);
        if (queue == null) return false;
        queue.offer(genuine);
        return true;
    }

    public boolean hasPendingQuestion(UUID jobId) {
        return pending.containsKey(jobId);
    }
}
