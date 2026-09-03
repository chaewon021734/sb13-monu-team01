package com.project.monu.domain.batch.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "batch.scheduler.enabled", havingValue = "true")
public class ArticleBackupScheduler {

    private final JobOperator jobOperator;
    private final Job articleBackupJob;

    public ArticleBackupScheduler(
            JobOperator jobOperator,
            @Qualifier("articleBackupJob") Job articleBackupJob
    ) {
        // 스케줄러에서도 Job Bean을 이름으로 지정해 수집 Job이 잘못 실행되지 않게 합니다.
        this.jobOperator = jobOperator;
        this.articleBackupJob = articleBackupJob;
    }

    // 매일 새벽 1시 30분에 전날 기사 데이터를 백업합니다.
    @Scheduled(cron = "0 30 1 * * *", zone = "Asia/Seoul")
    public void runBackupJob() {
        try {
            // 같은 JobInstance 재실행 오류를 피하려고 스케줄 실행마다 timestamp를 새로 넣습니다.
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobOperator.start(articleBackupJob, params);
            log.info(">>> 기사 백업 배치 스케줄 실행");
        } catch (Exception e) {
            log.error("기사 백업 배치 스케줄 실행 실패", e);
        }
    }
}
