package com.project.monu.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

  /*
   * 사용자 관리 - 회원가입
   * 이미 사용 중인 이메일로 회원가입을 시도한 경우 사용합니다.
   */
  EMAIL_ALREADY_EXISTS(
          HttpStatus.CONFLICT,
          "EMAIL_DUPLICATION",
          "이미 존재하는 이메일입니다."
  ),

  /*
   * 사용자 관리 - 로그인
   * 존재하지 않는 이메일이거나 비밀번호가 일치하지 않는 경우 사용합니다.
   */
  LOGIN_FAILED(
          HttpStatus.UNAUTHORIZED,
          "LOGIN_FAILED",
          "이메일 또는 비밀번호가 올바르지 않습니다."
  ),

  INVALID_INPUT_VALUE(
      HttpStatus.BAD_REQUEST,
      "INVALID_INPUT_VALUE",
      "잘못된 입력값입니다."
  ),

  NOTIFICATION_NOT_FOUND(
          HttpStatus.NOT_FOUND,
          "NOTIFICATION_NOT_FOUND",
          "알림을 찾을 수 없습니다."
  ),

  NOTIFICATION_ACCESS_DENIED(
          HttpStatus.FORBIDDEN,
          "NOTIFICATION_ACCESS_DENIED",
          "해당 알림에 접근할 수 없습니다."
  ),

  USER_NOT_FOUND(
          HttpStatus.NOT_FOUND,
          "USER_NOT_FOUND",
          "사용자를 찾을 수 없습니다."
  ),

  USER_UPDATE_ACCESS_DENIED(
      HttpStatus.FORBIDDEN,
    "USER_UPDATE_ACCESS_DENIED",
        "사용자 정보 수정 권한이 없습니다."
  ),

  USER_DELETE_ACCESS_DENIED(
      HttpStatus.FORBIDDEN,
      "USER_DELETE_ACCESS_DENIED",
      "사용자 삭제 권한이 없습니다."
  ),

  /*
   * 뉴스 기사 관리 - 기사 조회 및 삭제
   * 존재하지 않거나 이미 논리 삭제된 기사를 요청한 경우 사용합니다.
   */
  ARTICLE_NOT_FOUND(
          HttpStatus.NOT_FOUND,
          "ARTICLE_NOT_FOUND",
          "기사를 찾을 수 없습니다."
  ),

  ARTICLE_BACKUP_NOT_FOUND(
          HttpStatus.NOT_FOUND,
          "ARTICLE_BACKUP_NOT_FOUND",
          "기사 백업을 찾을 수 없습니다."
  ),

  /*
   * 관심사 관리 - 관심사 등록
   * 기존 관심사와 80% 이상 유사한 이름으로 등록을 시도한 경우 사용합니다.
   */
  INTEREST_ALREADY_EXISTS(
          HttpStatus.CONFLICT,
          "INTEREST_DUPLICATION",
          "이미 유사한 관심사가 존재합니다."
  ),

  INTEREST_NOT_FOUND(
          HttpStatus.NOT_FOUND,
          "INTEREST_NOT_FOUND",
          "관심사를 찾을 수 없습니다."
  ),

  /*
   * 관심사 관리 - 목록 조회
   * nextCursor 파라미터가 "정렬값_id" 형식이 아니거나 파싱할 수 없는 경우 사용합니다.
   */
  INVALID_INTEREST_CURSOR(
          HttpStatus.BAD_REQUEST,
          "INVALID_INTEREST_CURSOR",
          "잘못된 커서 형식입니다."
  ),

  SUBSCRIPTION_ALREADY_EXISTS(
          HttpStatus.CONFLICT,
          "SUBSCRIPTION_DUPLICATION",
          "이미 구독 중인 관심사입니다."
  ),

  SUBSCRIPTION_NOT_FOUND(
          HttpStatus.NOT_FOUND,
          "SUBSCRIPTION_NOT_FOUND",
          "구독 내역을 찾을 수 없습니다."
  ),

  /*
   * 관심사 관리 - 구독/구독취소
   * 동시 요청으로 인해 낙관적 락(버전) 충돌이 발생한 경우 사용합니다.
   */
  INTEREST_CONCURRENT_UPDATE(
          HttpStatus.CONFLICT,
          "INTEREST_CONCURRENT_UPDATE",
          "다른 요청과 동시에 처리되어 반영에 실패했습니다. 다시 시도해주세요."
  ),

  /*
   * 댓글 관리 - 댓글 조회/수정/삭제
   * 존재하지 않거나 이미 삭제된 댓글을 조회하려는 경우 사용
   */
  COMMENT_NOT_FOUND(
          HttpStatus.NOT_FOUND,
        "COMMENT_NOT_FOUND",
        "댓글을 찾을 수 없습니다."
  ),

  /*
   * 댓글 관리 - 댓글 수정/삭제
   * 댓글 작성자가 아닌 사용자가 수정 또는 삭제를 시도한 경우 사용
   */
  COMMENT_ACCESS_DENIED(
          HttpStatus.FORBIDDEN,
        "COMMENT_ACCESS_DENIED",
        "댓글을 수정하거나 삭제할 권한이 없습니다."
  ),

  /*
   * 댓글 관리 - 댓글 목록 조회
   * 올바르지 않은 커서 값으로 조회를 시도한 경우 사용
   */
  COMMENT_INVALID_CURSOR(
          HttpStatus.BAD_REQUEST,
        "COMMENT_INVALID_CURSOR",
        "올바르지 않은 댓글 커서입니다."
  ),

  /*
   * 댓글 관리 - 댓글 목록 조회
   * 지원하지 않는 정렬 기준으로 조회를 시도한 경우 사용
   */
  COMMENT_INVALID_SORT_TYPE(
          HttpStatus.BAD_REQUEST,
        "COMMENT_INVALID_SORT_TYPE",
        "지원하지 않는 댓글 정렬 기준입니다."
  ),

  /*
   * 댓글 관리 - 댓글 목록 조회
   * 지원하지 않는 정렬 방향으로 조회를 시도한 경우 사용
   */
  COMMENT_INVALID_SORT_DIRECTION(
          HttpStatus.BAD_REQUEST,
        "COMMENT_INVALID_SORT_DIRECTION",
        "지원하지 않는 댓글 정렬 방향입니다."
  ),

  /*
   * 댓글 관리 - 댓글 목록 조회
   * 올바르지 않은 조회 개수를 요청한 경우 사용
   */
  COMMENT_INVALID_LIMIT(
          HttpStatus.BAD_REQUEST,
        "COMMENT_INVALID_LIMIT",
        "댓글 조회 개수는 1 이상이어야 합니다."
  ),

  /*
   * 댓글 관리 - 댓글 좋아요
   * 이미 좋아요한 댓글에 다시 좋아요를 시도한 경우 사용
   */
  COMMENT_LIKE_ALREADY_EXISTS(
          HttpStatus.CONFLICT,
        "COMMENT_LIKE_DUPLICATION",
        "이미 좋아요한 댓글입니다."
  ),

  /*
   * 댓글 관리 - 댓글 좋아요
   * 취소할 좋아요 정보가 존재하지 않는 경우 사용
   */
  COMMENT_LIKE_NOT_FOUND(
          HttpStatus.NOT_FOUND,
        "COMMENT_LIKE_NOT_FOUND",
                "댓글 좋아요를 찾을 수 없습니다."
  );

  private final HttpStatus status;
  private final String code;
  private final String message;

  ErrorCode(
          HttpStatus status,
          String code,
          String message
  ) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
