# Changelog - Implementación de `isActive` en Product

**Fecha**: 2026-02-10  
**Objetivo**: Agregar el campo `isActive` al modelo de dominio `Product` y actualizar todos los mappers correspondientes para evitar pérdida de datos durante la sincronización.

---

## Problema Resuelto

Anteriormente, el campo `isActive` existía en:
- ✅ Base de datos local (Room - `ProductEntity`)
- ✅ Base de datos remota (Firestore - `RemoteProduct`)
- ❌ Modelo de dominio (`Product`)

Esto causaba que al transformar de Entidad a Dominio y viceversa, el estado `isActive` se perdiera y se reseteara siempre a `true`.

---

## Archivos Modificados

### 1. `app/src/main/java/com/are/distribuidora/domain/model/Product.kt`

**Línea modificada**: 22  
**Cambio**: Agregado el campo `isActive: Boolean = true`

**Antes**:
```kotlin
data class Product(
    val id: ProductId,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val price: Money,
    val imageUrl: String? = null,
    val barcode: String? = null,
    val stock: Quantity,
    val isDeleted: Boolean = false,
    val syncStatus: com.are.distribuidora.data.local.SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long? = null,
) {
```

**Después**:
```kotlin
data class Product(
    val id: ProductId,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val price: Money,
    val imageUrl: String? = null,
    val barcode: String? = null,
    val stock: Quantity,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val syncStatus: com.are.distribuidora.data.local.SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long? = null,
) {
```

---

### 2. `app/src/main/java/com/are/distribuidora/data/mapper/ProductMapper.kt`

**Cambios realizados**:

#### 2.1. Función `ProductEntity.toDomainOrNull()`
**Línea modificada**: 48  
**Cambio**: Agregado mapeo de `isActive = this.isActive`

**Antes**:
```kotlin
Product(
    id = ProductId.of(normalizedId),
    name = normalizedName,
    price = Money.of(price.toBigDecimal()),
    stock = Quantity.of(safeStock),
    description = this.description,
    category = this.category,
    imageUrl = this.imageUrl,
    barcode = this.barcode,
    isDeleted = this.isDeleted,
    syncStatus = this.syncStatus,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    lastSyncedAt = this.lastSyncedAt
)
```

**Después**:
```kotlin
Product(
    id = ProductId.of(normalizedId),
    name = normalizedName,
    price = Money.of(price.toBigDecimal()),
    stock = Quantity.of(safeStock),
    description = this.description,
    category = this.category,
    imageUrl = this.imageUrl,
    barcode = this.barcode,
    isActive = this.isActive,
    isDeleted = this.isDeleted,
    syncStatus = this.syncStatus,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    lastSyncedAt = this.lastSyncedAt
)
```

#### 2.2. Función `Product.toEntity()`
**Línea modificada**: 76  
**Cambio**: Reemplazado valor hardcodeado `isActive = true` por `isActive = this.isActive`

**Antes**:
```kotlin
fun Product.toEntity(): ProductEntity {
    return ProductEntity(
        id = this.id.value,
        name = this.name,
        description = this.description,
        category = this.category,
        price = this.price.amount.toDouble(),
        imageUrl = this.imageUrl,
        barcode = this.barcode,
        stock = this.stock.value,
        isDeleted = this.isDeleted,
        syncStatus = this.syncStatus,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        lastSyncedAt = this.lastSyncedAt,
        isActive = true // Default for now as Domain doesn't have it yet
    )
}
```

**Después**:
```kotlin
fun Product.toEntity(): ProductEntity {
    return ProductEntity(
        id = this.id.value,
        name = this.name,
        description = this.description,
        category = this.category,
        price = this.price.amount.toDouble(),
        imageUrl = this.imageUrl,
        barcode = this.barcode,
        stock = this.stock.value,
        isActive = this.isActive,
        isDeleted = this.isDeleted,
        syncStatus = this.syncStatus,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        lastSyncedAt = this.lastSyncedAt
    )
}
```

