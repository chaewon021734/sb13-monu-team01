package com.project.monu.domain.comment.controller;

import com.project.monu.domain.comment.dto.CommentDto;
import com.project.monu.domain.comment.dto.CommentLikeDto;
import com.project.monu.domain.comment.dto.request.CommentCreateRequest;
import com.project.monu.domain.comment.dto.request.CommentUpdateRequest;
import com.project.monu.global.dto.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "댓글 관리", description = "댓글 관련 API")
public interface CommentApiDocs {

    @Operation(summary = "댓글 목록 조회", description = "조건에 맞는 댓글 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (정렬 기준 오류, 페이지네이션 파라미터 오류 등)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<CursorPageResponse<CommentDto>> getComments(
            @Parameter(description = "기사 ID") UUID articleId,
            @Parameter(description = "정렬 속성 이름") String orderBy,
            @Parameter(description = "정렬 방향 (ASC, DESC)") String direction,
            @Parameter(description = "커서 값") String cursor,
            @Parameter(description = "보조 커서(createdAt) 값") Instant after,
            @Parameter(description = "커서 페이지 크기", example = "50") int limit,
            @Parameter(description = "요청자 ID") UUID requestUserId
    );

    @Operation(summary = "댓글 등록", description = "새로운 댓글을 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (입력값 검증 실패)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<CommentDto> create(CommentCreateRequest request);

    @Operation(summary = "댓글 좋아요", description = "댓글 좋아요를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 좋아요 성공"),
            @ApiResponse(responseCode = "404", description = "댓글 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<CommentLikeDto> like(
            @Parameter(description = "댓글 ID") UUID commentId,
            @Parameter(description = "요청자 ID") UUID requestUserId
    );

    @Operation(summary = "댓글 좋아요 취소", description = "댓글 좋아요를 취소합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 좋아요 취소 성공"),
            @ApiResponse(responseCode = "404", description = "댓글 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<Void> unlike(
            @Parameter(description = "댓글 ID") UUID commentId,
            @Parameter(description = "요청자 ID") UUID requestUserId
    );

    @Operation(summary = "댓글 논리 삭제", description = "댓글을 논리적으로 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "댓글 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<Void> delete(@Parameter(description = "댓글 ID") UUID commentId);

    @Operation(summary = "댓글 정보 수정", description = "댓글의 내용을 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (입력값 검증 실패)"),
            @ApiResponse(responseCode = "404", description = "댓글 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<CommentDto> update(
            @Parameter(description = "댓글 ID") UUID commentId,
            @Parameter(description = "요청자 ID") UUID requestUserId,
            CommentUpdateRequest request
    );

    @Operation(summary = "댓글 물리 삭제", description = "댓글을 물리적으로 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "댓글 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<Void> hardDelete(@Parameter(description = "댓글 ID") UUID commentId);
}