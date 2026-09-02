package com.project.monu.domain.article.controller;

import com.project.monu.domain.article.dto.response.ArticleDto;
import com.project.monu.domain.article.dto.request.ArticleSearchCondition;
import com.project.monu.domain.article.dto.request.ArticleSortType;
import com.project.monu.domain.article.dto.response.ArticleRestoreResultDto;
import com.project.monu.domain.article.dto.response.ArticleViewDto;
import com.project.monu.domain.article.service.ArticleBackupService;
import com.project.monu.domain.article.service.ArticleService;
import com.project.monu.global.dto.CursorPageResponse;
import com.project.monu.global.exception.BusinessException;
import com.project.monu.global.exception.ErrorCode;
import com.project.monu.global.exception.GlobalHandlerException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = ArticleController.class)
class ArticleControllerTest {

    @Autowired
    private ArticleController articleController;

    @MockitoBean
    private ArticleService articleService;

    @MockitoBean
    private ArticleBackupService articleBackupService;

    @Test
    void 기사_목록을_조회한다() throws Exception {
        // given
        // Controller만 단독으로 테스트하기 위해 MockMvc를 직접 구성합니다.
        // Service는 MockitoBean으로 대체해서 요청 파라미터 바인딩과 응답 직렬화만 검증합니다.
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(articleController).build();

        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant publishDate = Instant.parse("2026-08-18T00:00:00Z");
        Instant nextAfter = Instant.parse("2026-08-17T00:00:00Z");

        ArticleDto article = new ArticleDto(
                articleId,
                "NAVER",
                "https://example.com/articles/1",
                "AI 뉴스",
                publishDate,
                "AI 뉴스 요약",
                10L,
                100L,
                true
        );

        CursorPageResponse<ArticleDto> response = CursorPageResponse.of(
                List.of(article),
                "10_" + articleId,
                nextAfter,
                10,
                1L,
                false
        );

        when(articleService.getArticles(any(ArticleSearchCondition.class), eq(userId)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/articles")
                        .param("keyword", "AI")
                        .param("interestId", UUID.randomUUID().toString())
                        .param("sourceIn", "NAVER")
                        .param("publishDateFrom", "2026-08-01T00:00:00Z")
                        .param("publishDateTo", "2026-08-31T23:59:59Z")
                        .param("orderBy", "commentCount")
                        .param("direction", "DESC")
                        .param("after", "2026-08-17T00:00:00Z")
                        .param("cursor", "10_" + articleId)
                        .param("limit", "10")
                        .header("MoNew-Request-User-ID", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(articleId.toString()))
                .andExpect(jsonPath("$.content[0].source").value("NAVER"))
                .andExpect(jsonPath("$.content[0].title").value("AI 뉴스"))
                .andExpect(jsonPath("$.content[0].viewedByMe").value(true))
                .andExpect(jsonPath("$.nextCursor").value("10_" + articleId))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 요청_파라미터를_검색조건으로_변환해_Service에_전달한다() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(articleController).build();

        UUID interestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(articleService.getArticles(any(ArticleSearchCondition.class), eq(userId)))
                .thenReturn(CursorPageResponse.of(List.of(), null, null, 20, 0L, false));

        // when
        mockMvc.perform(get("/api/articles")
                        .param("keyword", "경제")
                        .param("interestId", interestId.toString())
                        .param("sourceIn", "CHOSUN")
                        .param("publishDateFrom", "2026-08-01T00:00:00Z")
                        .param("publishDateTo", "2026-08-31T23:59:59Z")
                        .param("orderBy", "viewCount")
                        .param("direction", "ASC")
                        .param("after", "2026-08-10T00:00:00Z")
                        .param("cursor", "100_" + UUID.randomUUID())
                        .param("limit", "20")
                        .header("MoNew-Request-User-ID", userId.toString()))
                .andExpect(status().isOk());

        // then
        // Controller가 받은 query parameter를 ArticleSearchCondition으로 잘 묶어 Service에 넘겼는지 확인합니다.
        ArgumentCaptor<ArticleSearchCondition> conditionCaptor = ArgumentCaptor.forClass(ArticleSearchCondition.class);
        verify(articleService).getArticles(conditionCaptor.capture(), eq(userId));

        ArticleSearchCondition condition = conditionCaptor.getValue();

        assertThat(condition.keyword()).isEqualTo("경제");
        assertThat(condition.interestId()).isEqualTo(interestId);
        assertThat(condition.sourceIn()).containsExactly("CHOSUN");
        assertThat(condition.publishDateFrom()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(condition.publishDateTo()).isEqualTo(Instant.parse("2026-08-31T23:59:59Z"));
        assertThat(condition.orderBy()).isEqualTo(ArticleSortType.VIEW_COUNT);
        assertThat(condition.direction()).isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
        assertThat(condition.after()).isEqualTo(Instant.parse("2026-08-10T00:00:00Z"));
        assertThat(condition.cursor()).startsWith("100_");
        assertThat(condition.limit()).isEqualTo(20);
    }

    @Test
    void 선택_파라미터가_없으면_기본_정렬과_기본_size를_사용한다() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(articleController).build();
        UUID userId = UUID.randomUUID();

        when(articleService.getArticles(any(ArticleSearchCondition.class), eq(userId)))
                .thenReturn(CursorPageResponse.of(List.of(), null, null, 10, 0L, false));

        // when
        mockMvc.perform(get("/api/articles")
                        .header("MoNew-Request-User-ID", userId.toString()))
                .andExpect(status().isOk());

        // then
        // sortType과 size는 Controller의 @RequestParam defaultValue로 기본값이 적용됩니다.
        ArgumentCaptor<ArticleSearchCondition> conditionCaptor = ArgumentCaptor.forClass(ArticleSearchCondition.class);
        verify(articleService).getArticles(conditionCaptor.capture(), eq(userId));

        ArticleSearchCondition condition = conditionCaptor.getValue();

        assertThat(condition.keyword()).isNull();
        assertThat(condition.interestId()).isNull();
        assertThat(condition.sourceIn()).isNull();
        assertThat(condition.publishDateFrom()).isNull();
        assertThat(condition.publishDateTo()).isNull();
        assertThat(condition.orderBy()).isEqualTo(ArticleSortType.PUBLISH_DATE);
        assertThat(condition.direction()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        assertThat(condition.after()).isNull();
        assertThat(condition.cursor()).isNull();
        assertThat(condition.limit()).isEqualTo(10);
    }
    @Test
    void 사용자_ID_헤더가_없으면_400을_응답한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(articleController).build();

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 기사를_논리_삭제하면_204를_응답한다() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/articles/{articleId}", articleId)
                        .header("MoNew-Request-User-ID", userId))
                .andExpect(status().isNoContent());

        verify(articleService).softDelete(articleId);

    }

    @Test
    void 존재하지_않는_기사를_논리_삭제하면_404를_응답한다() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .setControllerAdvice(new GlobalHandlerException())
                .build();

        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();


        doThrow(new BusinessException(ErrorCode.ARTICLE_NOT_FOUND))
                .when(articleService)
                .softDelete(articleId);

        // when & then
        mockMvc.perform(delete("/api/articles/{articleId}", articleId)
                        .header("MoNew-Request-User-ID", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("기사를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.status").value(404));

        verify(articleService).softDelete(articleId);
    }

    @Test
    void 헤더없이_삭제하면_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        UUID articleId = UUID.randomUUID();

        mockMvc.perform(delete("/api/articles/{articleId}", articleId))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(articleService);
    }

