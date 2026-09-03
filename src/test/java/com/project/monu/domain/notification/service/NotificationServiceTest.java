package com.project.monu.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.project.monu.global.dto.CursorPageResponse;
import com.project.monu.domain.notification.dto.NotificationConfirmAllResponse;
import com.project.monu.domain.notification.dto.NotificationResponse;
import com.project.monu.domain.notification.entity.Notification;
import com.project.monu.domain.notification.entity.NotificationResourceType;
import com.project.monu.domain.notification.repository.NotificationRepository;
import com.project.monu.global.exception.BusinessException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository =
            org.mockito.Mockito.mock(NotificationRepository.class);

    private final NotificationService notificationService =
            new NotificationService(notificationRepository);

    @Test
    void 본인_알림을_단건_확인할_수_있다() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = createNotification(userId);

        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification));

        NotificationResponse response =
                notificationService.confirmNotification(notificationId, userId);

        assertThat(response.confirmed()).isTrue();
        assertThat(notification.isConfirmed()).isTrue();
        assertThat(notification.getUpdatedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_알림을_확인하면_예외가_발생한다() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                notificationService.confirmNotification(notificationId, userId)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    void 다른_사용자의_알림은_확인할_수_없다() {
        UUID requestUserId = UUID.randomUUID();
        UUID notificationOwnerId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = createNotification(notificationOwnerId);

        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification));

        assertThatThrownBy(() ->
                notificationService.confirmNotification(notificationId, requestUserId)
        ).isInstanceOf(BusinessException.class);
    }

    private Notification createNotification(UUID userId) {
        return Notification.create(
                userId,
                "새로운 알림입니다.",
                NotificationResourceType.COMMENT,
                UUID.randomUUID()
        );
    }

    @Test
    void 사용자의_미확인_알림을_전체_확인할_수_있다() {
        UUID userId = UUID.randomUUID();
        Notification firstNotification = createNotification(userId);
        Notification secondNotification = createNotification(userId);

        when(notificationRepository.findByUserIdAndConfirmedFalse(userId))
                .thenReturn(List.of(firstNotification, secondNotification));

        NotificationConfirmAllResponse response =
                notificationService.confirmAllNotifications(userId);

        assertThat(response.confirmedCount()).isEqualTo(2);
        assertThat(firstNotification.isConfirmed()).isTrue();
        assertThat(secondNotification.isConfirmed()).isTrue();
        assertThat(firstNotification.getUpdatedAt()).isNotNull();
        assertThat(secondNotification.getUpdatedAt()).isNotNull();
    }

    @Test
    void 미확인_알림_목록을_조회할_수_있다() {
        UUID userId = UUID.randomUUID();
        Notification firstNotification = createNotification(userId);
        Notification secondNotification = createNotification(userId);

        when(notificationRepository.findByUserIdAndConfirmedFalseOrderByCreatedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of(firstNotification, secondNotification));

        CursorPageResponse<NotificationResponse> response =
                notificationService.getNotifications(userId, 10);

        assertThat(response.content()).hasSize(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void limit이_0보다_작거나_같으면_기본값_10으로_조회한다() {
        UUID userId = UUID.randomUUID();

        when(notificationRepository.findByUserIdAndConfirmedFalseOrderByCreatedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of());

        CursorPageResponse<NotificationResponse> response =
                notificationService.getNotifications(userId, 0);

        assertThat(response.content()).isEmpty();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isZero();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void limit이_100보다_크면_최대값_100으로_조회한다() {
        UUID userId = UUID.randomUUID();

        when(notificationRepository.findByUserIdAndConfirmedFalseOrderByCreatedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of());

        CursorPageResponse<NotificationResponse> response =
                notificationService.getNotifications(userId, 1000);

        assertThat(response.content()).isEmpty();
        assertThat(response.size()).isEqualTo(100);
        assertThat(response.totalElements()).isZero();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 일주일_지난_확인_알림을_삭제한다() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        Instant expectedThreshold = Instant.parse("2026-08-17T00:00:00Z");

        when(notificationRepository.deleteByConfirmedTrueAndUpdatedAtBefore(expectedThreshold))
                .thenReturn(5L);

        long deletedCount = notificationService.deleteOldConfirmedNotifications(now);

        assertThat(deletedCount).isEqualTo(5L);
        verify(notificationRepository).deleteByConfirmedTrueAndUpdatedAtBefore(expectedThreshold);
    }

    @Test
    void 댓글_좋아요_알림을_생성한다() {
        UUID commentAuthorId = UUID.randomUUID();
        UUID likedByUserId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        notificationService.createCommentLikeNotification(
                commentAuthorId,
                likedByUserId,
                "김모뉴",
                commentId
        );

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(notificationCaptor.capture());

        Notification savedNotification = notificationCaptor.getValue();

        assertThat(savedNotification.getUserId()).isEqualTo(commentAuthorId);
        assertThat(savedNotification.getContent()).isEqualTo("김모뉴님이 나의 댓글을 좋아합니다.");
        assertThat(savedNotification.getResourceType()).isEqualTo(NotificationResourceType.COMMENT);
        assertThat(savedNotification.getResourceId()).isEqualTo(commentId);
        assertThat(savedNotification.isConfirmed()).isFalse();
    }

    @Test
    void 관심사_기사_등록_알림을_구독자별로_생성한다() {
        UUID interestId = UUID.randomUUID();
        UUID firstSubscriberId = UUID.randomUUID();
        UUID secondSubscriberId = UUID.randomUUID();

        notificationService.createInterestArticleNotifications(
                interestId,
                "인공지능",
                3,
                List.of(firstSubscriberId, secondSubscriberId)
        );

        ArgumentCaptor<List<Notification>> notificationsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());

        List<Notification> savedNotifications = notificationsCaptor.getValue();

        assertThat(savedNotifications).hasSize(2);
        assertThat(savedNotifications)
                .extracting(Notification::getUserId)
                .containsExactly(firstSubscriberId, secondSubscriberId);
        assertThat(savedNotifications)
                .extracting(Notification::getContent)
                .containsOnly("인공지능와 관련된 기사가 3건 등록되었습니다.");
        assertThat(savedNotifications)
                .extracting(Notification::getResourceType)
                .containsOnly(NotificationResourceType.INTEREST);
        assertThat(savedNotifications)
                .extracting(Notification::getResourceId)
                .containsOnly(interestId);
    }

    @Test
    void 자신의_댓글을_좋아요하면_알림을_생성하지_않는다() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        notificationService.createCommentLikeNotification(
                userId,
                userId,
                "김모뉴",
                commentId
        );

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 댓글_좋아요_알림_생성은_새_트랜잭션에서_실행된다() throws NoSuchMethodException {
        Method method = NotificationService.class.getDeclaredMethod(
                "createCommentLikeNotification",
                UUID.class,
                UUID.class,
                String.class,
                UUID.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void 관심사_기사_알림_생성은_새_트랜잭션에서_실행된다() throws NoSuchMethodException {
        Method method = NotificationService.class.getDeclaredMethod(
                "createInterestArticleNotifications",
                UUID.class,
                String.class,
                int.class,
                List.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}