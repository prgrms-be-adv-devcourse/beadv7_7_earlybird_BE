# File 도메인

담당자: 강대혁
기준일: 2026-08-31

이 문서는 목표 설계가 아니라 현재 `file-service` 구현을 기준으로 File 도메인의 책임과 처리 흐름을 설명한다.
업로드 방식(백엔드 프록시 / Cloudinary unsigned / presigned)을 비교한 **선택 배경**은 [`file-service/README.md`](../README.md)에 있고, 이 문서는 그 결정 이후의 구현 상태를 다룬다.

## 0. 목차

1. 도메인 개요
2. 도메인 모델
3. 상태 전이
4. API
5. 주요 처리 흐름
6. 데이터 저장 구조
7. 도메인 이벤트와 서비스 간 통신
8. 예외 처리와 장애 복구
9. 테스트 현황
10. 현재 한계와 후속 과제

## 1. 도메인 개요

### 1.1 책임 범위

file-service는 **파일 바이트를 다루지 않는다.** 실제 이미지는 브라우저가 S3에 직접 PUT하고, 이 서비스는 "누가(uploaderId) 어떤 파일을 어디에(storedUrl) 올렸고, 그게 누구 것인가(ownerType + ownerId)"라는 **메타데이터와 그 접근 권한**만 책임진다. 파일 바이트는 게이트웨이도 file-service도 거치지 않는다.

- **presigned URL 발급** — 업로드용 PUT URL(10분), 조회용 GET URL(5분). 자격증명은 file-service만 가진다
- **소유권 연결(register)** — 업로드가 끝난 오브젝트를 실제 주인(프로젝트 등)에게 붙인다. presign 시점엔 아직 주인이 없을 수 있어(생성 전 프로젝트) 두 단계로 나뉜다
- **업로드 표면 통제** — contentType 허용 목록, 오브젝트 키 경로 조작 방지, `storedUrl` 출처 검증
- **소유자 기준 조회와 삭제** — 개별 삭제(업로더 본인)와 owner 단위 일괄 삭제(서비스 간)
- **비공개 버킷 중개** — 버킷을 직접 노출하지 않고, 응답 시점마다 짧게 만료되는 presigned GET URL로 바꿔 내려준다

이 도메인이 담당하지 않는 범위:

| 범위 | 담당 |
| --- | --- |
| 파일 바이트 저장 | S3 (브라우저가 직접 PUT) |
| S3 오브젝트 실물 삭제 | **아무도 안 한다** — 소프트 딜리트만 하고 정리 배치/lifecycle 정책이 없다 (§10) |
| 프로젝트 창작자가 누구인지 | project-service (`GET /internal/v1/projects/{id}/creator`) |
| 후기(REVIEW) 작성자가 누구인지 | board-service — 확인용 내부 API가 아직 없다 (§10) |
| 썸네일을 프로젝트에 연결(`thumbnailId`) | project-service |

### 1.2 다른 서비스와의 관계

| 연관 대상 | 통신 방향 | 주고받는 정보 | File의 책임 | 상대 대상의 책임 |
| --- | --- | --- | --- | --- |
| S3 (호환 스토리지) | File → S3 | presign 서명 (로컬 SigV4 연산, 네트워크 호출 없음) | `(bucket, key, contentType)` 하나에만 유효한 서명 발급 | 서명 검증, 실제 바이트 저장 |
| 브라우저 | 브라우저 ↔ S3 | 파일 바이트 PUT | 관여하지 않음 | 발급받은 `uploadUrl`로 직접 업로드 |
| project-service | File → project (RestClient) | `GET /internal/v1/projects/{id}/creator` | `register(PROJECT)` 시 소유권 확인 요청 | 창작자 id 응답 |
| project-service | project → File (Kafka) | `project.deleted.v1` 구독 | 그 프로젝트 소유 파일 메타데이터 일괄 삭제 | 프로젝트 삭제 커밋 후 이벤트 발행 (best-effort) |
| board-service | board → File (Feign) | `GET /internal/v1/files/batch` | 여러 owner의 파일을 한 번에 응답 | 후기 목록에 첨부 사진을 붙일 때 N+1 없이 조회 |
| chat-service | chat → File (Feign) | `GET /api/v1/files` | 소유자 기준 파일 목록 응답 | AI 챗봇이 프로젝트 썸네일 URL을 얻을 때 |
| gateway-server | 외부 → gateway → File | JWT → `X-User-Id` 헤더 | 헤더의 사용자 id를 업로더/요청자로 사용 | JWT 검증과 헤더 투영, `GET /api/v1/files`만 permitAll |

