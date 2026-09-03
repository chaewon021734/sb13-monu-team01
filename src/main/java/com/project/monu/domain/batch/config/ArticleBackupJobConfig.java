package com.project.monu.domain.batch.config;

import com.project.monu.domain.article.dto.response.ArticleBackupResultDto;
import com.project.monu.domain.article.service.ArticleBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.ZoneId;

@Configuration
@ConditionalOnProperty(name = "batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ArticleBackupJobConfig {

    private static final ZoneId BACKUP_ZONE = ZoneId.of("Asia/Seoul");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager txManager;
    private final ArticleBackupService articleBackupService;

    @Bean
    public Job articleBackupJob(Step articleBackupStep) {
        // Spring Batch의 Job은 실행 단위입니다. 여기서는 "기사 백업" 전체 작업을 하나의 Job으로 둡니다.
        return new JobBuilder("articleBackupJob", jobRepository)
                .start(articleBackupStep)
                .build();
    }

    @Bean
    public Step articleBackupStep() {
        // 현재 백업은 복잡한 chunk 처리 없이 서비스 메서드 1번 호출이면 충분해서 tasklet Step으로 구성합니다.
        return new StepBuilder("articleBackupStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // 수집 배치가 하루 중 여러 번 돌 수 있으므로, 안정적으로 확정된 전날 기사를 백업합니다.
                    LocalDate backupDate = LocalDate.now(BACKUP_ZONE).minusDays(1);
                    ArticleBackupResultDto result = articleBackupService.backup(backupDate);
                    log.info(">>> 기사 백업 완료 date={}, storage={}, key={}, articleCount={}",
                            result.backupDate(),
                            result.storage(),
                            result.key(),
                            result.articleCount()
                    );
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }
}
