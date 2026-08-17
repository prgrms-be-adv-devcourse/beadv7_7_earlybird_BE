package com.growmighty.lectures.firstday.file.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파일 메타데이터. 파일 주인을 owner_type + owner_id 다형 참조로 두어,
 * 프로젝트 이미지 외(리뷰 사진 등)로 확장해도 테이블 추가가 없다.
 */
@Entity
@Table(name = "files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class File extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileOwnerType ownerType;

    /** owner_type 별 대상 id (논리) — 예: projectdb.projects.id */
    @Column(nullable = false)
    private Long ownerId;

    /** 이 메타데이터를 등록한 사용자(JWT X-User-Id). delete 시 본인 파일인지 확인하는 데 쓴다. */
    @Column(nullable = false)
    private Long uploaderId;

    @Column(nullable = false)
    private String storedUrl;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    /** 프로젝트 이미지 노출 순서 */
    @Column(nullable = false)
    private Integer sortOrder;

    private File(FileOwnerType ownerType, Long ownerId, Long uploaderId, String storedUrl, String originalName,
                 String contentType, Long fileSize, int sortOrder) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId는 필수입니다.");
        }
        if (uploaderId == null) {
            throw new IllegalArgumentException("uploaderId는 필수입니다.");
        }
        if (storedUrl == null || storedUrl.isBlank()) {
            throw new IllegalArgumentException("저장 경로는 필수입니다.");
        }
        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException("파일 크기는 0보다 커야 합니다. 입력값: " + fileSize);
        }
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.uploaderId = uploaderId;
        this.storedUrl = storedUrl;
        this.originalName = originalName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sortOrder = sortOrder;
    }

    public static File register(FileOwnerType ownerType, Long ownerId, Long uploaderId, String storedUrl,
                                String originalName, String contentType, Long fileSize, int sortOrder) {
        return new File(ownerType, ownerId, uploaderId, storedUrl, originalName, contentType, fileSize, sortOrder);
    }

    public boolean isUploadedBy(Long requesterId) {
        return uploaderId.equals(requesterId);
    }
}
