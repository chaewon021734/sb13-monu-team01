package com.project.monu.domain.notification.service;

import com.project.monu.domain.notification.dto.NotificationConfirmAllResponse;
import com.project.monu.domain.notification.dto.NotificationResponse;
import com.project.monu.domain.notification.entity.Notification;
import com.project.monu.domain.notification.entity.NotificationResourceType;
import com.project.monu.domain.notification.repository.NotificationRepository;
import com.project.monu.global.dto.CursorPageResponse;
import com.project.monu.global.exception.BusinessException;
import com.project.monu.global.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int OLD_NOTIFICATION_RETENTION_DAYS = 7;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationResponse confirmNotification(
            UUID notificationId,
            UUID requestUserId
    ) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        validateNotificationOwner(notification, requestUserId);

        notification.confirm(Instant.now());

        return NotificationResponse.from(notification);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<NotificationResponse> getNotifications(
            UUID requestUserId,
            int size
    ) {
        int pageSize = normalizePageSize(size);

        List<Notification> notifications =
                notificationRepository.findByUserIdAndConfirmedFalseOrderByCreatedAtDesc(
                        requestUserId,
                        PageRequest.of(0, pageSize + 1)
                );

        boolean hasNext = notifications.size() > pageSize;

        List<Notification> pageNotifications = hasNext
                ? notifications.subList(0, pageSize)
                : notifications;

        List<NotificationResponse> content = pageNotifications.stream()
                .map(NotificationResponse::from)
                .toList();

        Notification lastNotification = pageNotifications.isEmpty()
                ? null
                : pageNotifications.get(pageNotifications.size() - 1);

        return CursorPageResponse.of(
                content,
                hasNext && lastNotification != null ? lastNotification.getId().toString() : null,
                hasNext && lastNotification != null ? lastNotification.getCreatedAt() : null,
                pageSize,
                content.size(),
                hasNext
        );
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void validateNotificationOwner(
            Notification notification,
            UUID requestUserId
    ) {
        if (!notification.getUserId().equals(requestUserId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }

    @Transactional
    public NotificationConfirmAllResponse confirmAllNotifications(UUID requestUserId) {
        List<Notification> notifications =
                notificationRepository.findByUserIdAndConfirmedFalse(requestUserId);

        Instant confirmedAt = Instant.now();

        notifications.forEach(notification -> notification.confirm(confirmedAt));

        return new NotificationConfirmAllResponse(notifications.size());
    }

    @Transactional
    public long deleteOldConfirmedNotifications(Instant now) {
        Instant threshold = now.minus(OLD_NOTIFICATION_RETENTION_DAYS, ChronoUnit.DAYS);

        return notificationRepository.deleteByConfirmedTrueAndUpdatedAtBefore(threshold);
    }

    @Transactional (propagation = Propagation.REQUIRES_NEW)
    public void createCommentLikeNotification(
            UUID commentAuthorId,
            UUID likedByUserId,
            String likedByUserNickname,
            UUID commentId
    ) {
        if (commentAuthorId.equals(likedByUserId)) {
            return;
        }

        Notification notification = Notification.create(
                commentAuthorId,
                likedByUserNickname + "님이 나의 댓글을 좋아합니다.",
                NotificationResourceType.COMMENT,
                commentId
        );

        notificationRepository.save(notification);
    }

    @Transactional (propagation = Propagation.REQUIRES_NEW)
    public void createInterestArticleNotifications(
            UUID interestId,
            String interestName,
            int articleCount,
            List<UUID> subscriberUserIds
    ) {
        String content = interestName + "와 관련된 기사가 " + articleCount + "건 등록되었습니다.";

        List<Notification> notifications = subscriberUserIds.stream()
                .map(subscriberUserId -> Notification.create(
                        subscriberUserId,
                        content,
                        NotificationResourceType.INTEREST,
                        interestId
                ))
                .toList();

        notificationRepository.saveAll(notifications);
    }
}