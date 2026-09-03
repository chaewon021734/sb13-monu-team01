package com.project.monu.domain.comment.entity;

import com.project.monu.domain.article.entity.Article;
import com.project.monu.domain.users.entity.User;
import com.project.monu.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "comment",
        indexes = {
                @Index(name = "idx_comment_article_created", columnList = "article_id, created_at"),
                @Index(name = "idx_comment_user", columnList = "user_id")
        }
)
public class Comment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Comment(Article article, User user, String content) {
        this.article = article;
        this.user = user;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }
}