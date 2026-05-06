---
description: "Use when mapping between Java objects (entity ↔ DTO, DTO ↔ domain model) with MapStruct. Covers basic mappings, qualifiers, nested objects, collections, Spring component model, and testing."
---

# MapStruct — Mapeamento de Objetos em Java

> MapStruct gera código de mapeamento em tempo de compilação — sem reflection em runtime, sem overhead.
> Requer Java 11+. Compatível com Spring Boot, Quarkus e Micronaut.

## Dependências (Maven)

```xml
<properties>
    <mapstruct.version>1.6.3</mapstruct.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${mapstruct.version}</version>
                    </path>
                    <!-- Se usar Lombok junto com MapStruct, Lombok DEVE vir antes -->
                    <!-- 
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok-mapstruct-binding</artifactId>
                        <version>0.2.0</version>
                    </path>
                    -->
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## Mapper básico

```java
// Entidade JPA
@Entity
public class User {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private UserRole role;
    // getters/setters ou construtor
}

// DTO de resposta (record — Java 17+)
public record UserResponse(UUID id, String fullName, String email, String role) {}

// DTO de criação
public record CreateUserRequest(@NotBlank String firstName,
                                 @NotBlank String lastName,
                                 @Email String email) {}
```

```java
@Mapper(componentModel = "spring")  // gera um @Component — injetável via construtor
public interface UserMapper {

    // Mapeamento de campo com nome diferente
    @Mapping(target = "fullName", expression = "java(user.getFirstName() + \" \" + user.getLastName())")
    @Mapping(target = "role", source = "role.name")  // enum → String via .name()
    UserResponse toResponse(User user);

    // Coleção — MapStruct gera automaticamente a partir do método de elemento único
    List<UserResponse> toResponseList(List<User> users);

    // Ignorar campos no destino que não existem na fonte
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    User toEntity(CreateUserRequest request);
}
```

**Regras de `@Mapper`:**
- `componentModel = "spring"` — gera bean Spring (`@Component`); use `"cdi"` para Quarkus/Micronaut CDI; `"jakarta"` para Jakarta CDI
- Sempre use `componentModel` — evite `Mappers.getMapper()` em código de produção (dificulta testes)
- Defina mapeamentos explícitos para campos com nomes diferentes ou transformações

---

## Mapeamento de objetos aninhados

```java
public record AddressResponse(String street, String city, String country) {}
public record OrderResponse(UUID id, OrderStatus status, AddressResponse shippingAddress) {}

@Mapper(componentModel = "spring", uses = AddressMapper.class)
public interface OrderMapper {

    // MapStruct delega Address → AddressResponse para AddressMapper automaticamente
    OrderResponse toResponse(Order order);
}

@Mapper(componentModel = "spring")
public interface AddressMapper {
    AddressResponse toResponse(Address address);
}
```

Para reutilizar mappers: use o atributo `uses = { OtherMapper.class }` — MapStruct compõe os mapeamentos.

---

## Atualização parcial (update in-place)

```java
@Mapper(componentModel = "spring")
public interface UserMapper {

    // Atualiza apenas os campos não-nulos do request na entidade existente
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(UpdateUserRequest request, @MappingTarget User user);
}
```

```java
// No service — aplica dirty checking do JPA automaticamente
@Transactional
public UserResponse update(UUID id, UpdateUserRequest request) {
    var user = repository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    mapper.updateFromRequest(request, user);   // modifica a entidade gerenciada
    return mapper.toResponse(user);            // dirty checking cuida do UPDATE
}
```

---

## Qualifiers — lógica de conversão reutilizável

```java
// Definir qualifier
@Qualifier
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface TrimAndLower {}

// Implementar em uma classe utilitária
@Component        // ou classe sem anotação — MapStruct usa pelo tipo
public class StringUtils {

    @TrimAndLower
    public String trimAndLower(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}

// Usar no mapper
@Mapper(componentModel = "spring", uses = StringUtils.class)
public interface ProductMapper {

    @Mapping(target = "slug", source = "name", qualifiedBy = TrimAndLower.class)
    ProductResponse toResponse(Product product);
}
```

---

## Enum para String e vice-versa

```java
// MapStruct converte Enum → String via name() por padrão
// Para mapeamento customizado use @ValueMapping

@Mapper(componentModel = "spring")
public interface OrderStatusMapper {

    @ValueMapping(source = "PENDING", target = "AWAITING_PAYMENT")
    @ValueMapping(source = "CONFIRMED", target = "IN_PROGRESS")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    String toApiStatus(OrderStatus status);
}
```

---

## Mapeamento com contexto / parâmetros extras

```java
@Mapper(componentModel = "spring")
public interface DocumentMapper {

    // Parâmetro extra passado pelo chamador — não é mapeado para nenhum campo
    @Mapping(target = "downloadUrl", expression = "java(buildUrl(document.getKey(), baseUrl))")
    DocumentResponse toResponse(Document document, @Context String baseUrl);

    default String buildUrl(String key, @Context String baseUrl) {
        return baseUrl + "/" + key;
    }
}

// Chamada
mapper.toResponse(document, "https://cdn.example.com");
```

---

## Integração com Spring e injeção de construtor

```java
// O mapper gerado é um @Component — injete normalmente
@Service
@Transactional(readOnly = true)
class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;

    UserService(UserRepository repository, UserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public UserResponse findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
```

---

## Testes

```java
// Teste unitário — não precisa de contexto Spring
class UserMapperTest {

    // Para componentModel = "spring", use a implementação gerada diretamente
    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);
    // Ou, se o mapper usa 'uses', instancie manualmente:
    // private final UserMapper mapper = new UserMapperImpl();

    @Test
    void shouldMapFullName() {
        var user = new User();
        user.setFirstName("Ana");
        user.setLastName("Silva");
        user.setEmail("ana@example.com");

        var response = mapper.toResponse(user);

        assertThat(response.fullName()).isEqualTo("Ana Silva");
        assertThat(response.email()).isEqualTo("ana@example.com");
    }

    @Test
    void shouldIgnoreIdOnCreate() {
        var request = new CreateUserRequest("Ana", "Silva", "ana@example.com");
        var entity = mapper.toEntity(request);
        assertThat(entity.getId()).isNull();
    }
}
```

Para mappers com `uses`, prefira `@SpringBootTest` ou instancie a implementação gerada (`new UserMapperImpl(new AddressMapperImpl())`).

---

## Regras

- Sempre `componentModel = "spring"` (ou `"jakarta"` / `"cdi"`) — nunca `Mappers.getMapper()` em produção
- Declare `@Mapping(target = "...", ignore = true)` explicitamente para campos sem correspondência — evita surpresas com campos novos na entidade
- Use `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` somente em métodos de update parcial (PATCH)
- Nunca coloque lógica de negócio dentro do mapper — use `@AfterMapping` ou qualifiers somente para transformações de dados (trim, format, encode)
- Se usar Lombok: `lombok-mapstruct-binding` é obrigatório e Lombok deve vir **antes** do MapStruct no `annotationProcessorPaths`
- Verifique warnings do compilador — MapStruct emite `[WARNING] Unmapped target property` para campos ignorados implicitamente; trate como erro com `unmappedTargetPolicy = ReportingPolicy.ERROR` em produção
