package com.project.monu.domain.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.monu.domain.article.entity.Article;
import com.project.monu.domain.article.entity.ArticleSource;
import com.project.monu.domain.article.entity.SourceType;
import com.project.monu.domain.comment.dto.request.CommentSearchCondition;
import com.project.monu.domain.comment.dto.request.CommentSortType;
import com.project.monu.domain.comment.entity.Comment;
import com.project.monu.domain.comment.entity.CommentLike;
import com.project.monu.domain.users.entity.User;
import com.project.monu.global.config.JpaAuditingConfig;
import com.project.monu.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class CommentRepositoryCustomImplTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 댓글을_생성일_내림차순으로_조회한다() {
        // given
        User author = user("author@test.com", "작성자");
        User requestUser = user("request@test.com", "조회자");
        ArticleSource source = source("NAVER");
        Article article = article(source, "article");

        Comment first = comment(article, author, "첫 번째 댓글");
        Comment second = comment(article, author, "두 번째 댓글");
        Comment third = comment(article, author, "세 번째 댓글");

        em.flush();

        setCreatedAt(first, Instant.parse("2026-08-24T03:00:00Z"));
        setCreatedAt(second, Instant.parse("2026-08-24T02:00:00Z"));
        setCreatedAt(third, Instant.parse("2026-08-24T01:00:00Z"));

        List<UUID> expectedIds = List.of(
                first.getId(),
                second.getId(),
                third.getId()
        );

        CommentSearchCondition condition = new CommentSearchCondition(
                article.getId(),
                CommentSortType.CREATED_AT,
                Sort.Direction.DESC,
                null,
                null,
                10,
                requestUser.getId()
        );

        flushAndClear();

        // when
        List<CommentQueryResult> result = commentRepository.searchByCursor(condition);

        // then
        assertThat(result)
                .extracting(CommentQueryResult::id)
                .containsExactlyElementsOf(expectedIds);
    }

    @Test
    void 다른_기사와_삭제된_댓글은_조회하지_않는다() {
        // given
        User author = user("author@test.com", "작성자");
        User requestUser = user("request@test.com", "조회자");
        ArticleSource source = source("NAVER");
        Article article = article(source, "article");
        Article otherArticle = article(source, "other-article");

        Comment activeComment = comment(article, author, "조회 대상 댓글");
        comment(otherArticle, author, "다른 기사 댓글");

        Comment deletedComment = comment(article, author, "삭제된 댓글");
        deletedComment.delete();

        CommentSearchCondition condition = new CommentSearchCondition(
                article.getId(),
                CommentSortType.CREATED_AT,
                Sort.Direction.DESC,
                null,
                null,
                10,
                requestUser.getId()
        );

        UUID activeCommentId = activeComment.getId();
        flushAndClear();

        // when
        List<CommentQueryResult> result = commentRepository.searchByCursor(condition);
        long totalElements = commentRepository.countByCondition(condition);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(activeCommentId);
        assertThat(totalElements).isEqualTo(1L);
    }

    @Test
    void 댓글을_좋아요수_내림차순으로_조회하고_내_좋아요여부를_반환한다() {
        // given
        User author = user("author@test.com", "작성자");
        User requestUser = user("request@test.com", "조회자");
        User liker1 = user("liker1@test.com", "좋아요1");
        User liker2 = user("liker2@test.com", "좋아요2");

        ArticleSource source = source("NAVER");
        Article article = article(source, "article");

        Comment mostLiked = comment(article, author, "좋아요 세 개");
        Comment likedOnce = comment(article, author, "좋아요 한 개");
        Comment noLike = comment(article, author, "좋아요 없음");

        commentLike(mostLiked, requestUser);
        commentLike(mostLiked, liker1);
        commentLike(mostLiked, liker2);
        commentLike(likedOnce, liker1);

        UUID mostLikedId = mostLiked.getId();
        UUID likedOnceId = likedOnce.getId();
        UUID noLikeId = noLike.getId();

        CommentSearchCondition condition = new CommentSearchCondition(
                article.getId(),
                CommentSortType.LIKE_COUNT,
                Sort.Direction.DESC,
                null,
                null,
                10,
                requestUser.getId()
        );

        flushAndClear();

        // when
        List<CommentQueryResult> result = commentRepository.searchByCursor(condition);

        // then
        assertThat(result).hasSize(3);

        assertThat(result.get(0).id()).isEqualTo(mostLikedId);
        assertThat(result.get(0).likeCount()).isEqualTo(3L);
        assertThat(result.get(0).likedByMe()).isTrue();

        assertThat(result.get(1).id()).isEqualTo(likedOnceId);
        assertThat(result.get(1).likeCount()).isEqualTo(1L);
        assertThat(result.get(1).likedByMe()).isFalse();

        assertThat(result.get(2).id()).isEqualTo(noLikeId);
        assertThat(result.get(2).likeCount()).isZero();
        assertThat(result.get(2).likedByMe()).isFalse();
    }

    @Test
    void limit보다_한_개_더_조회한다() {
        // given
        User author = user("author@test.com", "작성자");
        User requestUser = user("request@test.com", "조회자");
        ArticleSource source = source("NAVER");
        Article article = article(source, "article");

        comment(article, author, "댓글 1");
        comment(article, author, "댓글 2");
        comment(article, author, "댓글 3");
        comment(article, author, "댓글 4");

        CommentSearchCondition condition = new CommentSearchCondition(
                article.getId(),
                CommentSortType.CREATED_AT,
                Sort.Direction.DESC,
                null,
                null,
                2,
                requestUser.getId()
        );

        flushAndClear();

        // when
        List<CommentQueryResult> result = commentRepository.searchByCursor(condition);

        // then
        assertThat(result).hasSize(3);
    }

    @Test
    void 생성일_커서_다음_댓글을_조회한다() {
        // given
        User author = user("author@test.com", "작성자");
        User requestUser = user("request@test.com", "조회자");
        ArticleSource source = source("NAVER");
        Article article = article(source, "article");

        Comment first = comment(article, author, "댓글 1");
        Comment second = comment(article, author, "댓글 2");
        Comment third = comment(article, author, "댓글 3");

        Instant firstCreatedAt = Instant.parse("2026-08-24T03:00:00Z");
        Instant secondCreatedAt = Instant.parse("2026-08-24T02:00:00Z");
        Instant thirdCreatedAt = Instant.parse("2026-08-24T01:00:00Z");

        em.flush();

        setCreatedAt(first, firstCreatedAt);
        setCreatedAt(second, secondCreatedAt);
        setCreatedAt(third, thirdCreatedAt);

        String cursor = firstCreatedAt + "_" + first.getId();
        List<UUID> expectedIds = List.of(second.getId(), third.getId());

        CommentSearchCondition condition = new CommentSearchCondition(
                article.getId(),
                CommentSortType.CREATED_AT,
                Sort.Direction.DESC,
                cursor,
                null,
                10,
                requestUser.getId()
        );

        flushAndClear();

        // when
        List<CommentQueryResult> result = commentRepository.searchByCursor(condition);

        // then
        assertThat(result)
                .extracting(CommentQueryResult::id)
                .containsExactlyElementsOf(expectedIds);
    }

    @Test
    void 좋아요수_커서_다음_댓글을_조회한다() {
        // given
        User author = user("author@test.com", "작성자");
        User requestUser = user("request@test.com", "조회자");
        User liker1 = user("liker1@test.com", "좋아요1");
        User liker2 = user("liker2@test.com", "좋아요2");

        ArticleSource source = source("NAVER");
        Article article = article(source, "article");

        Comment threeLikes = comment(article, author, "좋아요 세 개");
        Comment twoLikes = comment(article, author, "좋아요 두 개");
        Comment oneLike = comment(article, author, "좋아요 한 개");

        commentLike(threeLikes, requestUser);
        commentLike(threeLikes, liker1);
        commentLike(threeLikes, liker2);

        commentLike(twoLikes, liker1);
        commentLike(twoLikes, liker2);

        commentLike(oneLike, liker1);

        Instant threeLikesCreatedAt = Instant.parse("2026-08-24T03:00:00Z");
        Instant twoLikesCreatedAt = Instant.parse("2026-08-24T02:00:00Z");
        Instant oneLikeCreatedAt = Instant.parse("2026-08-24T01:00:00Z");

        em.flush();

        setCreatedAt(threeLikes, threeLikesCreatedAt);
        setCreatedAt(twoLikes, twoLikesCreatedAt);
        setCreatedAt(oneLike, oneLikeCreatedAt);

        String cursor = "3_" + threeLikes.getId();

        CommentSearchCondition condition = new CommentSearchCondition(
                article.getId(),
                CommentSortType.LIKE_COUNT,
                Sort.Direction.DESC,
                cursor,
                threeLikesCreatedAt,
                10,
                requestUser.getId()
        );

        UUID twoLikesId = twoLikes.getId();
        UUID oneLikeId = oneLike.getId();

        flushAndClear();

        // when
        List<CommentQueryResult> result = commentRepository.searchByCursor(condition);

        // then
        assertThat(result)
                .extracting(CommentQueryResult::id)
                .containsExactly(twoLikesId, oneLikeId);
    }

    private User user(String email, String nickname) {
        User user = User.builder()
                .email(email)
                .nickname(nickname)
                .password("encoded-password")
                .build();
        em.persist(user);
        return user;
    }

    private ArticleSource source(String name) {
        ArticleSource source = ArticleSource.builder()
                .name(name)
                .type(SourceType.RSS)
                .sourceUrl("https://example.com/" + name)
                .build();
        em.persist(source);
        return source;
    }

    private Article article(ArticleSource source, String title) {
        Article article = Article.builder()
                .source(source)
                .sourceUrl("https://example.com/articles/" + title)
                .title(title)
                .publishDate(Instant.parse("2026-08-18T00:00:00Z"))
                .summary("summary")
                .build();
        em.persist(article);
        return article;
    }

    private Comment comment(Article article, User user, String content) {
        Comment comment = new Comment(article, user, content);
        em.persist(comment);
        return comment;
    }

    private CommentLike commentLike(Comment comment, User likedBy) {
        CommentLike commentLike = new CommentLike(comment, likedBy);
        em.persist(commentLike);
        return commentLike;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private void setCreatedAt(Comment comment, Instant createdAt) {
        em.createQuery("""
            UPDATE Comment c
            SET c.createdAt = ?1
            WHERE c.id = ?2
            """)
                .setParameter(1, createdAt)
                .setParameter(2, comment.getId())
                .executeUpdate();
    }
}