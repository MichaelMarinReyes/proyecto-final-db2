# Modelamiento de datos en MongoDB

## 1. ¿Por qué MongoDB?

El dataset original es una tabla de evaluaciones con columnas heterogéneas y valores como `SIN REGISTRO`, `NINGUNO` o años ausentes. MongoDB permite:

- almacenar cada evaluación como un documento BSON sin exigir un esquema rígido;
- agregar campos derivados (`aprobado`, `edad_ingreso`, `hash_fila`) durante el ETL;
- construir pipelines de agregación para responder las 20 preguntas analíticas;
- mantener dos representaciones complementarias: una **plana** para analítica y una **embebida** para lectura por aspirante.

## 2. Base de datos y colecciones

Base de datos: `evaluaciones`.

| Colección | Granularidad | Propósito |
|---|---|---|
| `evaluaciones` | Una fila del CSV | Fuente de verdad, consultas analíticas y reconstrucción de agregados. |
| `aspirantes` | Un documento por correlativo | Vista embebida con todas las evaluaciones del aspirante. Se reconstruye con `$out`. |
| `resumen_carrera` | Una carrera | Resumen precalculado con `$merge` (consulta 6). |
| `cargas` | Una carga | Historial, progreso, tiempos, totales y errores por archivo. |
| `registros_rechazados` | Una fila rechazada | Trazabilidad de filas inválidas con causa, archivo y línea. |
| `consultas_personalizadas` | Una definición guardada | Biblioteca administrativa MongoDB/Cypher; guarda nombre, código, motor y modo, no resultados. |

## 3. Colección `evaluaciones`

### 3.1 Esquema

| Campo | Tipo BSON | Origen / regla |
|---|---|---|
| `_id` | ObjectId | Generado por MongoDB. |
| `correlativo_aspirante` | String | `correlativo` del CSV, sin transformar. |
| `fecha_asignacion` | Date o null | Fecha parseada a medianoche UTC. |
| `anio_nacimiento` | Int o null | Año de nacimiento; `SIN REGISTRO` produce null. |
| `edad_ingreso` | Int o null | `anio_ingreso - anio_nacimiento` si está entre 10 y 80. |
| `sexo` | String | `M`, `F` o `DESCONOCIDO`. |
| `materia` | String | Mayúsculas, sin espacios duplicados. |
| `oportunidad` | Int | Número de fecha de evaluación; debe ser ≥ 1. |
| `anio_ingreso` | Int | Año entre 1990 y 2100. |
| `aprobacion` | String | `APROBADO` o `NO APROBADO`. |
| `aprobado` | Boolean | Derivado de `aprobacion`; facilita índices y agregaciones. |
| `carrera_objetivo` | String | Mayúsculas; `NINGUNO` si está vacía. |
| `departamento_ie` | String | Mayúsculas; `SIN REGISTRO` si está vacío. |
| `municipio_ie` | String | Mayúsculas; `SIN REGISTRO` si está vacío. |
| `tipo_ie` | String | `PUBLICO`, `PRIVADO`, `SIN REGISTRO` o el valor normalizado. |
| `archivo_origen` | String | Nombre del CSV cargado. |
| `hash_fila` | String | SHA-256 de los campos normalizados; índice único. |

### 3.2 Ejemplo de documento

```json
{
  "_id": { "$oid": "671f0c2a9f1b2c3d4e5f6789" },
  "correlativo_aspirante": "A1B2C3D4",
  "fecha_asignacion": { "$date": "2018-01-15T00:00:00.000Z" },
  "anio_nacimiento": 2000,
  "edad_ingreso": 18,
  "sexo": "M",
  "materia": "MATEMATICA",
  "oportunidad": 1,
  "anio_ingreso": 2018,
  "aprobacion": "APROBADO",
  "aprobado": true,
  "carrera_objetivo": "INGENIERIA EN SISTEMAS",
  "departamento_ie": "GUATEMALA",
  "municipio_ie": "GUATEMALA",
  "tipo_ie": "PUBLICO",
  "archivo_origen": "pruebas_especificas_2018.csv",
  "hash_fila": "e3b0c44298fc1c149afbf4c8996fb924..."
}
```

### 3.3 Índices

`EvaluationRepository.ensureIndexes()` crea:

| Índice | Motivo |
|---|---|
| `correlativo_aspirante` | Historial por aspirante y construcción de `aspirantes`. |
| `materia, aprobado` | Consultas 2, 4 y 14. |
| `carrera_objetivo, anio_ingreso` | Consultas 3, 5, 6, 7, 10, 16 y 20. |
| `tipo_ie` | Consultas 1, 12 y 18. |
| `municipio_ie` | Consultas 8 y 17. |
| `departamento_ie` | Consultas 8, 17 y 19. |
| `anio_ingreso` | Filtros y evolución anual. |
| `edad_ingreso` | Consultas 10 y 13. |
| `sexo, tipo_ie` | Consulta 12. |
| `hash_fila` (único, sparse) | Idempotencia del ETL. |

## 4. Colección `aspirantes` (modelo embebido)

Se reconstruye **después** de cada carga exitosa con una agregación sobre `evaluaciones` que termina en `$out: "aspirantes"`:

