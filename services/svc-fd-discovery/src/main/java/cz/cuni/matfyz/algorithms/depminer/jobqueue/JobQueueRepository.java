package cz.cuni.matfyz.algorithms.depminer.jobqueue;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface JobQueueRepository extends JpaRepository<JobQueue, UUID> {

    @Query(value = "SELECT TOP (1) * FROM JobQueue WITH (ROWLOCK, READPAST, UPDLOCK) WHERE JobStatus = 'NEW' AND ServiceType = 'FD_DISCOVERY' ORDER BY CreatedAt", nativeQuery = true)
    Optional<JobQueue> findNextNewForUpdate();
}
