package com.project.monu.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.project.monu.domain.article.entity.Article;
import com.project.monu.domain.article.repository.ArticleRepository;
import com.project.monu.domain.comment.dto.CommentDto;
import com.project.monu.domain.comment.dto.CommentLikeDto;
import com.project.monu.domain.comment.dto.request.CommentCreateRequest;
import com.project.monu.domain.comment.dto.request.CommentSearchCondition;
import com.project.monu.domain.comment.dto.request.CommentSortType;
import com.project.monu.domain.comment.dto.request.CommentUpdateRequest;
import com.project.monu.domain.comment.entity.Comment;
import com.project.monu.domain.comment.entity.CommentLike;
import com.project.monu.domain.comment.repository.CommentLikeRepository;
import com.project.monu.domain.comment.repository.CommentQueryResult;
import com.project.monu.domain.comment.repository.CommentRepository;
import com.project.monu.domain.notification.entity.NotificationResourceType;
import com.project.monu.domain.notification.repository.NotificationRepository;
import com.project.monu.domain.notification.event.CommentLikedEvent;
import com.project.monu.domain.users.entity.User;
import com.project.monu.domain.users.repository.UserRepository;
import com.project.monu.global.dto.CursorPageResponse;
import com.project.monu.global.exception.BusinessException;
import com.project.monu.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BasicCommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentLikeRepository commentLikeRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BasicCommentService commentService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void 댓글을_등록한다() {
        // given
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-24T00:00:00Z");

        CommentCreateRequest request = new CommentCreateRequest(articleId, userId, "댓글 등록 테스트입니다.");

        Article article = mock(Article.class);
        User user = mock(User.class);
        Comment savedComment = mock(Comment.class);

        when(articleRepository.findByIdAndDeletedAtIsNull(articleId)).thenReturn(Optional.of(article));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(article.getId()).thenReturn(articleId);
        when(user.getId()).thenReturn(userId);
        when(user.getNickname()).thenReturn("댓글테스터");
        when(savedComment.getId()).thenReturn(commentId);
        when(savedComment.getContent()).thenReturn("댓글 등록 테스트입니다.");
        when(savedComment.getCreatedAt()).thenReturn(createdAt);

        // when
        CommentDto result = commentService.create(request);

        // then
        assertThat(result.id()).isEqualTo(commentId);
        assertThat(result.articleId()).isEqualTo(articleId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.userNickname()).isEqualTo("댓글테스터");
        assertThat(result.content()).isEqualTo("댓글 등록 테스트입니다.");
        assertThat(result.likeCount()).isZero();
        assertThat(result.likedByMe()).isFalse();
        assertThat(result.createdAt()).isEqualTo(createdAt);

        verify(articleRepository).findByIdAndDeletedAtIsNull(articleId);
        verify(userRepository).findById(userId);
        verify(commentRepository).save(any(Comment.class));
        verify(article).increaseCommentCount();
    }

    @Test
    void 기사가_없으면_댓글_등록에_실패한다() {
        // given
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CommentCreateRequest request = new CommentCreateRequest(articleId, userId, "댓글 등록 테스트입니다.");

        when(articleRepository.findByIdAndDeletedAtIsNull(articleId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.create(request));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ARTICLE_NOT_FOUND);
    }

    @Test
    void 사용자가_없으면_댓글_등록에_실패한다() {
        // given
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CommentCreateRequest request = new CommentCreateRequest(articleId, userId, "댓글 등록 테스트입니다.");

        Article article = mock(Article.class);

        when(articleRepository.findByIdAndDeletedAtIsNull(articleId)).thenReturn(Optional.of(article));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.create(request));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 댓글을_수정한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-24T00:00:00Z");

        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글입니다.");

        Comment comment = mock(Comment.class);
        Article article = mock(Article.class);
        User user = mock(User.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getUser()).thenReturn(user);
        when(comment.getArticle()).thenReturn(article);
        when(comment.getId()).thenReturn(commentId);
        when(comment.getContent()).thenReturn("수정된 댓글입니다.");
        when(comment.getCreatedAt()).thenReturn(createdAt);
        when(user.getId()).thenReturn(userId);
        when(user.getNickname()).thenReturn("댓글테스터");
        when(article.getId()).thenReturn(articleId);

        // when
        CommentDto result = commentService.update(commentId, userId, request);

        // then
        assertThat(result.id()).isEqualTo(commentId);
        assertThat(result.articleId()).isEqualTo(articleId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.content()).isEqualTo("수정된 댓글입니다.");

        verify(comment).updateContent("수정된 댓글입니다.");
    }

    @Test
    void 댓글이_없으면_수정에_실패한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글입니다.");

        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.update(commentId, userId, request));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void 작성자가_아니면_댓글_수정에_실패한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID writerId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글입니다.");

        Comment comment = mock(Comment.class);
        User user = mock(User.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(writerId);

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.update(commentId, requestUserId, request));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_ACCESS_DENIED);
        verify(comment, never()).updateContent(anyString());
    }

    @Test
    void 댓글을_논리_삭제한다() {
        // given
        UUID commentId = UUID.randomUUID();

        Comment comment = mock(Comment.class);
        Article article = mock(Article.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getArticle()).thenReturn(article);

        // when
        commentService.delete(commentId);

        // then
        verify(comment).delete();
        verify(article).decreaseCommentCount();
    }

    @Test
    void 댓글이_없으면_삭제에_실패한다() {
        // given
        UUID commentId = UUID.randomUUID();

        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.delete(commentId));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void 삭제된_댓글은_수정할_수_없다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글입니다.");

        Comment comment = mock(Comment.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(Instant.now());

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.update(commentId, userId, request));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void 기사별_댓글_목록을_조회한다() {
        // given
        UUID articleId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-24T00:00:00Z");

        CommentSearchCondition condition = new CommentSearchCondition(
                articleId,
                CommentSortType.CREATED_AT,
                Sort.Direction.DESC,
                null,
                null,
                10,
                requestUserId
        );

        CommentQueryResult queryResult = new CommentQueryResult(
                commentId, articleId, userId, "댓글테스터",
                "댓글 내용입니다.", 0L, false, createdAt
        );

        when(commentRepository.searchByCursor(condition)).thenReturn(List.of(queryResult));
        when(commentRepository.countByCondition(condition)).thenReturn(1L);

        // when
        CursorPageResponse<CommentDto> result =
                commentService.getComments(articleId, "createdAt", "DESC", null, null, 10, requestUserId);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(commentId);
        assertThat(result.content().get(0).articleId()).isEqualTo(articleId);
        assertThat(result.content().get(0).userId()).isEqualTo(userId);
        assertThat(result.content().get(0).userNickname()).isEqualTo("댓글테스터");
        assertThat(result.content().get(0).content()).isEqualTo("댓글 내용입니다.");
        assertThat(result.content().get(0).likeCount()).isZero();
        assertThat(result.content().get(0).likedByMe()).isFalse();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.hasNext()).isFalse();

        verify(commentRepository).searchByCursor(condition);
        verify(commentRepository).countByCondition(condition);
    }

    @Test
    void 다음_페이지가_있으면_커서와_after를_반환한다() {
        // given
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        Instant firstCreatedAt = Instant.parse("2026-08-24T03:00:00Z");
        Instant secondCreatedAt = Instant.parse("2026-08-24T02:00:00Z");
        Instant thirdCreatedAt = Instant.parse("2026-08-24T01:00:00Z");

        CommentSearchCondition condition = new CommentSearchCondition(
                articleId,
                CommentSortType.CREATED_AT,
                Sort.Direction.DESC,
                null,
                null,
                2,
                requestUserId
        );

        when(commentRepository.searchByCursor(condition)).thenReturn(List.of(
                new CommentQueryResult(firstId, articleId, userId, "댓글테스터",
                        "첫 번째 댓글", 1L, false, firstCreatedAt),
                new CommentQueryResult(secondId, articleId, userId, "댓글테스터",
                        "두 번째 댓글", 2L, true, secondCreatedAt),
                new CommentQueryResult(thirdId, articleId, userId, "댓글테스터",
                        "세 번째 댓글", 0L, false, thirdCreatedAt)
        ));
        when(commentRepository.countByCondition(condition)).thenReturn(3L);

        // when
        CursorPageResponse<CommentDto> result =
                commentService.getComments(articleId, "createdAt", "DESC", null, null, 2, requestUserId);

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(secondCreatedAt + "_" + secondId);
        assertThat(result.nextAfter()).isEqualTo(secondCreatedAt);
        assertThat(result.totalElements()).isEqualTo(3L);
    }

    @Test
    void 댓글_조회시_좋아요_정보를_반환한다() {
        // given
        UUID articleId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-24T00:00:00Z");

        CommentSearchCondition condition = new CommentSearchCondition(
                articleId,
                CommentSortType.LIKE_COUNT,
                Sort.Direction.DESC,
                null,
                null,
                10,
                requestUserId
        );

        when(commentRepository.searchByCursor(condition))
                .thenReturn(List.of(new CommentQueryResult(
                        commentId, articleId, userId, "댓글테스터",
                        "좋아요가 있는 댓글", 3L, true, createdAt
                )));
        when(commentRepository.countByCondition(condition)).thenReturn(1L);

        // when
        CursorPageResponse<CommentDto> result =
                commentService.getComments(articleId, "likeCount", "DESC", null, null, 10, requestUserId);

        // then
        assertThat(result.content().get(0).likeCount()).isEqualTo(3L);
        assertThat(result.content().get(0).likedByMe()).isTrue();
        verify(commentRepository).searchByCursor(condition);
    }

    @Test
    void 지원하지_않는_정렬_기준이면_댓글_조회에_실패한다() {
        // given
        UUID requestUserId = UUID.randomUUID();

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.getComments(null, "wrong", "DESC", null, null, 10, requestUserId));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_INVALID_SORT_TYPE);
    }

    @Test
    void 지원하지_않는_정렬_방향이면_댓글_조회에_실패한다() {
        // given
        UUID requestUserId = UUID.randomUUID();

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.getComments(null, "createdAt", "WRONG", null, null, 10, requestUserId));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_INVALID_SORT_DIRECTION);
    }

    @Test
    void limit이_1보다_작으면_댓글_조회에_실패한다() {
        // given
        UUID requestUserId = UUID.randomUUID();

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.getComments(null, "createdAt", "DESC", null, null, 0, requestUserId));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_INVALID_LIMIT);
    }

    @Test
    void 댓글에_좋아요를_등록한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        UUID likeId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID commentUserId = UUID.randomUUID();

        Instant likeCreatedAt = Instant.parse("2026-08-27T06:00:00Z");
        Instant commentCreatedAt = Instant.parse("2026-08-26T06:00:00Z");

        Comment comment = mock(Comment.class);
        User commentUser = mock(User.class);
        User requestUser = mock(User.class);
        Article article = mock(Article.class);
        CommentLike savedLike = mock(CommentLike.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(null);
        when(userRepository.findById(requestUserId)).thenReturn(Optional.of(requestUser));

        when(comment.getId()).thenReturn(commentId);
        when(comment.getArticle()).thenReturn(article);
        when(comment.getUser()).thenReturn(commentUser);
        when(comment.getContent()).thenReturn("댓글 내용");
        when(comment.getCreatedAt()).thenReturn(commentCreatedAt);

        when(article.getId()).thenReturn(articleId);
        when(commentUser.getId()).thenReturn(commentUserId);
        when(commentUser.getNickname()).thenReturn("작성자");

        when(savedLike.getId()).thenReturn(likeId);
        when(savedLike.getCreatedAt()).thenReturn(likeCreatedAt);

        when(commentLikeRepository.saveAndFlush(any(CommentLike.class))).thenReturn(savedLike);
        when(commentLikeRepository.countByComment_Id(commentId)).thenReturn(1L);

        // when
        CommentLikeDto result = commentService.like(commentId, requestUserId);

        // then
        assertThat(result.id()).isEqualTo(likeId);
        assertThat(result.likedBy()).isEqualTo(requestUserId);
        assertThat(result.createdAt()).isEqualTo(likeCreatedAt);
        assertThat(result.commentId()).isEqualTo(commentId);
        assertThat(result.articleId()).isEqualTo(articleId);
        assertThat(result.commentUserId()).isEqualTo(commentUserId);
        assertThat(result.commentUserNickname()).isEqualTo("작성자");
        assertThat(result.commentContent()).isEqualTo("댓글 내용");
        assertThat(result.commentLikeCount()).isEqualTo(1L);
        assertThat(result.commentCreatedAt()).isEqualTo(commentCreatedAt);

        verify(commentLikeRepository).saveAndFlush(any(CommentLike.class));
    }

    @Test
    void 댓글이_없으면_좋아요_등록에_실패한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();

        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> commentService.like(commentId, requestUserId)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(commentLikeRepository);
    }

    @Test
    void 사용자가_없으면_좋아요_등록에_실패한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();

        Comment comment = mock(Comment.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(null);
        when(userRepository.findById(requestUserId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.like(commentId, requestUserId));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(commentLikeRepository);
    }

    @Test
    void 이미_좋아요한_댓글이면_좋아요_등록에_실패한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();

        Comment comment = mock(Comment.class);
        User requestUser = mock(User.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(null);
        when(userRepository.findById(requestUserId)).thenReturn(Optional.of(requestUser));
        when(commentLikeRepository.existsByComment_IdAndLikedBy_Id(commentId, requestUserId))
                .thenReturn(true);

        // when
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> commentService.like(commentId, requestUserId));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_LIKE_ALREADY_EXISTS);
        verify(commentLikeRepository, never()).saveAndFlush(any(CommentLike.class));
    }

    @Test
    void 댓글_좋아요를_취소한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();

        Comment comment = mock(Comment.class);
        CommentLike commentLike = mock(CommentLike.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(null);
        when(commentLikeRepository.findByComment_IdAndLikedBy_Id(commentId, requestUserId))
                .thenReturn(Optional.of(commentLike));

        // when
        commentService.unlike(commentId, requestUserId);

        // then
        verify(commentLikeRepository).delete(commentLike);
    }

    @Test
    void 좋아요하지_않은_댓글이면_좋아요_취소에_실패한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();

        Comment comment = mock(Comment.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(null);
        when(commentLikeRepository.findByComment_IdAndLikedBy_Id(commentId, requestUserId))
                .thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(
                BusinessException.class, () -> commentService.unlike(commentId, requestUserId)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_LIKE_NOT_FOUND);
    }

    @Test
    void 댓글을_물리_삭제한다() {
        // given
        UUID commentId = UUID.randomUUID();

        Comment comment = mock(Comment.class);
        Article article = mock(Article.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(null);
        when(comment.getArticle()).thenReturn(article);

        // when
        commentService.hardDelete(commentId);

        // then
        verify(commentLikeRepository).deleteAllByComment_Id(commentId);
        verify(commentRepository).delete(comment);
        verify(article).decreaseCommentCount();
        verify(notificationRepository).deleteAllByResourceTypeAndResourceIdIn(
                NotificationResourceType.COMMENT, List.of(commentId));
    }

    @Test
    void 이미_논리_삭제된_댓글을_물리_삭제할때_댓글수는_감소하지_않는다() {
        // given
        UUID commentId = UUID.randomUUID();

        Comment comment = mock(Comment.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(Instant.parse("2026-08-27T00:00:00Z"));

        // when
        commentService.hardDelete(commentId);

        // then
        verify(commentLikeRepository).deleteAllByComment_Id(commentId);
        verify(commentRepository).delete(comment);
        verify(comment, never()).getArticle();
    }

    @Test
    void 존재하지_않는_댓글은_물리_삭제할수_없다() {
        // given
        UUID commentId = UUID.randomUUID();

        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(
                BusinessException.class, () -> commentService.hardDelete(commentId)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        verifyNoInteractions(commentLikeRepository, notificationRepository);
    }

    @Test
    void 사용자_물리_삭제시_작성한_댓글과_관련_좋아요를_정리한다() {
        // given
        UUID userId = UUID.randomUUID();

        Comment activeComment = mock(Comment.class);
        Comment deletedComment = mock(Comment.class);
        Article article = mock(Article.class);

        when(commentRepository.findAllByUser_Id(userId))
                .thenReturn(List.of(activeComment, deletedComment));

        when(activeComment.getDeletedAt()).thenReturn(null);
        when(activeComment.getArticle()).thenReturn(article);
        when(deletedComment.getDeletedAt()).thenReturn(Instant.parse("2026-09-01T00:00:00Z"));

        // when
        commentService.hardDeleteAllByUserId(userId);

        // then
        verify(article).decreaseCommentCount();
        verify(commentLikeRepository).deleteAllByComment_User_Id(userId);
        verify(commentLikeRepository).deleteAllByLikedBy_Id(userId);
        verify(commentRepository).deleteAll(List.of(activeComment, deletedComment));
        verify(deletedComment, never()).getArticle();
    }

    @Test
    void 댓글_좋아요_등록시_CommentLikedEvent를_발행한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID commentAuthorId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();

        Comment comment = mock(Comment.class);
        User commentAuthor = mock(User.class);
        User requestUser = mock(User.class);
        Article article = mock(Article.class);
        CommentLike savedLike = mock(CommentLike.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(null);
        when(userRepository.findById(requestUserId)).thenReturn(Optional.of(requestUser));
        when(commentLikeRepository.existsByComment_IdAndLikedBy_Id(commentId, requestUserId))
                .thenReturn(false);

        when(comment.getId()).thenReturn(commentId);
        when(comment.getUser()).thenReturn(commentAuthor);
        when(comment.getArticle()).thenReturn(article);
        when(commentAuthor.getId()).thenReturn(commentAuthorId);
        when(requestUser.getNickname()).thenReturn("좋아요누른사람");
        when(article.getId()).thenReturn(articleId);

        when(commentLikeRepository.saveAndFlush(any(CommentLike.class))).thenReturn(savedLike);

        // when
        commentService.like(commentId, requestUserId);

        // then
        verify(eventPublisher).publishEvent(new CommentLikedEvent(
                commentAuthorId,
                requestUserId,
                "좋아요누른사람",
                commentId
        ));
    }

    @Test
    void 좋아요_저장_중_제약_위반이_발생하면_중복_예외로_변환한다() {
        // given
        UUID commentId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();

        Comment comment = mock(Comment.class);
        User requestUser = mock(User.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(comment.getDeletedAt()).thenReturn(null);
        when(userRepository.findById(requestUserId)).thenReturn(Optional.of(requestUser));
        when(commentLikeRepository.existsByComment_IdAndLikedBy_Id(commentId, requestUserId))
                .thenReturn(false);
        when(commentLikeRepository.saveAndFlush(any(CommentLike.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when
        BusinessException exception = catchThrowableOfType(
                BusinessException.class, () -> commentService.like(commentId, requestUserId)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_LIKE_ALREADY_EXISTS);
    }

}