```
브라우저 → gateway → POST /api/v1/files/presigned-upload  : URL 발급 (인증 필요)
브라우저 → S3       PUT uploadUrl (파일 바이트)            : file-service를 거치지 않음
브라우저 → gateway → POST /api/v1/files                    : 메타데이터 등록 + 소유권 연결
                        └→ project-service GET creator     : PROJECT면 창작자 확인 (fail-closed)
브라우저 → gateway → PATCH /api/v1/projects/{id}           : thumbnailId 연결 (project-service)

project-service → Kafka project.deleted.v1 → file-service  : 프로젝트 삭제 시 파일 정리
board-service   → GET /internal/v1/files/batch             : 후기 첨부 사진 일괄 조회
```

책임 경계:

- **소유권 검증은 원본 서비스에 묻는다.** file-service는 "이 프로젝트의 주인이 누구인지"를 스스로 알지 못하고, 알려고도 하지 않는다 — `ProjectPort` 인터페이스 하나로만 바라본다
- **소유권 확인 실패는 fail-closed다.** project-service 장애 시 낙관적으로 통과시키지 않고 503으로 거부한다. 보안 경계라 원격 서비스 장애가 우회 수단이 되면 안 된다
- **파일 삭제 요청의 출처에 따라 검증 강도가 다르다.** 사용자 요청(`DELETE /api/v1/files/{id}`)은 업로더 본인 확인을 하고, 서비스 간 요청(Kafka 이벤트)은 owner 단위로 확인 없이 지운다 — 이벤트를 보낸 서비스가 이미 그 owner에 대한 권한을 확인했다고 신뢰한다

### 1.3 기능 범위

| 기능 | 권한 |
| --- | --- |
| presigned 업로드 URL 발급 | 인증된 사용자 |
| 메타데이터 등록 (register) | 인증된 사용자 + `PROJECT`면 창작자 본인 |
| 소유자 기준 조회 | **공개** (비로그인 포함, `GET /api/v1/files`만 permitAll) |
| 개별 삭제 | 업로더 본인 |
| owner 단위 일괄 삭제 | 서비스 간 (Kafka 이벤트) |
| 여러 owner 일괄 조회 | 서비스 간 (board-service) |

## 2. 도메인 모델

### 2.1 File

파일 메타데이터 한 건. 주인을 `ownerType` + `ownerId` **다형 참조**로 두어, 용도가 늘어도 테이블 추가 없이 `FileOwnerType`에 값만 늘리면 된다. 크로스 서비스 참조는 이 레포 관례대로 ID로만 하고 JPA 연관관계를 걸지 않는다.

| 필드 | 의미 | 제약조건 |
| --- | --- | --- |
| `id` | PK | `IDENTITY` |
| `ownerType` | 주인의 종류 | `NOT NULL`, `EnumType.STRING` |
| `ownerId` | 주인의 id (다른 서비스 DB의 논리적 FK) | `NOT NULL`, 생성 시 null 금지 |
| `uploaderId` | 이 메타데이터를 등록한 사용자 (`X-User-Id`) | `NOT NULL`. 삭제 시 본인 확인에 쓴다 |
| `storedUrl` | `{cdn-base-url}/{key}` | `NOT NULL`, 공백 금지 |
| `originalName` | 원본 파일명 | `NOT NULL` |
| `contentType` | MIME 타입 | `NOT NULL` |
| `fileSize` | 바이트 크기 | `NOT NULL`, 0 초과 |
| `sortOrder` | 같은 owner 안에서의 노출 순서 | `NOT NULL` |
| `deletedAt` | 소프트 딜리트 시각 | null이면 살아있는 레코드 |

`BaseEntity`를 상속해 생성/수정 시각이 JPA Auditing으로 관리된다.

주요 도메인 규칙:

- **생성(`File.register`)은 최소 불변식만 검증한다** — `ownerId`/`uploaderId` null 금지, `storedUrl` 공백 금지, `fileSize` 0 초과. 파일 형식·크기 상한 같은 나머지 검증은 presign 단계(Bean Validation)와 스토리지 쪽 제약에서 이미 걸러졌다고 보고 도메인에서 반복하지 않는다
- **`isUploadedBy(requesterId)`** — 삭제 권한 판정의 유일한 기준. 소유자(ownerId)가 아니라 **업로더**가 기준이다
- **삭제는 소프트 딜리트** — `@SQLDelete`가 `deleteById`를 `UPDATE files SET deleted_at = NOW()`로 바꾸고, `@SQLRestriction("deleted_at IS NULL")`이 모든 조회에 자동으로 걸린다. 애플리케이션 코드는 소프트 딜리트를 의식하지 않는다
- **수정 메서드가 없다.** 등록된 파일 메타데이터는 불변이다 — 바꿔야 하면 새로 등록하고 옛것을 지운다

### 2.2 FileOwnerType (enum)

