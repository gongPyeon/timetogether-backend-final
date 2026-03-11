<div align="center">

<img src="https://user-images.githubusercontent.com/80824750/208554611-f8277015-12e8-48d2-b2cc-d09d67f03c02.png" width="400"/>

### TimeTogether (밋나우) - Backend

**DB가 탈취되어도 사용자 관계를 알 수 없는, 프라이버시 중심 그룹 약속 조율 플랫폼**

[<img src="https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk&logoColor=white"/>]()
[<img src="https://img.shields.io/badge/Spring Boot-3.4.1-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>]()
[<img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white"/>]()
[<img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white"/>]()
[<img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white"/>]()
[<img src="https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white"/>]()

</div>

<br/>

## 프로젝트 소개

TimeTogether는 그룹 내에서 약속 일정과 장소를 협업적으로 조율할 수 있는 플랫폼입니다.

단순한 일정 관리 서비스와 차별화되는 핵심 가치는 **다단계 암호화 아키텍처**입니다. DB가 탈취되더라도 "누가 어떤 그룹에 속해있는지", "누가 어떤 약속에 참여하는지"를 알 수 없도록 설계했습니다.

### 주요 기능
- **그룹 관리** - 그룹 생성/조회/편집, 멤버 관리
- **약속 조율** - 약속 생성, 참여자 관리, 상태 추적(시간/장소/확정)
- **시간 투표** - 참여자별 가능 시간 입력, 겹치는 시간대 조회, 개인 캘린더 연동
- **장소 투표** - 장소 제안(최대 5개), 투표, 별점 평가, AI 기반 장소 추천
- **소셜 로그인** - Google / Naver / Kakao OAuth2 + 일반 로그인

<br/>

## 핵심 기술적 도전

### 1. 다단계 암호화 아키텍처

> **문제**: 일반적인 설계에서는 DB가 유출되면 `user_id → group_id → promise_id` 관계가 그대로 노출됩니다.

> **해결**: 3계층 키 구조 + Proxy 테이블로 사용자-리소스 매핑을 암호화했습니다.

```
[사용자 개인키]  →  [그룹/약속 암호화키]  →  [암호화된 ID 매핑]
   (클라이언트)        (ShareKey 테이블)       (ProxyUser 테이블)
```

| 구성 요소 | 역할 |
|:---:|:---|
| `GroupProxyUser` / `PromiseProxyUser` | 사용자-리소스 매핑을 암호화하여 저장 |
| `GroupShareKey` / `PromiseShareKey` | 암호화된 그룹/약속 키를 사용자별로 분배 |
| **Multi-Step API** | 그룹 조회 3단계, 약속 생성 4단계로 키 교환 수행 |

**성과**:
- 평문 ID 노출 경로 **100% 제거**
- 4단계 약속 생성 API 평균 응답시간 **5.04ms** (단일 API 대비 x1.9)
- 100명 동시 접속 부하 테스트: 평균 **126.9ms**, 에러율 **0%**, **1,165 TPS**

<br/>

### 2. JWT + OAuth2 인증 체계

```
[ExceptionHandlerFilter] → [JwtAuthenticationFilter] → [Spring Security Filters]
```

- **Dual Token 전략**: Access Token(헤더) + Refresh Token(HttpOnly Cookie)
- **Redis 기반 토큰 블랙리스트**: Stateless JWT 환경에서 안전한 로그아웃 구현
- **로그인 시도 제한**: 반복 실패 시 계정 잠금
- **OAuth2 팩토리 패턴**: `OAuthClientFactory`로 3개 소셜 프로바이더 유연하게 확장

<br/>

### 3. 필드 레벨 암호화

사용자 민감 정보(이메일, 전화번호, 프로필 이미지)에 **AES + IV(Initialization Vector)** 적용으로 동일한 평문이라도 매번 다른 암호문을 생성합니다.

<br/>

## 기술 스택

| 영역 | 기술 |
|:---:|:---|
| **Language & Framework** | Java 17, Spring Boot 3.4.1, Spring Security 6.x, Spring Data JPA |
| **Database** | MySQL 8.0, Redis (Lettuce), QueryDSL 5.0.0 |
| **Authentication** | JWT (JJWT 0.11.5), OAuth2 Client (Google, Naver, Kakao), BCrypt |
| **Infra & Deploy** | AWS EC2, RDS, S3, Docker, Nginx |
| **Documentation** | SpringDoc OpenAPI 3.0 (Swagger), Spring REST Docs |
| **Testing** | JUnit 5, Mockito, MockMvc |

<br/>

## 프로젝트 구조

```
src/main/java/timetogeter/
├── context/                    # 도메인 기반 모듈
│   ├── auth/                   # 인증 (회원가입, 로그인, OAuth2)
│   ├── group/                  # 그룹 관리 (생성, 조회, 멤버)
│   ├── promise/                # 약속 조율 (생성, 키교환, 상태관리)
│   ├── place/                  # 장소 (제안, 투표, 별점, AI추천)
│   ├── time/                   # 시간 (가용시간, 투표, 확정)
│   └── schedule/               # 일정 (확정된 약속 캘린더)
└── global/                     # 공통 모듈
    ├── security/               # JWT 필터, OAuth2, 인증 설정
    ├── config/                 # Redis, QueryDSL, Swagger 설정
    ├── interceptor/            # 요청/응답 처리
    └── common/                 # 유틸리티, 예외 처리
```

각 도메인 모듈은 **Presentation(Controller) - Application(Service, DTO) - Domain(Entity, Repository)** 3계층으로 구성됩니다.

<br/>

## 프로젝트 규모

| 항목 | 수치 |
|:---:|:---:|
| Java 파일 | 337개 |
| 코드 라인 | 14,188 LOC |
| API 엔드포인트 | 72개 |
| 엔티티 | 32개 |
| 커스텀 예외 | 21개 |
| 테스트 메서드 | 42개 |

<br/>