    @Test
    void 기사를_물리_삭제하면_204를_응답한다() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        UUID articleId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/articles/{articleId}/hard", articleId))
                .andExpect(status().isNoContent());

        verify(articleService).hardDelete(articleId);
    }

    @Test
    void 존재하지_않는_기사를_물리_삭제하면_404를_응답한다() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .setControllerAdvice(new GlobalHandlerException())
                .build();

        UUID articleId = UUID.randomUUID();

        doThrow(new BusinessException(ErrorCode.ARTICLE_NOT_FOUND))
                .when(articleService)
                .hardDelete(articleId);

        // when & then
        mockMvc.perform(delete("/api/articles/{articleId}/hard", articleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("기사를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.status").value(404));

        verify(articleService).hardDelete(articleId);
    }

    @Test
    void 뉴스_기사를_복구하면_200과_복구_결과를_응답한다() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        Instant from = Instant.parse("2026-08-20T00:00:00Z");
        Instant to = Instant.parse("2026-08-21T23:59:59Z");
        UUID restoredArticleId = UUID.randomUUID();

        when(articleBackupService.restore(from, to))
                .thenReturn(List.of(new ArticleRestoreResultDto(
                        Instant.parse("2026-08-20T15:00:00Z"),
                        List.of(restoredArticleId),
                        1
                )));

        // when & then
        mockMvc.perform(get("/api/articles/restore")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].restoreDate").value("2026-08-20T15:00:00Z"))
                .andExpect(jsonPath("$[0].restoredArticleIds[0]").value(restoredArticleId.toString()))
                .andExpect(jsonPath("$[0].restoredArticleCount").value(1));

        verify(articleBackupService).restore(from, to);
    }

    @Test
    void 복구할_기사_백업이_없으면_404를_응답한다() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .setControllerAdvice(new GlobalHandlerException())
                .build();

        Instant from = Instant.parse("2026-08-20T00:00:00Z");
        Instant to = Instant.parse("2026-08-21T23:59:59Z");

        when(articleBackupService.restore(from, to))
                .thenThrow(new BusinessException(ErrorCode.ARTICLE_BACKUP_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/articles/restore")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_BACKUP_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("기사 백업을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.status").value(404));

        verify(articleBackupService).restore(from, to);
    }

    @Test
    void 기사_출처_목록을_조회하면_200을_응답한다() throws Exception {

        // given
        List<String> sources = List.of(
                "네이버",
                "한국경제",
                "조선일보",
                "연합뉴스TV"
        );

        when(articleService.getSources())
                .thenReturn(sources);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        // when & then
        mockMvc.perform(get("/api/articles/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("네이버"))
                .andExpect(jsonPath("$[1]").value("한국경제"))
                .andExpect(jsonPath("$[2]").value("조선일보"))
                .andExpect(jsonPath("$[3]").value("연합뉴스TV"));

        verify(articleService).getSources();
    }

    @Test
    void 기사_ID로_단건_조회하면_200을_응답한다() throws Exception {
        // given
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ArticleDto article = new ArticleDto(
                articleId,
                "NAVER",
                "https://example.com/article/1",
                "테스트 기사",
                Instant.parse("2026-08-24T00:00:00Z"),
                "테스트 요약",
                3L,
                10L,
                true
        );

        when(articleService.getArticle(articleId, userId))
                .thenReturn(article);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        // when & then
        mockMvc.perform(get("/api/articles/{articleId}", articleId)
                        .header("MoNew-Request-User-ID", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(articleId.toString()))
                .andExpect(jsonPath("$.source").value("NAVER"))
                .andExpect(jsonPath("$.sourceUrl")
                        .value("https://example.com/article/1"))
                .andExpect(jsonPath("$.title").value("테스트 기사"))
                .andExpect(jsonPath("$.summary").value("테스트 요약"))
                .andExpect(jsonPath("$.commentCount").value(3))
                .andExpect(jsonPath("$.viewCount").value(10))
                .andExpect(jsonPath("$.viewedByMe").value(true));

        verify(articleService).getArticle(articleId, userId);
    }

    @Test
    void 존재하지_않는_기사를_단건_조회하면_404를_응답한다()
            throws Exception {
        // given
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .setControllerAdvice(new GlobalHandlerException())
                .build();

        when(articleService.getArticle(articleId, userId))
                .thenThrow(
                        new BusinessException(ErrorCode.ARTICLE_NOT_FOUND)
                );

        // when & then
        mockMvc.perform(get("/api/articles/{articleId}", articleId)
                        .header("MoNew-Request-User-ID", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("ARTICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("기사를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.status").value(404));

        verify(articleService).getArticle(articleId, userId);
    }

    @Test
    void 요청자_헤더_없이_기사를_단건_조회하면_400을_응답한다()
            throws Exception {
        // given
        UUID articleId = UUID.randomUUID();

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        // when & then
        mockMvc.perform(get("/api/articles/{articleId}", articleId))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(articleService);
    }

    @Test
    void 기사_조회수를_등록하면_200을_응답한다() throws Exception {
        // given
        UUID articleViewId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-25T00:00:00Z");

        ArticleViewDto response = new ArticleViewDto(
                articleViewId,
                userId,
                createdAt,
                articleId,
                "NAVER",
                "https://example.com/article/1",
                "테스트 기사",
                Instant.parse("2026-08-24T00:00:00Z"),
                "테스트 요약",
                3L,
                11L
        );

        when(articleService.registerView(articleId, userId))
                .thenReturn(response);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        // when & then
        mockMvc.perform(post(
                        "/api/articles/{articleId}/article-views",
                        articleId
                ).header("MoNew-Request-User-ID", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(articleViewId.toString()))
                .andExpect(jsonPath("$.viewedBy")
                        .value(userId.toString()))
                .andExpect(jsonPath("$.articleId")
                        .value(articleId.toString()))
                .andExpect(jsonPath("$.source").value("NAVER"))
                .andExpect(jsonPath("$.articleTitle")
                        .value("테스트 기사"))
                .andExpect(jsonPath("$.articleViewCount").value(11));

        verify(articleService).registerView(articleId, userId);
    }

    @Test
    void 사용자_ID_헤더없이_조회수를_등록하면_400을_응답한다()
            throws Exception {
        // given
        UUID articleId = UUID.randomUUID();

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(articleController)
                .build();

        // when & then
        mockMvc.perform(post(
                        "/api/articles/{articleId}/article-views",
                        articleId
                ))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(articleService);
    }
}