| 값 | 용도 | 소유권 검증 |
| --- | --- | --- |
| `PROJECT` | 프로젝트 썸네일 | ✅ project-service에 창작자 확인 |
| `REVIEW` | 후기 사진 (board-service) | ❌ 확인용 내부 API가 없다 |
| `REWARD` | 리워드 사진 | ❌ 검증도, 삭제 연동도 없다 |

`REVIEW`와 `REWARD`는 등록 시 소유권 확인 없이 통과한다 — 인증된 사용자라면 임의의 `ownerId`를 붙일 수 있다는 뜻이다(§10). README의 "PROJECT/REVIEW 두 가지" 서술은 `REWARD`가 추가되기 전 기준이라 현재 코드와 다르다.

## 3. 상태 전이

File은 status 필드가 없고, `deletedAt` 하나로 살아있음/지워짐만 구분한다. 다만 **오브젝트 관점에서 보면 메타데이터가 없는 중간 상태가 존재한다.**

```
[presign 발급]  ── 10분 안에 PUT 안 하면 ──→ (아무것도 안 생김)
      │
      ├─ 브라우저가 S3에 PUT 성공
      │      │
      │      ├─ register 호출됨 ──→ File(deletedAt = null)  : 정상
      │      │                          │
      │      │                          ├─ DELETE /api/v1/files/{id}   ─┐
      │      │                          └─ Kafka project.deleted.v1     ─┴→ File(deletedAt = NOW)
      │      │                                                              [S3 오브젝트는 남음]
      │      │
      │      └─ register 안 됨 ──→ ⚠ 고아 오브젝트 (S3에만 존재, DB에 기록 없음)
```

| 상태 | 의미 | 진입 조건 |
| --- | --- | --- |
| 정상 | 조회·삭제 가능 | `register` 성공 |
| 삭제됨 | 모든 조회에서 자동 제외 | `deleted_at` 채워짐 |
| **고아 오브젝트** | S3에는 있는데 DB에 기록이 없음 | PUT은 성공했으나 `register`가 호출되지 않음 |
| **고아 S3 오브젝트** | 메타데이터는 삭제됐는데 실물이 남음 | 소프트 딜리트 후 (항상) |

- **되돌리는 경로가 없다.** 소프트 딜리트를 복구하는 API는 없다 — `deleted_at`을 직접 비우는 수밖에 없다
- 아래 두 "고아" 상태를 정리하는 배치도 lifecycle 정책도 현재 없다. 지금은 **삭제할수록 S3 사용량이 단조 증가한다**(§10)

## 4. API

### 4.1 외부 API

게이트웨이 라우트는 `/api/v1/files/**`이고 StripPrefix가 없어 경로가 그대로 전달된다.

