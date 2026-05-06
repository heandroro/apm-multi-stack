---
description: "Use when building Django applications in Python. Covers models, views, Django REST Framework serializers, viewsets, authentication, and testing with pytest-django."
---

# Django — Padrões

## Models

- Use `UUIDField` como primary key explícita
- Use `auto_now_add` / `auto_now` para timestamps
- Defina `__str__` e `class Meta` em todos os models
- Use `related_name` descritivo em ForeignKeys

```python
import uuid
from django.db import models

class User(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    name = models.CharField(max_length=100)
    email = models.EmailField(unique=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "users"
        ordering = ["name"]

    def __str__(self) -> str:
        return f"{self.name} <{self.email}>"
```

## Django REST Framework — Serializers

```python
from rest_framework import serializers

class CreateUserSerializer(serializers.Serializer):
    name = serializers.CharField(max_length=100)
    email = serializers.EmailField()

    def validate_name(self, value: str) -> str:
        if not value.strip():
            raise serializers.ValidationError("Name cannot be blank.")
        return value.strip()

class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ["id", "name", "email", "created_at"]
        read_only_fields = ["id", "created_at"]
```

## ViewSets com DRF

```python
from rest_framework import viewsets, status
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

class UserViewSet(viewsets.ViewSet):
    permission_classes = [IsAuthenticated]

    def list(self, request):
        users = User.objects.filter(is_active=True)
        serializer = UserSerializer(users, many=True)
        return Response(serializer.data)

    def retrieve(self, request, pk=None):
        try:
            user = User.objects.get(pk=pk, is_active=True)
        except User.DoesNotExist:
            return Response({"code": "NOT_FOUND"}, status=status.HTTP_404_NOT_FOUND)
        return Response(UserSerializer(user).data)

    def create(self, request):
        serializer = CreateUserSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        if User.objects.filter(email=serializer.validated_data["email"]).exists():
            return Response({"code": "EMAIL_TAKEN"}, status=status.HTTP_409_CONFLICT)

        user = User.objects.create(**serializer.validated_data)
        return Response(UserSerializer(user).data, status=status.HTTP_201_CREATED)
```

## Registro de URLs

```python
# urls.py
from django.urls import path, include
from rest_framework.routers import DefaultRouter

router = DefaultRouter()
router.register("users", UserViewSet, basename="user")

urlpatterns = [
    path("api/v1/", include(router.urls)),
]
```

## Managers customizados

```python
class ActiveUserManager(models.Manager):
    def get_queryset(self):
        return super().get_queryset().filter(is_active=True)

class User(models.Model):
    # ...
    objects = models.Manager()    # default
    active = ActiveUserManager()  # filtrado
```

## Testes com pytest-django

```python
import pytest
from django.test import Client

@pytest.mark.django_db
def test_create_user(client: Client):
    response = client.post(
        "/api/v1/users/",
        {"name": "Bob", "email": "bob@example.com"},
        content_type="application/json",
    )
    assert response.status_code == 201
    assert response.json()["name"] == "Bob"

@pytest.mark.django_db
def test_create_user_duplicate_email(client, existing_user):
    response = client.post(
        "/api/v1/users/",
        {"name": "Bob2", "email": existing_user.email},
        content_type="application/json",
    )
    assert response.status_code == 409
```
