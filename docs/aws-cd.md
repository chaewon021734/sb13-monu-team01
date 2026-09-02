# AWS ECS CD 설정

이 프로젝트는 GitHub Actions에서 Docker 이미지를 ECR에 푸시한 뒤 ECS 서비스에 배포합니다. `dev` 브랜치는 dev 환경, `main` 브랜치는 prod 환경으로 분리해서 운영합니다.

## 1. 환경 분리 원칙

- `dev` 브랜치: dev ECS 리소스와 dev 데이터 저장소를 사용합니다.
- `main` 브랜치: prod ECS 리소스와 prod 데이터 저장소를 사용합니다.
- RDS, MongoDB, S3, 배치 수동 실행 secret은 dev/prod 값을 섞지 않습니다.
- GitHub Actions secret은 Repository secrets에 dev 기본값과 `PROD_` 접두사 prod 값을 분리해서 등록합니다.

권장 prod 리소스 예시:

- ECS Cluster: `monu-prod-cluster`
- ECS Service: `monu-prod-service`
- ALB: `monu-prod-alb`
- Article backup S3 bucket: prod 전용 버킷
- Log archive S3 prefix 또는 bucket: prod 전용 값
- RDS PostgreSQL: prod 전용 DB 또는 schema
- MongoDB: prod 전용 database/cluster

## 2. GitHub Repository Secrets

GitHub repository settings의 `Secrets and variables` -> `Actions`에 아래 secret을 등록합니다.

dev 배포용 기본 Secrets:

- `AWS_ROLE_TO_ASSUME`: GitHub Actions OIDC로 assume할 IAM Role ARN
- `AWS_REGION`: 예: `ap-northeast-2`
- `ECR_REPOSITORY`: ECR repository 이름
- `ECS_CLUSTER`: 환경별 ECS cluster 이름
- `ECS_SERVICE`: 환경별 ECS service 이름
- `ECS_TASK_DEFINITION`: 환경별 ECS task definition family 또는 ARN
- `ECS_CONTAINER_NAME`: task definition 안의 애플리케이션 container 이름

prod 배포용 Secrets:

| Secret | 값 예시 |
| --- | --- |
| `PROD_AWS_ROLE_TO_ASSUME` | `arn:aws:iam::263011181084:role/monew-github-actions-deploy-role` |
| `PROD_AWS_REGION` | `ap-northeast-2` |
| `PROD_ECR_REPOSITORY` | `monu-app` |
| `PROD_ECS_CLUSTER` | `monu-prod-cluster` |
| `PROD_ECS_SERVICE` | `monu-prod-service` |
| `PROD_ECS_TASK_DEFINITION` | `monu-prod-task` |
| `PROD_ECS_CONTAINER_NAME` | `monu-app` |

예시:

| 용도 | Secret | 값 예시 |
| --- | --- | --- |
| dev | `ECS_CLUSTER` | `monu-dev-cluster` |
| dev | `ECS_SERVICE` | `monu-dev-service` |
| dev | `ECS_TASK_DEFINITION` | `monu-dev-task` |
| dev | `ECS_CONTAINER_NAME` | `monu-app` |
| prod | `PROD_ECS_CLUSTER` | `monu-prod-cluster` |
| prod | `PROD_ECS_SERVICE` | `monu-prod-service` |
| prod | `PROD_ECS_TASK_DEFINITION` | `monu-prod-task` |
| prod | `PROD_ECS_CONTAINER_NAME` | `monu-app` |

Repository settings 권한 때문에 GitHub Environment를 만들 수 없는 경우에도 이 방식으로 dev/prod 배포 대상을 분리할 수 있습니다.

## 3. ECS Task Definition 환경값

애플리케이션은 `prod` Spring profile에서 환경변수를 읽습니다. dev/prod task definition 또는 ECS secret 참조를 각각 분리해 설정합니다.

필수 환경값:

```env
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_DB_HOST:5432/YOUR_DB_NAME
SPRING_DATASOURCE_USERNAME=YOUR_DB_USER
SPRING_DATASOURCE_PASSWORD=YOUR_DB_PASSWORD
MONGODB_URI=mongodb://YOUR_MONGO_HOST:27017/YOUR_DB_NAME
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_BATCH_JOB_ENABLED=false
BATCH_ENABLED=true
BATCH_SCHEDULER_ENABLED=true
BATCH_MANUAL_SECRET=YOUR_BATCH_MANUAL_SECRET
AWS_S3_BUCKET=YOUR_ARTICLE_BACKUP_BUCKET
AWS_REGION=ap-northeast-2
LOG_ARCHIVE_STORAGE=s3
LOG_ARCHIVE_S3_PREFIX=logs
NAVER_CLIENT_ID=YOUR_NAVER_CLIENT_ID
NAVER_CLIENT_SECRET=YOUR_NAVER_CLIENT_SECRET
SERVER_PORT=8080
JAVA_OPTS=-Xms256m -Xmx512m
```

민감한 값은 task definition의 plain environment가 아니라 AWS Secrets Manager 또는 SSM Parameter Store 참조로 넣는 것을 권장합니다.

## 4. 배포 동작

`.github/workflows/cd.yml`은 아래처럼 동작합니다.

- `dev` push: 기본 Repository secret을 사용해 dev ECS 서비스에 배포합니다.
- `main` push: `PROD_` 접두사 Repository secret을 사용해 prod ECS 서비스에 배포합니다.
- 수동 실행도 선택된 브랜치가 `dev` 또는 `main`일 때만 배포됩니다.
- Docker image는 commit SHA 기반으로 `dev-<sha>` 또는 `prod-<sha>` 태그를 사용합니다.
- 보조 태그로 `dev-latest`, `prod-latest`를 푸시합니다.

## 5. GitHub Actions Role 최소 권한 예시

환경별 ECS 리소스 ARN으로 좁혀서 권한을 부여하는 것을 권장합니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:CompleteLayerUpload",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart"
      ],
      "Resource": "arn:aws:ecr:ap-northeast-2:YOUR_ACCOUNT_ID:repository/YOUR_ECR_REPOSITORY"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecs:DescribeServices",
        "ecs:DescribeTaskDefinition",
        "ecs:RegisterTaskDefinition",
        "ecs:UpdateService"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "iam:PassRole"
      ],
      "Resource": [
        "arn:aws:iam::YOUR_ACCOUNT_ID:role/YOUR_ECS_TASK_ROLE",
        "arn:aws:iam::YOUR_ACCOUNT_ID:role/YOUR_ECS_EXECUTION_ROLE"
      ]
    }
  ]
}
```

## 6. 배포 실패 점검

배포가 멈추거나 롤백되면 먼저 아래 항목을 확인합니다.

```bash
aws ecs describe-services --cluster YOUR_CLUSTER --services YOUR_SERVICE
aws ecs describe-tasks --cluster YOUR_CLUSTER --tasks YOUR_TASK_ID
aws logs tail /aws/ecs/YOUR_LOG_GROUP --since 30m
```

애플리케이션이 시작 직후 종료되면 ECS task definition의 필수 환경값과 secret 참조를 확인합니다. 최소한 `MONGODB_URI`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `AWS_S3_BUCKET`, `BATCH_MANUAL_SECRET`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`은 환경별 실제 값으로 채워져야 합니다.
