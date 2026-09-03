package com.project.monu.domain.batch.controller;

import com.project.monu.domain.batch.config.ArticleRestoreJobConfig;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * 기사 수집/백업/복구 작업을 수동으로 실행합니다.
 *
 * POST /api/batch/collect : 기사 수집 배치 실행
 * POST /api/batch/backup  : 전날 기사 백업 배치 실행
 * POST /api/batch/restore?date=2026-08-20 : 해당 날짜 백업 파일로 기사 복구 실행
 */


@RestController
@RequestMapping("/api/batch")
@ConditionalOnProperty(name = "batch.enabled", havingValue = "true")
public class BatchController {

    private static final String BATCH_SECRET_HEADER = "X-BATCH-SECRET";

    private final JobOperator jobOperator;
    private final Job articleCollectJob;
    private final Job articleBackupJob;
    private final Job articleRestoreJob;
    private final String manualSecret;

    public BatchController(
            JobOperator jobOperator,
            @Qualifier("articleCollectJob") Job articleCollectJob,
            @Qualifier("articleBackupJob") Job articleBackupJob,
            @Qualifier("articleRestoreJob") Job articleRestoreJob,
            @Value("${batch.manual.secret:}") String manualSecret
    ) {
        // Job Bean이 여러 개라서 @Qualifier로 수집/백업/복구 Job을 명확히 구분합니다.
        this.jobOperator = jobOperator;
        this.articleCollectJob = articleCollectJob;
        this.articleBackupJob = articleBackupJob;
        this.articleRestoreJob = articleRestoreJob;
        this.manualSecret = manualSecret;
    }


    @PostMapping(value = "/collect", produces = "text/plain;charset=UTF-8")
    public String runCollectJob(
            @RequestHeader(value = BATCH_SECRET_HEADER, required = false) String batchSecret
    ) throws Exception {
        validateManualBatchSecret(batchSecret);

        // timestamp를 넣어 매번 다른 JobParameters를 만들면 같은 Job을 반복 실행할 수 있습니다.
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        startJob(articleCollectJob, params, "기사 수집 배치 실행에 실패했습니다.");
        return "기사 수집 배치 실행 완료";
    }

    @PostMapping(value = "/backup", produces = "text/plain;charset=UTF-8")
    public String runBackupJob(
            @RequestHeader(value = BATCH_SECRET_HEADER, required = false) String batchSecret
    ) throws Exception {
        validateManualBatchSecret(batchSecret);

        // 수동 실행도 스케줄 실행과 같은 articleBackupJob을 사용합니다.
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        startJob(articleBackupJob, params, "기사 백업 배치 실행에 실패했습니다.");
        return "기사 백업 배치 실행 완료";
    }

    @PostMapping(value = "/restore", produces = "text/plain;charset=UTF-8")
    public String runRestoreJob(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestHeader(value = BATCH_SECRET_HEADER, required = false) String batchSecret
    ) throws Exception {
        validateManualBatchSecret(batchSecret);

        // 복구 날짜를 JobParameters로 넘겨 Spring Batch 실행 이력에 남깁니다.
        JobParameters params = new JobParametersBuilder()
                .addString(ArticleRestoreJobConfig.RESTORE_DATE_PARAM, date.toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        startJob(articleRestoreJob, params, "기사 복구 배치 실행에 실패했습니다.");
        return "기사 복구 배치 실행 완료";
    }

    private void startJob(Job job, JobParameters params, String failureMessage) throws Exception {
        JobExecution execution = jobOperator.start(job, params);
        BatchStatus status = execution.getStatus();

        if (status.isUnsuccessful()) {
            ExitStatus exitStatus = execution.getExitStatus();
            String exitDescription = exitStatus == null ? "" : exitStatus.getExitDescription();
            String reason = exitDescription == null || exitDescription.isBlank()
                    ? status.name()
                    : exitDescription;

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, failureMessage + " " + reason);
        }
    }

    private void validateManualBatchSecret(String batchSecret) {
        if (manualSecret == null || manualSecret.isBlank() || !manualSecret.equals(batchSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "배치 실행 권한이 없습니다.");
        }
    }
}
