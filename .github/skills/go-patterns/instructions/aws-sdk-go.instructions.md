---
description: "Use when integrating Go services with AWS. Covers AWS SDK for Go v2 (github.com/aws/aws-sdk-go-v2) for S3, SQS, SNS, DynamoDB, Secrets Manager, EventBridge and Parameter Store. Idiomatic error handling, context propagation, and LocalStack testing."
---

# AWS SDK for Go v2 — Boas Práticas

> Use sempre o **SDK v2** (`github.com/aws/aws-sdk-go-v2`). O SDK v1 (`github.com/aws/aws-sdk-go`) está em modo de manutenção desde 2023.
> O SDK v2 é idiomatic Go: erros explícitos, `context.Context` em toda operação, sem globals.

## Dependências

```bash
go get github.com/aws/aws-sdk-go-v2
go get github.com/aws/aws-sdk-go-v2/config
go get github.com/aws/aws-sdk-go-v2/service/s3
go get github.com/aws/aws-sdk-go-v2/service/sqs
go get github.com/aws/aws-sdk-go-v2/service/sns
go get github.com/aws/aws-sdk-go-v2/service/dynamodb
go get github.com/aws/aws-sdk-go-v2/service/secretsmanager
go get github.com/aws/aws-sdk-go-v2/service/eventbridge
go get github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue
```

## Configuração de credenciais e região

O SDK usa a **default credential provider chain**:
1. Variáveis de ambiente `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_REGION`
2. `~/.aws/credentials` (desenvolvimento local)
3. IAM Role (EC2 instance profile / ECS task role / Lambda) — **preferido em produção**

```go
// main.go ou wire setup
cfg, err := config.LoadDefaultConfig(ctx,
    config.WithRegion("us-east-1"),
)
if err != nil {
    return fmt.Errorf("load aws config: %w", err)
}

s3Client    := s3.NewFromConfig(cfg)
sqsClient   := sqs.NewFromConfig(cfg)
snsClient   := sns.NewFromConfig(cfg)
dynamoClient := dynamodb.NewFromConfig(cfg)
```

Para **LocalStack** em testes:

```go
cfg, err := config.LoadDefaultConfig(ctx,
    config.WithRegion("us-east-1"),
    config.WithCredentialsProvider(credentials.NewStaticCredentialsProvider("test", "test", "")),
    config.WithEndpointResolverWithOptions(
        aws.EndpointResolverWithOptionsFunc(func(service, region string, opts ...interface{}) (aws.Endpoint, error) {
            return aws.Endpoint{URL: "http://localhost:4566", HostnameImmutable: true}, nil
        }),
    ),
)
```

> **Regra de segurança**: nunca use `credentials.NewStaticCredentialsProvider` fora de testes.

---

## S3

```go
type S3Storage struct {
    client *s3.Client
    bucket string
}

func NewS3Storage(client *s3.Client, bucket string) *S3Storage {
    return &S3Storage{client: client, bucket: bucket}
}

func (s *S3Storage) Upload(ctx context.Context, key string, body io.Reader, contentType string) error {
    _, err := s.client.PutObject(ctx, &s3.PutObjectInput{
        Bucket:      aws.String(s.bucket),
        Key:         aws.String(key),
        Body:        body,
        ContentType: aws.String(contentType),
    })
    if err != nil {
        return fmt.Errorf("s3 upload %s: %w", key, err)
    }
    return nil
}

func (s *S3Storage) Download(ctx context.Context, key string) (io.ReadCloser, error) {
    output, err := s.client.GetObject(ctx, &s3.GetObjectInput{
        Bucket: aws.String(s.bucket),
        Key:    aws.String(key),
    })
    if err != nil {
        return nil, fmt.Errorf("s3 download %s: %w", key, err)
    }
    return output.Body, nil
}

func (s *S3Storage) Delete(ctx context.Context, key string) error {
    _, err := s.client.DeleteObject(ctx, &s3.DeleteObjectInput{
        Bucket: aws.String(s.bucket),
        Key:    aws.String(key),
    })
    if err != nil {
        return fmt.Errorf("s3 delete %s: %w", key, err)
    }
    return nil
}

// Presigned URL para download temporário
func (s *S3Storage) PresignDownload(ctx context.Context, key string, ttl time.Duration) (string, error) {
    presigner := s3.NewPresignClient(s.client)
    req, err := presigner.PresignGetObject(ctx, &s3.GetObjectInput{
        Bucket: aws.String(s.bucket),
        Key:    aws.String(key),
    }, s3.WithPresignExpires(ttl))
    if err != nil {
        return "", fmt.Errorf("presign %s: %w", key, err)
    }
    return req.URL, nil
}
```

