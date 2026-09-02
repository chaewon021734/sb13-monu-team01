package com.project.monu.domain.article.service;

import com.project.monu.domain.article.backup.ArticleBackupStorage;
import com.project.monu.domain.article.dto.response.ArticleBackupResultDto;
import com.project.monu.domain.article.dto.response.ArticleRestoreResultDto;
import com.project.monu.domain.article.entity.Article;
import com.project.monu.domain.article.entity.ArticleBackup;
import com.project.monu.domain.article.entity.ArticleInterest;
import com.project.monu.domain.article.entity.ArticleRestore;
import com.project.monu.domain.article.entity.ArticleSource;
import com.project.monu.domain.article.entity.SourceType;
import com.project.monu.domain.article.repository.ArticleBackupRepository;
import com.project.monu.domain.article.repository.ArticleInterestRepository;
import com.project.monu.domain.article.repository.ArticleRepository;
import com.project.monu.domain.article.repository.ArticleRestoreRepository;
import com.project.monu.domain.article.repository.ArticleSourceRepository;
import com.project.monu.domain.interest.entity.Interest;
import com.project.monu.domain.interest.repository.InterestRepository;
import com.project.monu.global.exception.BusinessException;
import com.project.monu.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArticleBackupServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleSourceRepository articleSourceRepository;

    @Mock
    private ArticleInterestRepository articleInterestRepository;

    @Mock
    private ArticleBackupRepository articleBackupRepository;

    @Mock
    private ArticleRestoreRepository articleRestoreRepository;

    @Mock
    private InterestRepository interestRepository;

    @Mock
    private ArticleBackupStorage backupStorage;

    private final ObjectMapper objectMapper = new JsonMapper();

    @Test
    @DisplayName("날짜 기준으로 기사 백업 파일을 생성한다")
    void 날짜_기준으로_기사_백업_파일을_생성한다() {
        // given
        // 2026-08-21 하루치 백업은 한국 시간 기준 00:00부터 다음날 00:00 직전까지 조회합니다.
        LocalDate backupDate = LocalDate.of(2026, 8, 21);
        ArticleSource source = source("NAVER");
        Article article = article(
                source,
                "https://example.com/news/1",
                "AI 뉴스",
                Instant.parse("2026-08-21T03:00:00Z")
        );
        UUID articleId = UUID.randomUUID();
        ReflectionTestUtils.setField(article, "id", articleId);
        Interest interest = Interest.create("축구");
        ArticleInterest articleInterest = ArticleInterest.builder()
                .article(article)
                .interest(interest)
                .build();

        given(articleRepository.findByPublishDateGreaterThanEqualAndPublishDateLessThan(
                Instant.parse("2026-08-20T15:00:00Z"),
                Instant.parse("2026-08-21T15:00:00Z")
        )).willReturn(List.of(article));
        given(articleInterestRepository.findByArticle_Id(articleId))
                .willReturn(List.of(articleInterest));

        given(articleBackupRepository.findByS3Key("article-backups/2026-08-21.jsonl"))
                .willReturn(Optional.empty());
        given(backupStorage.storageName()).willReturn("local");

        // when
        ArticleBackupResultDto result = service().backup(backupDate);

        // then
        assertThat(result.backupDate()).isEqualTo(backupDate);
        assertThat(result.storage()).isEqualTo("local");
        assertThat(result.key()).isEqualTo("article-backups/2026-08-21.jsonl");
        assertThat(result.articleCount()).isEqualTo(1);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(backupStorage).save(keyCaptor.capture(), contentCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("article-backups/2026-08-21.jsonl");
        assertThat(contentCaptor.getValue())
                .contains("NAVER")
                .contains("https://example.com/news/1")
                .contains("AI 뉴스")
                .contains("interestNames")
                .contains("축구");

        ArgumentCaptor<ArticleBackup> backupCaptor = ArgumentCaptor.forClass(ArticleBackup.class);
        verify(articleBackupRepository).save(backupCaptor.capture());

        ArticleBackup savedBackup = backupCaptor.getValue();
        assertThat(savedBackup.getBackupDate()).isEqualTo(backupDate);
        assertThat(savedBackup.getS3Bucket()).isEqualTo("local");
        assertThat(savedBackup.getS3Key()).isEqualTo("article-backups/2026-08-21.jsonl");
        assertThat(savedBackup.getArticleCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("백업에는 있고 DB에는 없는 기사만 복구한다")
    void 백업에는_있고_DB에는_없는_기사만_복구한다() {
        // given
        // 백업 파일에는 2건이 있지만, 1건은 이미 DB에 있다고 가정합니다.
        // 따라서 복구 대상은 sourceUrl 기준으로 DB에 없는 두 번째 기사 1건뿐입니다.
        LocalDate restoreDate = LocalDate.of(2026, 8, 21);
        String key = "article-backups/2026-08-21.jsonl";
        String backupContent = """
                {"sourceName":"NAVER","sourceUrl":"https://example.com/news/1","title":"기존 기사","publishDate":"2026-08-21T01:00:00Z","summary":"요약1"}
                {"sourceName":"NAVER","sourceUrl":"https://example.com/news/2","title":"유실 기사","publishDate":"2026-08-21T02:00:00Z","summary":"요약2","interestNames":["축구"]}
                """;
        ArticleSource naver = source("NAVER");
        Interest interest = Interest.create("축구");
        UUID restoredArticleId = UUID.randomUUID();
        ArticleBackup backup = ArticleBackup.create(
                restoreDate,
                "local",
                key,
                2L
        );

        given(backupStorage.exists(key)).willReturn(true);
        given(backupStorage.load(key)).willReturn(backupContent);
        given(articleBackupRepository.findTopByBackupDateOrderByCreatedAtDesc(restoreDate))
                .willReturn(Optional.of(backup));
        given(articleRepository.existsBySourceUrl("https://example.com/news/1")).willReturn(true);
        given(articleRepository.existsBySourceUrl("https://example.com/news/2")).willReturn(false);
        given(articleSourceRepository.findByName("NAVER")).willReturn(Optional.of(naver));
        given(interestRepository.findByNameIn(List.of("축구"))).willReturn(List.of(interest));
        given(articleRepository.save(any(Article.class))).willAnswer(invocation -> {
            Article savedArticle = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedArticle, "id", restoredArticleId);
            return savedArticle;
        });

        // when
        ArticleRestoreResultDto result = service().restore(restoreDate);

        // then
        assertThat(result.restoreDate()).isEqualTo(Instant.parse("2026-08-20T15:00:00Z"));
        assertThat(result.restoredArticleIds()).containsExactly(restoredArticleId);
        assertThat(result.restoredArticleCount()).isEqualTo(1);

        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(articleCaptor.capture());

        Article restoredArticle = articleCaptor.getValue();
        assertThat(restoredArticle.getSource()).isEqualTo(naver);
        assertThat(restoredArticle.getSourceUrl()).isEqualTo("https://example.com/news/2");
        assertThat(restoredArticle.getTitle()).isEqualTo("유실 기사");
        assertThat(restoredArticle.getPublishDate()).isEqualTo(Instant.parse("2026-08-21T02:00:00Z"));
        assertThat(restoredArticle.getSummary()).isEqualTo("요약2");

        ArgumentCaptor<ArticleRestore> restoreCaptor = ArgumentCaptor.forClass(ArticleRestore.class);
        verify(articleRestoreRepository).save(restoreCaptor.capture());

        ArticleRestore savedRestore = restoreCaptor.getValue();
        assertThat(savedRestore.getRestoreDate()).isEqualTo(restoreDate);
        assertThat(savedRestore.getRestoredCount()).isEqualTo(1L);
        assertThat(savedRestore.getBackup()).isEqualTo(backup);

        verify(articleInterestRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("복구할 백업 파일이 없으면 예외를 던진다")
    void 복구할_백업_파일이_없으면_예외를_던진다() {
        // given
        LocalDate restoreDate = LocalDate.of(2026, 8, 21);
        given(backupStorage.exists("article-backups/2026-08-21.jsonl")).willReturn(false);

        ArticleBackupService service = service();

        // when & then
        assertThatThrownBy(() -> service.restore(restoreDate))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ARTICLE_BACKUP_NOT_FOUND));
    }

    @Test
    @DisplayName("날짜 범위 복구에서 일부 날짜 백업이 없으면 해당 날짜만 건너뛴다")
    void 날짜_범위_복구에서_일부_날짜_백업이_없으면_해당_날짜만_건너뛴다() {
        // given
        LocalDate firstDate = LocalDate.of(2026, 8, 21);
        LocalDate missingDate = LocalDate.of(2026, 8, 22);
        LocalDate lastDate = LocalDate.of(2026, 8, 23);

        String firstKey = "article-backups/2026-08-21.jsonl";
        String missingKey = "article-backups/2026-08-22.jsonl";
        String lastKey = "article-backups/2026-08-23.jsonl";

        ArticleBackup firstBackup = ArticleBackup.create(firstDate, "local", firstKey, 0L);
        ArticleBackup lastBackup = ArticleBackup.create(lastDate, "local", lastKey, 0L);

        given(backupStorage.exists(firstKey)).willReturn(true);
        given(backupStorage.exists(missingKey)).willReturn(false);
        given(backupStorage.exists(lastKey)).willReturn(true);
        given(backupStorage.load(firstKey)).willReturn("");
        given(backupStorage.load(lastKey)).willReturn("");
        given(articleBackupRepository.findTopByBackupDateOrderByCreatedAtDesc(firstDate))
                .willReturn(Optional.of(firstBackup));
        given(articleBackupRepository.findTopByBackupDateOrderByCreatedAtDesc(lastDate))
                .willReturn(Optional.of(lastBackup));

        // when
        List<ArticleRestoreResultDto> results = service().restore(
                Instant.parse("2026-08-20T15:00:00Z"),
                Instant.parse("2026-08-23T14:59:59Z")
        );

        // then
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(ArticleRestoreResultDto::restoreDate)
                .containsExactly(
                        Instant.parse("2026-08-20T15:00:00Z"),
                        Instant.parse("2026-08-22T15:00:00Z")
                );
    }

    @Test
    @DisplayName("날짜 범위 복구에서 복구 가능한 백업이 하나도 없으면 404 예외를 던진다")
    void 날짜_범위_복구에서_복구_가능한_백업이_하나도_없으면_예외를_던진다() {
        // given
        given(backupStorage.exists("article-backups/2026-08-21.jsonl")).willReturn(false);
        given(backupStorage.exists("article-backups/2026-08-22.jsonl")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> service().restore(
                Instant.parse("2026-08-20T15:00:00Z"),
                Instant.parse("2026-08-22T14:59:59Z")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ARTICLE_BACKUP_NOT_FOUND));
    }

    private ArticleBackupService service() {
        return new ArticleBackupService(
                articleRepository,
                articleSourceRepository,
                articleInterestRepository,
                articleBackupRepository,
                articleRestoreRepository,
                interestRepository,
                backupStorage,
                objectMapper
        );
    }

    private ArticleSource source(String name) {
        return ArticleSource.builder()
                .name(name)
                .type(SourceType.API)
                .sourceUrl("https://openapi.naver.com")
                .build();
    }

    private Article article(
            ArticleSource source,
            String sourceUrl,
            String title,
            Instant publishDate
    ) {
        return Article.builder()
                .source(source)
                .sourceUrl(sourceUrl)
                .title(title)
                .publishDate(publishDate)
                .summary("요약")
                .build();
    }
}