---

### 3. `app/src/main/java/com/are/distribuidora/data/local/mapper/ProductEntityMappers.kt`

**Cambios realizados**:

#### 3.1. Función `ProductEntity.toDomain()`
**Línea modificada**: 18  
**Cambio**: Agregado mapeo de `isActive = isActive`

**Antes**:
```kotlin
fun ProductEntity.toDomain(): Product = Product(
    id = ProductId.of(id),
    name = name,
    description = description,
    category = category,
    price = Money.of(price.toBigDecimal()),
    imageUrl = imageUrl,
    barcode = barcode,
    stock = Quantity.of(stock),
    isDeleted = isDeleted,
    syncStatus = syncStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)
```

**Después**:
```kotlin
fun ProductEntity.toDomain(): Product = Product(
    id = ProductId.of(id),
    name = name,
    description = description,
    category = category,
    price = Money.of(price.toBigDecimal()),
    imageUrl = imageUrl,
    barcode = barcode,
    stock = Quantity.of(stock),
    isActive = isActive,
    isDeleted = isDeleted,
    syncStatus = syncStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)
```

#### 3.2. Función `Product.toEntity()`
**Línea modificada**: 35  
**Cambio**: Reemplazado valor hardcodeado `isActive = true` por `isActive = isActive`

**Antes**:
```kotlin
fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id.value,
    name = name,
    description = description,
    category = category,
    price = price.amount.toDouble(),
    imageUrl = imageUrl,
    barcode = barcode,
    stock = stock.value,
    isActive = true, // Default since Domain doesn't have it
    isDeleted = isDeleted,
    syncStatus = syncStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)
```

**Después**:
```kotlin
fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id.value,
    name = name,
    description = description,
    category = category,
    price = price.amount.toDouble(),
    imageUrl = imageUrl,
    barcode = barcode,
    stock = stock.value,
    isActive = isActive,
    isDeleted = isDeleted,
    syncStatus = syncStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)
```

---

## Resumen de Cambios

| Archivo | Función Modificada | Tipo de Cambio |
|---------|-------------------|----------------|
| `domain/model/Product.kt` | Data class `Product` | ➕ Agregado campo `isActive: Boolean = true` |
| `data/mapper/ProductMapper.kt` | `ProductEntity.toDomainOrNull()` | ✏️ Mapeo de `isActive` desde entidad a dominio |
| `data/mapper/ProductMapper.kt` | `Product.toEntity()` | ✏️ Uso de `this.isActive` en lugar de hardcoded `true` |
| `data/local/mapper/ProductEntityMappers.kt` | `ProductEntity.toDomain()` | ✏️ Mapeo de `isActive` desde entidad a dominio |
| `data/local/mapper/ProductEntityMappers.kt` | `Product.toEntity()` | ✏️ Uso de `isActive` en lugar de hardcoded `true` |

---

## Impacto

✅ **Beneficios**:
- El campo `isActive` ahora se preserva correctamente durante todas las transformaciones
- No hay pérdida de datos durante la sincronización entre Room y Firestore
- Consistencia total en todo el flujo de datos (Remote → Entity → Domain → Entity → Remote)

⚠️ **Consideraciones**:
- Valor por defecto establecido en `true` para compatibilidad con código existente
- Los productos existentes en base de datos mantendrán su estado actual de `isActive`
- No requiere migración de base de datos (el campo ya existía en las entidades)

---

## Testing Sugerido

Se recomienda validar:
1. ✅ Sincronización de productos desde Firestore mantiene `isActive`
2. ✅ Crear/actualizar productos preserva el campo `isActive`
3. ✅ Transformaciones Entity ↔ Domain mantienen todos los campos
4. ✅ Productos con `isActive = false` no se resetean a `true`

---

**Implementado por**: GitHub Copilot  
**Validado**: ✅ Sin errores de compilación

