package com.project.monu.domain.comment.repository;

import com.project.monu.domain.article.entity.QArticle;
import com.project.monu.domain.comment.dto.request.CommentSearchCondition;
import com.project.monu.domain.comment.entity.QComment;
import com.project.monu.domain.comment.entity.QCommentLike;
import com.project.monu.domain.comment.exception.InvalidCommentCursorException;
import com.project.monu.domain.users.entity.QUser;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

    private static final QComment comment = QComment.comment;
    private static final QArticle article = QArticle.article;
    private static final QUser user = QUser.user;
    private static final QCommentLike myLike = new QCommentLike("myLike");
    private static final QCommentLike likeCounter = new QCommentLike("likeCounter");

    // 좋아요 수를 서브쿼리로 계산 → groupBy/having 불필요
    private static final NumberExpression<Long> LIKE_COUNT = Expressions.numberTemplate(
            Long.class,
            "{0}",
            JPAExpressions.select(likeCounter.id.count())
                    .from(likeCounter)
                    .where(likeCounter.comment.eq(comment))
    );

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CommentQueryResult> searchByCursor(CommentSearchCondition condition) {
        List<Tuple> rows = queryFactory
                .select(
                        comment.id,
                        article.id,
                        user.id,
                        user.nickname,
                        comment.content,
                        LIKE_COUNT,
                        myLike.id,
                        comment.createdAt
                )
                .from(comment)
                .join(comment.article, article)
                .join(comment.user, user)
                .leftJoin(myLike).on(
                        myLike.comment.eq(comment)
                                .and(myLike.likedBy.id.eq(condition.requestUserId()))
                )
                .where(
                        comment.deletedAt.isNull(),
                        articleEq(condition.articleId()),
                        cursorCondition(condition)
                )
                .orderBy(orderBy(condition))
                .limit(condition.limit() + 1L)
                .fetch();

        return rows.stream()
                .map(row -> {
                    Long count = row.get(LIKE_COUNT);

                    return new CommentQueryResult(
                            row.get(comment.id),
                            row.get(article.id),
                            row.get(user.id),
                            row.get(user.nickname),
                            row.get(comment.content),
                            count == null ? 0L : count,
                            row.get(myLike.id) != null,
                            row.get(comment.createdAt)
                    );
                })
                .toList();
    }

    @Override
    public long countByCondition(CommentSearchCondition condition) {
        Long count = queryFactory
                .select(comment.count())
                .from(comment)
                .where(
                        comment.deletedAt.isNull(),
                        articleEq(condition.articleId())
                )
                .fetchOne();

        return count == null ? 0L : count;
    }

    private BooleanExpression articleEq(UUID articleId) {
        return articleId == null ? null : comment.article.id.eq(articleId);
    }

    private OrderSpecifier<?>[] orderBy(CommentSearchCondition condition) {
        boolean desc = condition.direction().isDescending();

        OrderSpecifier<?> createdAt = desc ? comment.createdAt.desc() : comment.createdAt.asc();
        OrderSpecifier<?> id = desc ? comment.id.desc() : comment.id.asc();

        return switch (condition.sortType()) {
            case CREATED_AT -> new OrderSpecifier[]{createdAt, id};
            case LIKE_COUNT -> new OrderSpecifier[]{
                    desc ? LIKE_COUNT.desc() : LIKE_COUNT.asc(), createdAt, id
            };
        };
    }

    private BooleanExpression cursorCondition(CommentSearchCondition condition) {
        if (condition.cursor() == null || condition.cursor().isBlank()) {
            return null;
        }

        Cursor cursor = parseCursor(condition.cursor());
        boolean desc = condition.direction().isDescending();

        return switch (condition.sortType()) {
            case CREATED_AT -> createdAtCursor(cursor, desc);
            case LIKE_COUNT -> likeCountCursor(cursor, desc, condition.after());
        };
    }

    private BooleanExpression createdAtCursor(Cursor cursor, boolean desc) {
        Instant createdAt;
        try {
            createdAt = Instant.parse(cursor.value());
        } catch (Exception e) {
            throw new InvalidCommentCursorException();
        }

        BooleanExpression tieBreak = idTieBreak(cursor.id(), desc);

        return desc
                ? comment.createdAt.lt(createdAt)
                  .or(comment.createdAt.eq(createdAt).and(tieBreak))
                : comment.createdAt.gt(createdAt)
                  .or(comment.createdAt.eq(createdAt).and(tieBreak));
    }

    private BooleanExpression likeCountCursor(Cursor cursor, boolean desc, Instant after) {
        long likeCount;
        try {
            likeCount = Long.parseLong(cursor.value());
        } catch (NumberFormatException e) {
            throw new InvalidCommentCursorException();
        }

        BooleanExpression idTieBreak = idTieBreak(cursor.id(), desc);

        BooleanExpression tieBreak = after == null
                ? idTieBreak
                : desc
                  ? comment.createdAt.lt(after)
                    .or(comment.createdAt.eq(after).and(idTieBreak))
                  : comment.createdAt.gt(after)
                    .or(comment.createdAt.eq(after).and(idTieBreak));

        return desc
                ? LIKE_COUNT.lt(likeCount).or(LIKE_COUNT.eq(likeCount).and(tieBreak))
                : LIKE_COUNT.gt(likeCount).or(LIKE_COUNT.eq(likeCount).and(tieBreak));
    }

    private BooleanExpression idTieBreak(UUID cursorId, boolean desc) {
        return desc ? comment.id.lt(cursorId) : comment.id.gt(cursorId);
    }

    private Cursor parseCursor(String cursor) {
        int lastUnderscoreIndex = cursor.lastIndexOf('_');

        if (lastUnderscoreIndex < 0) {
            throw new InvalidCommentCursorException();
        }

        try {
            return new Cursor(
                    cursor.substring(0, lastUnderscoreIndex),
                    UUID.fromString(cursor.substring(lastUnderscoreIndex + 1))
            );
        } catch (IllegalArgumentException e) {
            throw new InvalidCommentCursorException();
        }
    }

    private record Cursor(String value, UUID id) {
    }
}