```javascript
db.evaluaciones.aggregate([
  { $group: {
      _id: "$correlativo_aspirante",
      sexo: { $first: "$sexo" },
      anio_nacimiento: { $first: "$anio_nacimiento" },
      total_evaluaciones: { $sum: 1 },
      materias: { $addToSet: "$materia" },
      carreras_objetivo: { $addToSet: "$carrera_objetivo" },
      evaluaciones: { $push: {
          fecha_asignacion: "$fecha_asignacion",
          materia: "$materia",
          oportunidad: "$oportunidad",
          anio_ingreso: "$anio_ingreso",
          aprobacion: "$aprobacion",
          carrera_objetivo: "$carrera_objetivo",
          municipio_ie: "$municipio_ie",
          tipo_ie: "$tipo_ie"
      } }
  } },
  { $set: { actualizado_en: "$$NOW" } },
  { $out: "aspirantes" }
])
```

Ventajas:

- una sola lectura para la ficha del aspirante (consulta 15);
- `materias` y `carreras_objetivo` como conjuntos;
- el documento crece con el historial, pero el número de evaluaciones por aspirante es acotado en este dominio.

Se eligió `$out` y no `$merge` porque el documento completo debe reflejar el estado actual: si se corrigiera o eliminara una evaluación, la reconstrucción descarta la versión anterior. La colección plana sigue siendo la fuente de verdad.

## 5. Colección `resumen_carrera`

La consulta 6 usa `$merge` para mantener un resumen incremental por carrera:

```javascript
db.evaluaciones.aggregate([
  { $group: {
      _id: "$carrera_objetivo",
      total_evaluaciones: { $sum: 1 },
      aspirantes: { $addToSet: "$correlativo_aspirante" },
      aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } },
      edades: { $push: "$edad_ingreso" }
  } },
  { $project: {
      total_evaluaciones: 1,
      total_aspirantes: { $size: "$aspirantes" },
      aprobados: 1,
      reprobados: { $subtract: ["$total_evaluaciones", "$aprobados"] },
      tasa_aprobacion: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total_evaluaciones"] }, 100] }, 2] },
      edades: { $filter: { input: "$edades", as: "e", cond: { $ne: ["$$e", null] } } }
  } },
  { $addFields: {
      promedio_edad: { $cond: [
          { $eq: [{ $size: "$edades" }, 0] },
          null,
          { $round: [{ $avg: "$edades" }, 2] }
      ] }
  } },
  { $unset: "edades" },
  { $set: { generado_en: "$$NOW" } },
  { $merge: {
      into: "resumen_carrera",
      on: "_id",
      whenMatched: "replace",
      whenNotMatched: "insert"
  } }
])
```

`aprobados` cuenta **evaluaciones aprobadas**; `total_aspirantes` cuenta correlativos únicos. Es importante no confundir ambas métricas al leer el dashboard.

## 6. Colección `cargas`

Documento de ejemplo:

```json
{
  "_id": "carga-5294a1bf-50cf-4250-926b-8bccc24e5890",
  "estado": "EXITOSA_CON_RECHAZOS",
  "fechaInicio": "2026-10-26T17:32:49.077Z",
  "fechaFin": "2026-10-26T17:32:49.949Z",
  "duracionMs": 872,
  "progreso": 100,
  "totales": {
    "leidos": 29,
    "procesados": 28,
    "rechazados": 1,
    "duplicados": 28
  },
  "archivos": [
    {
      "nombre": "pruebas_especificas_muestra.csv",
      "registrosLeidos": 29,
      "registrosProcesados": 28,
      "registrosRechazados": 1,
      "duplicadosOmitidos": 28,
      "duracionMs": 755,
      "estado": "EXITOSA_CON_RECHAZOS",
      "errores": [
        { "fila": 30, "causa": "materia vacía", "valor": "..." }
      ]
    }
  ]
}
```

El documento se inserta al iniciar y se reemplaza al finalizar. Durante la ejecución, el estado en memoria del `CargaJob` es el que consulta el endpoint de progreso.

## 7. Colección `registros_rechazados`

```json
{
  "carga_id": "carga-5294a1bf-50cf-4250-926b-8bccc24e5890",
  "archivo": "pruebas_especificas_muestra.csv",
  "fila": 30,
  "causa": "materia vacía",
  "valor": "Z9Y8X7W6 | 2020-01-01 | F | 2002 |  | 1 | ...",
  "fecha": "2026-10-26T17:32:49.100Z"
}
```

El índice por `carga_id` permite paginar los rechazos de una carga concreta sin recorrer toda la colección.

## 8. Consistencia y reconstrucción

- `evaluaciones` se inserta por lotes con `ordered(false)` para que una fila inválida no bloquee el resto.
- Los duplicados se detectan por el índice único de `hash_fila` (código 11000) y se informan como `duplicadosOmitidos`.
- Después de una carga con al menos una inserción, se reconstruye `aspirantes`.
- `resumen_carrera` se regenera a demanda con `POST /api/consultas/mongo/6/resumen`.
- Neo4j se actualiza con `MERGE`, por lo que volver a cargar el mismo archivo no duplica nodos ni relaciones.

## 9. Decisiones de modelado y trade-offs

| Decisión | Alternativa descartada | Justificación |
|---|---|---|
| Documento plano por evaluación | Un documento por aspirante desde el inicio | El análisis requiere agrupar por materia, carrera, año y municipio; la forma plana simplifica pipelines. |
| `aspirantes` derivado con `$out` | Mantenerlo escrito por la aplicación | Evita lógica duplicada y garantiza que se reconstruya desde la fuente de verdad. |
| `resumen_carrera` con `$merge` | Calcular la consulta 6 en cada petición | Demuestra `$merge` y acelera lecturas repetidas del dashboard. |
| Booleanos derivados | Filtrar por texto `APROBADO` | Índices y agregaciones más simples; el texto original se conserva en `aprobacion`. |
| `hash_fila` | Índice compuesto por todos los campos | Un solo índice único, robusto ante valores nulos y cambios de orden de columnas. |
