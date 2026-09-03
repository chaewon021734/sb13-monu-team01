package com.project.monu.domain.comment.controller;

import com.project.monu.domain.comment.dto.CommentDto;
import com.project.monu.domain.comment.dto.CommentLikeDto;
import com.project.monu.domain.comment.dto.request.CommentCreateRequest;
import com.project.monu.domain.comment.dto.request.CommentUpdateRequest;
import com.project.monu.domain.comment.service.CommentService;
import com.project.monu.global.dto.CursorPageResponse;
import com.project.monu.global.constant.RequestHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
public class CommentController implements CommentApiDocs{

    private final CommentService commentService;

    @Override
    @PostMapping
    public ResponseEntity<CommentDto> create(
            @Valid @RequestBody CommentCreateRequest request) {

        CommentDto comment = commentService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(comment);
    }

    @Override
    @GetMapping
    public ResponseEntity<CursorPageResponse<CommentDto>> getComments(
            @RequestParam(required = false) UUID articleId,
            @RequestParam String orderBy,
            @RequestParam String direction,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Instant after,
            @RequestParam int limit,
            @RequestHeader(RequestHeaders.REQUEST_USER_ID) UUID requestUserId
    ) {
        CursorPageResponse<CommentDto> comments = commentService.getComments(
                articleId,
                orderBy,
                direction,
                cursor,
                after,
                limit,
                requestUserId
        );

        return ResponseEntity.ok(comments);
    }

    @Override
    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentDto> update(
            @PathVariable UUID commentId,
            @RequestHeader(RequestHeaders.REQUEST_USER_ID) UUID requestUserId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        CommentDto comment = commentService.update(commentId, requestUserId, request);

        return ResponseEntity.ok(comment);
    }

    @Override
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID commentId) {
        commentService.delete(commentId);

        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{commentId}/hard")
    public ResponseEntity<Void> hardDelete(@PathVariable UUID commentId) {
        commentService.hardDelete(commentId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{commentId}/comment-likes")
    public ResponseEntity<CommentLikeDto> like(
            @PathVariable UUID commentId,
            @RequestHeader(RequestHeaders.REQUEST_USER_ID) UUID requestUserId
    ) {
        CommentLikeDto commentLike = commentService.like(commentId, requestUserId);
        return ResponseEntity.ok(commentLike);
    }

    @Override
    @DeleteMapping("/{commentId}/comment-likes")
    public ResponseEntity<Void> unlike(
            @PathVariable UUID commentId,
            @RequestHeader(RequestHeaders.REQUEST_USER_ID) UUID requestUserId
    ) {
        commentService.unlike(commentId, requestUserId);
        return ResponseEntity.ok().build();
    }


}