---

## SQS

```go
type SQSPublisher struct {
    client   *sqs.Client
    queueURL string
}

func (p *SQSPublisher) Send(ctx context.Context, body string) error {
    _, err := p.client.SendMessage(ctx, &sqs.SendMessageInput{
        QueueUrl:    aws.String(p.queueURL),
        MessageBody: aws.String(body),
    })
    if err != nil {
        return fmt.Errorf("sqs send: %w", err)
    }
    return nil
}

// Consumidor com long polling — use em goroutine dedicada
func (p *SQSPublisher) Poll(ctx context.Context, handler func(msg types.Message) error) error {
    for {
        select {
        case <-ctx.Done():
            return ctx.Err()
        default:
        }

        output, err := p.client.ReceiveMessage(ctx, &sqs.ReceiveMessageInput{
            QueueUrl:            aws.String(p.queueURL),
            MaxNumberOfMessages: 10,
            WaitTimeSeconds:     20,  // long polling
        })
        if err != nil {
            return fmt.Errorf("sqs receive: %w", err)
        }

        for _, msg := range output.Messages {
            if err := handler(msg); err != nil {
                // não deleta — mensagem retorna após visibility timeout
                continue
            }
            if _, err := p.client.DeleteMessage(ctx, &sqs.DeleteMessageInput{
                QueueUrl:      aws.String(p.queueURL),
                ReceiptHandle: msg.ReceiptHandle,
            }); err != nil {
                return fmt.Errorf("sqs delete: %w", err)
            }
        }
    }
}
```

---

## SNS

```go
type SNSPublisher struct {
    client   *sns.Client
    topicARN string
}

func (p *SNSPublisher) Publish(ctx context.Context, message, subject string) error {
    _, err := p.client.Publish(ctx, &sns.PublishInput{
        TopicArn: aws.String(p.topicARN),
        Message:  aws.String(message),
        Subject:  aws.String(subject),
    })
    if err != nil {
        return fmt.Errorf("sns publish: %w", err)
    }
    return nil
}
```

---

## DynamoDB

```go
// Struct mapeada com tags `dynamodbav`
type OrderItem struct {
    OrderID     string `dynamodbav:"orderId"`
    ItemID      string `dynamodbav:"itemId"`
    ProductName string `dynamodbav:"productName"`
    Quantity    int    `dynamodbav:"quantity"`
}

type OrderItemRepository struct {
    client    *dynamodb.Client
    tableName string
}

func (r *OrderItemRepository) Save(ctx context.Context, item OrderItem) error {
    av, err := attributevalue.MarshalMap(item)
    if err != nil {
        return fmt.Errorf("marshal order item: %w", err)
    }
    _, err = r.client.PutItem(ctx, &dynamodb.PutItemInput{
        TableName: aws.String(r.tableName),
        Item:      av,
    })
    if err != nil {
        return fmt.Errorf("dynamodb put: %w", err)
    }
    return nil
}

func (r *OrderItemRepository) FindByID(ctx context.Context, orderID, itemID string) (*OrderItem, error) {
    output, err := r.client.GetItem(ctx, &dynamodb.GetItemInput{
        TableName: aws.String(r.tableName),
        Key: map[string]dtypes.AttributeValue{
            "orderId": &dtypes.AttributeValueMemberS{Value: orderID},
            "itemId":  &dtypes.AttributeValueMemberS{Value: itemID},
        },
    })
    if err != nil {
        return nil, fmt.Errorf("dynamodb get: %w", err)
    }
    if output.Item == nil {
        return nil, nil  // not found — retorne nil, nil (sem ErrNotFound genérico)
    }
    var item OrderItem
    if err := attributevalue.UnmarshalMap(output.Item, &item); err != nil {
        return nil, fmt.Errorf("unmarshal order item: %w", err)
    }
    return &item, nil
}
```

---

## Secrets Manager

