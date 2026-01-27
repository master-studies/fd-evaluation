package succinctness.jobqueue;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "JobQueue")
public class JobQueue {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "JobId", columnDefinition = "uniqueidentifier")
    private UUID jobId;

    @Column(name = "JobStatus", nullable = false)
    private String jobStatus = "NEW";

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "StartedAt")
    private LocalDateTime startedAt;

    @Column(name = "CompletedAt")
    private LocalDateTime completedAt;

    @Column(name = "ErrorMessage")
    private String errorMessage;

    @Column(name = "Payload", columnDefinition = "NVARCHAR(MAX)")
    private String payload;

    @Column(name = "ServiceType", nullable = false)
    private String serviceType;

    @Column(name = "ResultData", columnDefinition = "NVARCHAR(MAX)")
    private String resultData;

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(String jobStatus) {
        this.jobStatus = jobStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getResultData() {
        return resultData;
    }

    public void setResultData(String resultData) {
        this.resultData = resultData;
    }
}
