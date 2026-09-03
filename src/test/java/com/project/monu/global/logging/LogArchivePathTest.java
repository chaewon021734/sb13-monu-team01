package com.project.monu.global.logging;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LogArchivePathTest {

    @Test
    void 날짜별_로컬_경로와_S3_키를_같은_규칙으로_생성한다() {
        // given
        LogArchiveProperties properties = new LogArchiveProperties(
                "local",
                "logs",
                "build/log-archives",
                "/app-logs/",
                "0 30 2 * * *",
                "Asia/Seoul",
                true
        );
        LogArchivePath archivePath = new LogArchivePath(properties);
        LocalDate logDate = LocalDate.of(2026, 8, 28);

        // when & then
        assertThat(archivePath.sourceFile(logDate))
                .isEqualTo(Path.of("logs", "monu.2026-08-28.log"));
        assertThat(archivePath.localDestinationFile(logDate))
                .isEqualTo(Path.of("build/log-archives", "2026", "08", "28", "monu-2026-08-28.log"));
        assertThat(archivePath.s3Key(logDate))
                .isEqualTo("app-logs/2026/08/28/monu-2026-08-28.log");
    }
}
