---
description: "Use when implementing gRPC services in Go with protobuf. Covers .proto file conventions, server/client implementation, interceptors, error handling with status codes, and testing."
---

# gRPC + Protobuf em Go — Padrões

## Estrutura do projeto

```
myservice/
├── proto/
│   └── user/
│       └── v1/
│           └── user.proto
├── gen/
│   └── user/
│       └── v1/
│           ├── user.pb.go         # gerado
│           └── user_grpc.pb.go    # gerado
├── internal/
│   └── user/
│       ├── server.go
│       └── server_test.go
└── cmd/
    └── server/
        └── main.go
```

## Convenções .proto

```protobuf
syntax = "proto3";

package user.v1;

option go_package = "github.com/myorg/myservice/gen/user/v1;userv1";

import "google/protobuf/timestamp.proto";
import "google/protobuf/empty.proto";

service UserService {
  rpc CreateUser(CreateUserRequest) returns (CreateUserResponse);
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
  rpc ListUsers(ListUsersRequest) returns (stream User);
  rpc DeleteUser(DeleteUserRequest) returns (google.protobuf.Empty);
}

message User {
  string id    = 1;
  string name  = 2;
  string email = 3;
  google.protobuf.Timestamp created_at = 4;
}

message CreateUserRequest {
  string name  = 1;
  string email = 2;
}

message CreateUserResponse { User user = 1; }
message GetUserRequest     { string id = 1; }
message GetUserResponse    { User user = 1; }
message ListUsersRequest   {}
message DeleteUserRequest  { string id = 1; }
```

## Geração de código

```bash
# buf (recomendado)
buf generate

# ou protoc direto
protoc --go_out=gen --go_opt=paths=source_relative \
       --go-grpc_out=gen --go-grpc_opt=paths=source_relative \
       proto/user/v1/user.proto
```

## Implementação do servidor

```go
package user

import (
    "context"
    "errors"

    "google.golang.org/grpc/codes"
    "google.golang.org/grpc/status"

    userv1 "github.com/myorg/myservice/gen/user/v1"
)

type Server struct {
    userv1.UnimplementedUserServiceServer  // sempre embed
    service UserService
}

func NewServer(service UserService) *Server {
    return &Server{service: service}
}

func (s *Server) GetUser(ctx context.Context, req *userv1.GetUserRequest) (*userv1.GetUserResponse, error) {
    user, err := s.service.FindByID(ctx, req.Id)
    if err != nil {
        return nil, toGRPCStatus(err)
    }
    return &userv1.GetUserResponse{User: toProto(user)}, nil
}

func (s *Server) ListUsers(req *userv1.ListUsersRequest, stream userv1.UserService_ListUsersServer) error {
    users, err := s.service.FindAll(stream.Context())
    if err != nil {
        return toGRPCStatus(err)
    }
    for _, u := range users {
        if err := stream.Send(toProto(u)); err != nil {
            return err
        }
    }
    return nil
}
```

## Mapeamento de erros para gRPC status codes

```go
func toGRPCStatus(err error) error {
    var notFound *NotFoundError
    switch {
    case errors.As(err, &notFound):
        return status.Errorf(codes.NotFound, err.Error())
    case errors.Is(err, ErrAlreadyExists):
        return status.Errorf(codes.AlreadyExists, err.Error())
    case errors.Is(err, ErrInvalidArgument):
        return status.Errorf(codes.InvalidArgument, err.Error())
    default:
        return status.Errorf(codes.Internal, "internal error")
    }
}
```

## Interceptors (middleware)

```go
func loggingInterceptor(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
    start := time.Now()
    resp, err := handler(ctx, req)
    slog.Info("grpc request",
        "method", info.FullMethod,
        "latency", time.Since(start),
        "error", err,
    )
    return resp, err
}

// Registrar
grpc.NewServer(
    grpc.UnaryInterceptor(loggingInterceptor),
    grpc.StreamInterceptor(loggingStreamInterceptor),
)
```

## Testes

```go
func TestGetUser(t *testing.T) {
    service := &mockUserService{user: &User{ID: "1", Name: "Alice"}}
    server := NewServer(service)

    resp, err := server.GetUser(context.Background(), &userv1.GetUserRequest{Id: "1"})
    require.NoError(t, err)
    assert.Equal(t, "Alice", resp.User.Name)
}

// Com servidor real para testes de integração
func newTestServer(t *testing.T) userv1.UserServiceClient {
    t.Helper()
    srv := grpc.NewServer()
    userv1.RegisterUserServiceServer(srv, NewServer(/* deps */))
    lis, _ := net.Listen("tcp", "localhost:0")
    go srv.Serve(lis)
    t.Cleanup(func() { srv.GracefulStop() })
    conn, _ := grpc.Dial(lis.Addr().String(), grpc.WithTransportCredentials(insecure.NewCredentials()))
    return userv1.NewUserServiceClient(conn)
}
```
