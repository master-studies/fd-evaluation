package genuineness.jobqueue;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobQueueRepository extends JpaRepository<JobQueue, UUID> {

    @Query(value = "SELECT TOP (1) * FROM JobQueue WITH (ROWLOCK, READPAST, UPDLOCK) WHERE JobStatus = 'NEW' AND ServiceType = 'GENUINENESS' ORDER BY CreatedAt", nativeQuery = true)
    Optional<JobQueue> findNextNewForUpdate();

    @Query(value = "SELECT TOP (1) * FROM JobQueue WHERE Payload = :filename AND ServiceType = 'FD_DISCOVERY' AND JobStatus = 'FINISHED' ORDER BY CompletedAt DESC", nativeQuery = true)
    Optional<JobQueue> findLatestFinishedFDDiscovery(@Param("filename") String filename);
}