```go
type SecretsService struct {
    client *secretsmanager.Client
}

func (s *SecretsService) GetSecret(ctx context.Context, secretID string) (string, error) {
    output, err := s.client.GetSecretValue(ctx, &secretsmanager.GetSecretValueInput{
        SecretId: aws.String(secretID),
    })
    if err != nil {
        return "", fmt.Errorf("get secret %s: %w", secretID, err)
    }
    if output.SecretString == nil {
        return "", fmt.Errorf("secret %s is binary — not supported", secretID)
    }
    return *output.SecretString, nil
}

// Desserializa JSON do secret para uma struct
func GetSecretAs[T any](ctx context.Context, svc *SecretsService, secretID string) (T, error) {
    var zero T
    raw, err := svc.GetSecret(ctx, secretID)
    if err != nil {
        return zero, err
    }
    var result T
    if err := json.Unmarshal([]byte(raw), &result); err != nil {
        return zero, fmt.Errorf("unmarshal secret %s: %w", secretID, err)
    }
    return result, nil
}
```

---

## EventBridge

```go
type EventBus struct {
    client       *eventbridge.Client
    eventBusName string
}

func (b *EventBus) Publish(ctx context.Context, source, detailType, detail string) error {
    _, err := b.client.PutEvents(ctx, &eventbridge.PutEventsInput{
        Entries: []ebtypes.PutEventsRequestEntry{
            {
                EventBusName: aws.String(b.eventBusName),
                Source:       aws.String(source),
                DetailType:   aws.String(detailType),
                Detail:       aws.String(detail),
            },
        },
    })
    if err != nil {
        return fmt.Errorf("eventbridge put events: %w", err)
    }
    return nil
}
```

---

## Tratamento de erros AWS

```go
import "github.com/aws/smithy-go"

// Inspecione o tipo de erro para decidir se é retryable
var apiErr smithy.APIError
if errors.As(err, &apiErr) {
    switch apiErr.ErrorCode() {
    case "NoSuchKey":
        return ErrNotFound
    case "ProvisionedThroughputExceededException":
        return ErrThrottled  // candidato a retry com backoff
    default:
        return fmt.Errorf("aws error %s: %s", apiErr.ErrorCode(), apiErr.ErrorMessage())
    }
}
```

---

## Testes com LocalStack + Testcontainers

```go
func TestS3Upload(t *testing.T) {
    ctx := context.Background()

    container, err := localstack.Run(ctx, "localstack/localstack:3",
        testcontainers.WithEnv(map[string]string{"SERVICES": "s3"}),
    )
    require.NoError(t, err)
    t.Cleanup(func() { _ = container.Terminate(ctx) })

    endpoint, err := container.Endpoint(ctx, "")
    require.NoError(t, err)

    cfg, err := config.LoadDefaultConfig(ctx,
        config.WithRegion("us-east-1"),
        config.WithCredentialsProvider(
            credentials.NewStaticCredentialsProvider("test", "test", ""),
        ),
        config.WithEndpointResolverWithOptions(
            aws.EndpointResolverWithOptionsFunc(func(svc, region string, _ ...interface{}) (aws.Endpoint, error) {
                return aws.Endpoint{URL: "http://" + endpoint, HostnameImmutable: true}, nil
            }),
        ),
    )
    require.NoError(t, err)

    client := s3.NewFromConfig(cfg, func(o *s3.Options) { o.UsePathStyle = true })
    _, err = client.CreateBucket(ctx, &s3.CreateBucketInput{Bucket: aws.String("test-bucket")})
    require.NoError(t, err)

    storage := NewS3Storage(client, "test-bucket")
    err = storage.Upload(ctx, "hello.txt", strings.NewReader("hello"), "text/plain")
    require.NoError(t, err)
}
```

---

## Regras de segurança

- Nunca use `credentials.NewStaticCredentialsProvider` fora de testes com LocalStack
- Passe sempre `context.Context` — permite deadline/cancellation e rastreamento distribuído
- Envolva todos os erros AWS com `fmt.Errorf("...: %w", err)` — preserve a chain para `errors.As`
- Nunca logue o conteúdo de secrets — logue apenas o `SecretId`
- Feche `io.ReadCloser` do `GetObject` após consumir o corpo — evita leak de conexão