| Method | Path | 요청 | 응답 | 동작 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/files/presigned-upload` | `PresignedUploadRequest` + `X-User-Id` | `PresignedUploadResponse` | 업로드용 presigned PUT URL 발급 |
| POST | `/api/v1/files` | `RegisterFileRequest` + `X-User-Id` | `FileResponse` | 메타데이터 등록 + 소유권 연결 |
| GET | `/api/v1/files?ownerType=&ownerId=` | — | `List<FileResponse>` | 소유자 기준 목록 조회 |
| DELETE | `/api/v1/files/{fileId}` | `X-User-Id` | `null` | 소프트 딜리트 (업로더 본인만) |

**인가 경계**: 게이트웨이 `SecurityConfig`가 `GET /api/v1/files` **정확히 그 경로만** `permitAll`이고, 나머지 `/api/v1/files/**`는 전부 `authenticated()`다. 프로젝트 썸네일·후기 사진은 전부 공개 콘텐츠라 비로그인 방문자도 봐야 한다는 정책 결정(#376)이며, 게시·삭제는 인증(+소유권 확인)이 필요하다.

> README의 "게이트웨이가 `/api/v1/files/**` 전체를 `authenticated()`로 막아둬서 네 엔드포인트 모두 `X-User-Id` 없이는 호출 자체가 안 된다"는 서술은 #376 이전 기준이라 현재 코드와 다르다.

### 외부 API 데이터 스키마

```jsonc
// PresignedUploadRequest
{
  "contentType": "image/jpeg",   // @NotBlank + @Pattern — 이미지 MIME 허용 목록
  "originalName": "thumb.jpg"    // @NotBlank
}

// PresignedUploadResponse
{
  "uploadUrl": "https://<bucket>.s3.<region>.amazonaws.com/files/42/2026/08/<uuid>.jpg?X-Amz-Signature=...",
  "storedUrl": "https://<cdn-base-url>/files/42/2026/08/<uuid>.jpg",
  "requiredHeaders": { "Content-Type": "image/jpeg" }
}

// RegisterFileRequest
{
  "ownerType": "PROJECT",        // @NotNull — PROJECT | REVIEW | REWARD
  "ownerId": 10,                 // @NotNull
  "storedUrl": "https://<cdn-base-url>/files/42/2026/08/<uuid>.jpg",  // @NotBlank + 출처 검증
  "originalName": "thumb.jpg",   // @NotBlank
  "contentType": "image/jpeg",   // @NotBlank
  "fileSize": 204800,            // @NotNull @Positive
  "sortOrder": 0
}

// FileResponse
{
  "id": 1, "ownerType": "PROJECT", "ownerId": 10,
  "storedUrl": "https://...?X-Amz-Signature=...",   // ← DB 원본이 아니라 5분짜리 presigned GET URL
  "originalName": "thumb.jpg", "contentType": "image/jpeg",
  "fileSize": 204800, "sortOrder": 0
}
```

`contentType` 허용 목록은 코드 기준 다음과 같다(대소문자 무시):
`image/` + `jpeg | jpg | pjpeg | x-png | png | webp | gif | avif | heic | heif | bmp`.
README에 적힌 `jpeg|png|webp|gif` 4종은 확장 전 기준이다.

**`FileResponse.storedUrl`은 DB에 저장된 값이 아니다.** 버킷이 private이라 원본 URL은 클라이언트가 직접 열 수 없어서, 응답을 만들 때마다 `presignDownload()`로 5분짜리 presigned GET URL을 새로 발급해 그 자리에 넣는다(`FileService.toFileInfo`). 즉 **같은 파일을 두 번 조회하면 `storedUrl` 값이 다르다** — 클라이언트가 이 값을 캐시하거나 DB에 저장하면 안 된다.

### 4.2 내부 API

| Method | Path | 호출 서비스 | 동작 |
| --- | --- | --- | --- |
| GET | `/internal/v1/files/batch?ownerType=&ownerIds=` | board-service | 여러 owner의 파일 일괄 조회 (N+1 방지) |

### 내부 API 데이터 스키마

```
GET /internal/v1/files/batch?ownerType=REVIEW&ownerIds=1,2,3  → List<FileResponse>
```

내부 API는 조회 하나뿐이다. 예전에는 owner 단위 일괄 삭제(`DELETE /internal/v1/files`)도 있었는데, 프로젝트 삭제 시 파일 정리가 동기 HTTP에서 Kafka(`project.deleted.v1`, #690)로 전환되면서 호출자가 사라졌다 — 인증 없이 호출 가능한 삭제 경로를 남겨둘 이유가 없어 제거했다.

신뢰 경계: `/internal/v1`은 게이트웨이 라우트가 없는 Eureka-to-Eureka 직접 호출 전용이라 JWT도 `X-User-Id`도 요구하지 않는다. 남은 `GET /batch`는 조회 전용이라 이 신뢰 모델에서 위험이 낮다.

## 5. 주요 처리 흐름

### 5.1 업로드 (정상 흐름)

```
① presign
   FileController.presign                       [X-User-Id 필수]
   → FileService.issuePresignedUpload
   → S3PresignedUploadGenerator.generate
        key = files/{requesterId}/{yyyy}/{MM}/{uuid}{안전한 확장자}
        PutObjectPresignRequest, 유효 10분, contentType 고정
        (로컬 SigV4 서명 — S3로 나가는 네트워크 호출이 없다)
   → { uploadUrl, storedUrl, requiredHeaders }

② 업로드
   브라우저 → S3  PUT uploadUrl  (body = 파일 바이트, Content-Type 헤더 필수)
   ※ 게이트웨이도 file-service도 거치지 않는다

③ register
   FileController.register                      [X-User-Id 필수]
   → FileService.register                       [@Transactional]
      1. validateStoredUrlOrigin
           storedUrl이 "{cdn-base-url}/files/{uploaderId}/"로 시작하지 않으면 400
      2. ownerType == PROJECT 이면
           projectPort.getCreatorId(ownerId)    ← project-service, 서킷브레이커 3s
           requesterId != creatorId → 403
      3. File.register(...) → save
   → FileResponse (storedUrl은 5분짜리 presigned GET)

④ 연결
   브라우저 → PATCH /api/v1/projects/{id}  { thumbnailId: fileId }   (project-service)
```

**presign과 register를 나눈 이유**: presign 시점엔 아직 주인이 없을 수 있다(프로젝트를 만들기 전에 썸네일부터 올리는 흐름). 그래서 presign 요청은 `ownerId`를 아예 받지 않고, 소유권 연결을 register로 미룬다.

**세 겹의 업로드 표면 통제**가 이 흐름에 들어 있다.

| 통제 | 위치 | 막는 것 |
| --- | --- | --- |
| contentType 허용 목록 | `PresignedUploadRequest` `@Pattern` | `text/html`로 PUT한 오브젝트가 CDN에서 그 MIME으로 서빙되어 생기는 **저장형 XSS** |
| 확장자 화이트리스트 (`^\.[a-zA-Z0-9]{1,10}$`) | `S3PresignedUploadGenerator.extensionOf` | `a.jpg/../../secret` 같은 `originalName`으로 키에 `/`·`..`가 섞여 들어가는 **경로 조작** |
| `storedUrl` 출처 검증 | `FileService.validateStoredUrlOrigin` | 본인이 올린 오브젝트가 아니라 **임의의 외부 URL(악성 사이트) 등록** |

세 번째가 특히 중요하다. presign 키에 `requesterId`가 들어가 있기 때문에 `{cdn-base-url}/files/{uploaderId}/` 접두사 검사만으로 "본인이 발급받아 올린 것"을 확인할 수 있다 — 키 형식이 곧 검증 수단인 구조다.

### 5.2 조회 (presigned GET 중개)

```
FileController.getFilesByOwner / FileInternalController.getFilesByOwners
→ FileService.getFilesByOwner(s)                [@Transactional(readOnly = true)]
→ fileRepository.findByOwnerTypeAndOwnerId(In)  (deleted_at IS NULL 자동 적용)
→ 각 File마다 presignDownload(storedUrl)        ← 5분짜리 GET URL을 새로 발급
→ List<FileResponse>
```

발급 자체엔 인가가 없다. 이건 접근 제어가 아니라 **"버킷을 직접 노출하지 않고 file-service가 중개한다"**는 목적이다 — 버킷을 private으로 유지하면서, 나중에 스토리지 백엔드를 바꿔도 클라이언트가 보는 URL 체계를 file-service가 흡수할 수 있다. 비공개 콘텐츠 타입이 추가되면 그때 `toFileInfo`에 인가 검사를 넣어야 한다.

> ⚠️ 파일 N건을 조회하면 presign 연산도 N번 돈다. 로컬 SigV4 연산이라 네트워크 호출은 없지만 CPU 비용은 선형으로 늘고, `batch` 조회는 owner 수만큼 곱해진다.

### 5.3 삭제 (두 경로)

```
[A] 사용자 요청   DELETE /api/v1/files/{fileId}   + X-User-Id
    → findById (없으면 404)
    → isUploadedBy(requesterId) 아니면 403
    → deleteById → @SQLDelete → UPDATE files SET deleted_at = NOW()

[B] Kafka        project.deleted.v1  구독  (#690)
    → ProjectDeletedKafkaListener.onProjectDeleted
    → payload 유효성만 확인 (event/payload/projectId null이면 WARN 후 무시)
    → deleteByOwner(PROJECT, projectId)   ← 소유권 확인 없음

```

두 경로 모두 **S3 오브젝트는 지우지 않는다.** 메타데이터의 `deleted_at`만 채워지고 실물은 그대로 남는다.

경로 [B]는 원래 project-service가 `DELETE /internal/v1/files`를 동기 호출하던 것을 대체한 것이다(#689 → #690). 삭제 트랜잭션 커밋 후(`AFTER_COMMIT`) 발행하므로 롤백된 삭제 시도가 file-service로 새어나가지 않고, 삭제 트랜잭션이 카프카 응답을 기다리며 DB 락을 물고 있지도 않는다. file-service가 죽어 있어도 프로젝트 삭제가 막히지 않는 것이 전환의 직접적인 목적이었다(#689).

### 5.4 실패 및 보상 흐름

```
presign 발급 성공
→ 브라우저 PUT 실패 / 10분 만료      : 아무것도 안 생김 (부작용 없음)
→ PUT 성공, register 실패(403/400/503) : ⚠ 고아 오브젝트 — S3에만 남고 정리 경로 없음
→ register 성공                        : 정상

프로젝트 삭제
→ project-service Kafka 발행 실패     : best-effort, WARN 로그만. 프로젝트 삭제는 그대로 진행
                                        → ⚠ 파일 메타데이터가 남는다 (고아 메타데이터)
→ file-service 소비 실패              : project.deleted.v1.DLT로 격리
```

- **보상 트랜잭션이 없다.** register 실패 시 방금 올라간 S3 오브젝트를 지우는 경로가 없다 — presign은 file-service가 발급했지만 PUT 결과는 file-service가 알지 못하기 때문이다
- **중복 요청**: `register`에 멱등키가 없다. 같은 `storedUrl`로 두 번 등록하면 File 레코드가 두 개 생긴다(유니크 제약 없음). presign이 매번 새 UUID 키를 만들어 실사용에서 잘 드러나지 않을 뿐이다
- **Kafka 소비 멱등성**: `deleteByOwner`는 "그 owner의 살아있는 파일을 전부 지운다"라 같은 이벤트가 두 번 와도 결과가 같다(두 번째는 대상 0건)

## 6. 데이터 저장 구조

```
FileService
→ FileRepository (domain 인터페이스)
→ FileRepositoryAdapter (infrastructure)
→ FileJpaRepository (Spring Data JPA)
→ MySQL → files
```

| 항목 | 내용 |
| --- | --- |
| 테이블 | `files` (단일 테이블) |
| 유니크 | **없다.** `storedUrl`에도 유니크가 없어 같은 오브젝트를 여러 번 등록할 수 있다 |
| 외래 키 | 없다. `owner_id`, `uploader_id` 모두 다른 서비스 DB를 가리키는 논리 참조 |
| 인덱스 | 별도 선언 없음 — `(owner_type, owner_id)` 조회가 주 경로인데 인덱스가 없다 (§10) |
| 체크 제약 | 없다. `fileSize > 0` 등은 엔티티가 검증 |
| 동시성 제어 | 없다. `@Version`도 락도 없다 — 등록 후 수정되지 않는 불변 레코드라 경합 지점이 없다 |
| 소프트 딜리트 | `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")` (Hibernate) |

`FileRepository`(domain 인터페이스)와 `FileRepositoryAdapter`(구현) 분리는 이 레포의 포트/어댑터 관례를 따른 것이다. 다만 어댑터가 하는 일은 `FileJpaRepository`로의 단순 위임뿐이다.

**소프트 딜리트를 Hibernate 애노테이션으로 처리한 것이 이 도메인의 핵심 구현 선택이다.** 애플리케이션 코드 어디에도 `deletedAt` 조건이 등장하지 않고, `deleteById`를 부르면 자동으로 UPDATE가 나간다. 대가로, **네이티브 쿼리나 다른 도구로 이 테이블을 조회하면 삭제된 행이 그대로 보인다** — 필터가 Hibernate 레이어에만 존재하기 때문이다.

## 7. 도메인 이벤트와 서비스 간 통신

file-service는 이벤트를 **발행하지 않고 구독만 한다.**

```
project-service: 프로젝트 삭제 커밋
→ (AFTER_COMMIT) KafkaFileEventPublisher → project.deleted.v1
→ file-service: ProjectDeletedKafkaListener
→ FileService.deleteByOwner(PROJECT, projectId)
```

| 토픽 | 방향 | 페이로드 | 처리 |
| --- | --- | --- | --- |
| `project.deleted.v1` | 구독 (groupId `file-service`) | `{eventId, eventType, schemaVersion, occurredAt, payload{projectId}}` | 해당 프로젝트 소유 파일 메타데이터 일괄 소프트 딜리트 |
| `project.deleted.v1.DLT` | — | 동일 | 처리 실패 격리 (`common`의 `KafkaTopics`에 정의) |

- 역직렬화는 `@KafkaListener`의 `properties`로 `spring.json.value.default.type`을 지정해 처리한다 — 발행 측(project-service)과 소비 측(file-service)이 **각자 자기 패키지에 같은 모양의 record 사본**을 두는 방식이다. 서비스 간 클래스 공유를 하지 않는 대신, 스키마 변경 시 양쪽을 같이 고쳐야 한다

> ⚠️ **이 역직렬화가 실제로 동작하는지 미검증이다(#764의 "선행 확인 필요").** 공용 `application.yml`의 producer는 맨 `JsonSerializer`라 `spring.json.add.type.headers`가 기본값(true)이고, project-service에는 `spring.json.type.mapping` 설정이 없다 — 즉 `__TypeId__` 헤더에 project-service의 FQCN이 그대로 실려 온다. `JsonDeserializer`는 타입 헤더가 있으면 그것을 `value.default.type`보다 **우선**하므로, file-service 클래스패스에 없는 그 FQCN을 로드하려다 실패해 전량 DLT로 갈 가능성이 높다. `trusted.packages=*`는 신뢰 범위만 넓힐 뿐 없는 클래스를 만들어주지는 않는다.
> order-service·payment-service는 **producer 쪽 설정(config repo)에도 `spring.json.type.mapping` alias**를 둬서 이 문제를 피하고 있다 — project-service만 그 짝이 빠져 있다. 프로젝트 삭제가 드물어 이 경로가 실제로 돌아본 적이 없을 수 있다. 검증 후 producer에 alias를 넣거나 consumer에 `spring.json.use.type.headers=false`를 주는 정리가 필요하다.
- `event`/`payload`/`projectId`가 null이면 예외 대신 **WARN 로그 후 무시**한다 — 깨진 메시지 하나가 컨슈머를 멈추지 않게 하기 위해서다
- **순서 보장**: 발행 측이 `projectId`를 파티션 키로 쓰므로 같은 프로젝트의 이벤트는 순서가 보장된다
- **발행 측이 best-effort다.** project-service가 5초 타임아웃 안에 발행하지 못하면 WARN 로그만 남기고 프로젝트 삭제를 계속 진행한다 — 그 경우 file-service에는 삭제 이벤트가 오지 않아 메타데이터가 남는다

## 8. 예외 처리와 장애 복구

| 장애 상황 | 판별 기준 | 처리 방식 | 최종 상태 |
| --- | --- | --- | --- |
| 허용 목록 밖 contentType | `@Pattern` 불일치 | Bean Validation | 400 |
| 필수 필드 누락 / `fileSize <= 0` | `@NotNull`/`@NotBlank`/`@Positive` | Bean Validation | 400 |
| `storedUrl` 출처 불일치 | 접두사 검사 실패 | `BusinessException(BAD_REQUEST)` | 400 |
| 존재하지 않는 파일 삭제 | `findById` empty | `EntityNotFoundException` | 404 |
| 존재하지 않는 프로젝트에 register | project-service 404 | `EntityNotFoundException` | 404 |
| 남의 프로젝트에 register | `requesterId != creatorId` | `BusinessException(FORBIDDEN)` | 403 |
| 남의 파일 삭제 | `isUploadedBy` false | `BusinessException(FORBIDDEN)` | 403 |
| project-service 응답 없음 | 서킷 OPEN / 3초 타임아웃 | **fail-closed** — `BusinessException(SERVICE_UNAVAILABLE)` | 503 |
| 깨진 Kafka 메시지 | payload null | WARN 로그 후 무시 | 커밋 (재처리 안 함) |
| Kafka 소비 중 예외 | — | `project.deleted.v1.DLT`로 격리 | 수동 재처리 |

**서킷브레이커 설정** (`FileCircuitBreakerConfig`, 기본 설정으로 모든 id에 적용): 타임아웃 3초, `slidingWindowSize=10`, `minimumNumberOfCalls=4`, `failureRateThreshold=50%`, `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=2`. 실제로 쓰는 id는 `project-creator` 하나다.

이 설정의 핵심은 **`ignoreExceptions(EntityNotFoundException.class)`**다. 존재하지 않는 `projectId`로 register를 시도하는 것은 project-service 장애가 아니라 잘못된 입력인데, 이를 서킷 실패로 집계하면 그런 요청이 반복될 때(버그 또는 악의적 요청) 서킷이 열려 **무관한 다른 정상 프로젝트의 register까지 fail-closed로 503 거부된다.** `ProjectHttpClient`가 404를 `EntityNotFoundException`으로 변환하고, 설정이 그것을 실패 집계에서 빼는 두 조각이 맞물려 동작한다.

**fail-closed 판단**: `failClosed`는 `EntityNotFoundException`이면 그대로 다시 던지고(404 유지), 그 외 원인은 503으로 바꾼다. cart-service가 리워드 조회 실패 시 낙관적으로 통과시키는 것과 반대 방향의 선택이며, 이유는 소유권 검증이 보안 경계이기 때문이다 — project-service 장애가 소유권 검증의 우회 수단이 되면 안 된다.

**복구 수단**:

- **파일 메타데이터 정합성 복구 경로가 없다.** Kafka 발행 실패로 남은 고아 메타데이터, register 실패로 남은 고아 S3 오브젝트 모두 자동 탐지·정리가 없다
- **재처리**: DLT에 쌓인 메시지를 수동으로 다시 넣는 것 외에 자동 재시도가 없다
- **AWS 자격증명**은 SDK 기본 체인(환경변수 / 인스턴스 프로필)을 그대로 쓴다. 자격증명이 없으면 presign 발급 시점에 실패하고, 서비스 기동 자체는 막지 않는다

## 9. 테스트 현황

| 테스트 | 검증 범위 |
| --- | --- |
| `FileServiceTest` | register의 storedUrl 출처 검증, PROJECT 소유권 확인(403), 삭제 시 업로더 본인 확인(403), 조회 시 presigned GET 치환 |
| `S3PresignedUploadGeneratorTest` | 키 형식(`files/{id}/{yyyy}/{MM}/{uuid}{ext}`), 확장자 화이트리스트, 경로 조작 입력 차단, presign 만료 시간 — **실제 AWS 자격증명 없이 검증 가능**(로컬 SigV4 연산이라 네트워크 호출이 없다) |
| `FileSoftDeleteIntegrationTest` | `@SQLDelete`/`@SQLRestriction` 동작 — 삭제 후 조회에서 빠지는지 (Testcontainers MySQL) |
| `ProjectHttpClientTest` | 404 → `EntityNotFoundException` 변환, 장애 시 fail-closed 503 |
| `ProjectDeletedKafkaListenerTest` | 이벤트 수신 시 owner 단위 삭제, 깨진 payload 무시 |
| `FileControllerTest` | `X-User-Id` 헤더 요구, Bean Validation(빈 `storedUrl`·`fileSize <= 0`), presign contentType 허용 목록 — `text/html`뿐 아니라 `image/*`·`image/svg+xml`처럼 **구체적이지 않거나 스크립트 실행 가능한 이미지 MIME도 거부**되는지 |

테스트하지 못한 중요 시나리오:

- **실제 S3와의 종단간 업로드.** 버킷/자격증명이 아직 없어(§10) presign → PUT → register 전체 흐름을 실제로 돌려본 적이 없다. presign 서명이 실제 S3에서 통과하는지는 미검증이다
- **`REVIEW`/`REWARD` 타입의 소유권 우회.** 검증이 아예 없어 테스트할 대상 자체가 없다 — 이 공백이 테스트 부재로도 드러난다
- 고아 오브젝트/고아 메타데이터가 실제로 쌓이는 시나리오와 그 영향

## 10. 현재 한계와 후속 과제

- **실제 S3 버킷/자격증명이 아직 연결되지 않았다.** 운영이 k8s 기반이라 GitHub Secrets → `cd.yml`(`kubectl create secret`) → Deployment `secretKeyRef` 흐름으로 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_S3_BUCKET`/`AWS_S3_CDN_BASE_URL`을 연결해야 동작한다(`payment-secrets`와 동일 패턴).
    - 대응 계획: 이슈 #354.
- **S3 오브젝트가 절대 지워지지 않는다.** 소프트 딜리트만 하고, 정리 배치도 버킷 lifecycle 정책도 없다 — 확인 결과 lifecycle 설정은 문서상 "향후 과제"로만 언급돼 있고 실제로 존재하지 않는다. 삭제할수록 스토리지 사용량이 단조 증가한다.
    - 대응 계획: 버킷 lifecycle 정책(가장 저렴) 또는 `deleted_at`이 일정 기간 지난 레코드의 오브젝트를 지우는 배치. 전자를 먼저 검토한다.
- **presign만 하고 register되지 않은 고아 오브젝트를 정리하는 경로가 없다.** 키에 `requesterId`가 들어 있어 추적은 가능하지만 자동 정리가 없다.
    - 대응 계획: 위 lifecycle 정책에 "생성 후 N일 지났고 DB에 대응 레코드가 없는 오브젝트" 규칙을 함께 넣는다.
- **`REVIEW`/`REWARD` 타입은 소유권 검증이 없다.** 인증만 되면 임의의 `ownerId`를 붙여 남의 후기·리워드에 파일을 등록할 수 있다. `PROJECT`만 `ProjectPort`로 확인한다.
    - 대응 계획: board-service에 `GET /internal/v1/reviews/{id}/author` 같은 내부 API가 필요하고, `REWARD`는 project-service에 리워드→창작자 확인 경로가 필요하다. `ProjectPort`처럼 타입별 포트를 추가하는 구조는 이미 잡혀 있다.
- **`REWARD` 타입은 삭제 연동도 없다.** 리워드가 삭제돼도 그 파일 메타데이터를 정리하는 이벤트 구독이 없다 — `project.deleted.v1`은 `PROJECT`만 처리한다.
    - 대응 계획: 리워드 삭제 이벤트를 project-service가 발행하고 file-service가 구독하도록 추가.
- **`(owner_type, owner_id)` 인덱스가 없다.** 조회의 주 경로인데 인덱스 선언이 없어 파일이 늘면 풀스캔이 된다.
    - 대응 계획: 복합 인덱스 추가. `deleted_at IS NULL`이 항상 붙으므로 그것까지 포함할지 함께 검토.
- **`register`에 멱등키가 없고 `storedUrl`에 유니크 제약도 없다.** 같은 오브젝트가 중복 등록될 수 있다.
    - 대응 계획: `storedUrl` 유니크 제약 추가가 가장 간단하다.
- **presigned PUT은 업로드 용량 상한을 URL에 걸 수 없다.** S3 스펙상 presigned POST policy로 바꿔야 가능한데, FE가 이미 PUT 흐름으로 구현을 끝낸 상태라 계약을 바꾸면 양쪽 재작업이 필요하다. 코드에도 `ponytail:` 주석으로 남겨져 있다.
    - 대응 계획: 남용이 관측되면 버킷 lifecycle 또는 CloudFront 단에서 크기 제한 추가.
- **조회 시 파일 수만큼 presign 연산이 돈다.** `batch` 조회는 owner 수만큼 곱해진다. 네트워크 호출은 없지만 CPU 비용은 선형이다.
    - 대응 계획: 실측 후 필요하면 URL 캐싱(만료 시간보다 짧게) 검토.
