package com.project.monu.domain.batch.controller;

import com.project.monu.domain.batch.config.ArticleRestoreJobConfig;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BatchControllerTest {

    private static final String BATCH_SECRET = "test-batch-secret";

    @Mock
    private JobOperator jobOperator;

    @Mock
    private Job articleCollectJob;

    @Mock
    private Job articleBackupJob;

    @Mock
    private Job articleRestoreJob;

    @Test
    @DisplayName("POST /api/batch/backup 요청 시 기사 백업 Job을 실행한다")
    void 백업_배치_수동_실행_요청을_처리한다() throws Exception {
        // given
        // Controller만 단독으로 테스트해서, HTTP 요청이 올바른 Job 실행으로 이어지는지만 확인합니다.
        MockMvc mockMvc = mockMvc();
        when(jobOperator.start(eq(articleBackupJob), any(JobParameters.class)))
                .thenReturn(jobExecution(BatchStatus.COMPLETED, ExitStatus.COMPLETED));

        // when & then
        mockMvc.perform(post("/api/batch/backup")
                        .header("X-BATCH-SECRET", BATCH_SECRET))
                .andExpect(status().isOk())
                .andExpect(content().string("기사 백업 배치 실행 완료"));

        ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(articleBackupJob), paramsCaptor.capture());

        // Spring Batch는 같은 JobParameters로 같은 Job을 다시 실행하지 못하므로 timestamp를 넣어 매번 새 실행으로 만듭니다.
        assertThat(paramsCaptor.getValue().getLong("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("POST /api/batch/restore 요청 시 지정 날짜 기사 복구 Job을 실행한다")
    void 복구_수동_실행_요청을_처리한다() throws Exception {
        // given
        // 복구는 날짜 파라미터가 필요하므로, Controller가 LocalDate로 바인딩해 JobParameters에 담는지 확인합니다.
        LocalDate restoreDate = LocalDate.of(2026, 8, 20);
        when(jobOperator.start(eq(articleRestoreJob), any(JobParameters.class)))
                .thenReturn(jobExecution(BatchStatus.COMPLETED, ExitStatus.COMPLETED));

        // when & then
        mockMvc().perform(post("/api/batch/restore")
                        .param("date", "2026-08-20")
                        .header("X-BATCH-SECRET", BATCH_SECRET))
                .andExpect(status().isOk())
                .andExpect(content().string("기사 복구 배치 실행 완료"));

        ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(articleRestoreJob), paramsCaptor.capture());

        assertThat(paramsCaptor.getValue().getString(ArticleRestoreJobConfig.RESTORE_DATE_PARAM))
                .isEqualTo(restoreDate.toString());
        assertThat(paramsCaptor.getValue().getLong("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("수동 배치 실행 secret이 없으면 거부한다")
    void 수동_배치_실행_secret이_없으면_거부한다() throws Exception {
        mockMvc().perform(post("/api/batch/backup"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("수동 배치 Job이 실패하면 500으로 응답한다")
    void 수동_배치_job이_실패하면_500으로_응답한다() throws Exception {
        when(jobOperator.start(eq(articleBackupJob), any(JobParameters.class)))
                .thenReturn(jobExecution(BatchStatus.FAILED, ExitStatus.FAILED));

        mockMvc().perform(post("/api/batch/backup")
                        .header("X-BATCH-SECRET", BATCH_SECRET))
                .andExpect(status().isInternalServerError());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new BatchController(
                        jobOperator,
                        articleCollectJob,
                        articleBackupJob,
                        articleRestoreJob,
                        BATCH_SECRET
                ))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private JobExecution jobExecution(BatchStatus status, ExitStatus exitStatus) {
        JobExecution jobExecution = new JobExecution(1L, null, new JobParameters());
        jobExecution.setStatus(status);
        jobExecution.setExitStatus(exitStatus);
        return jobExecution;
    }
}
