# Consultas MongoDB

Las 20 consultas se implementan en `MongoQueryService` y se exponen en `GET /api/consultas/mongo/{n}`. Los filtros opcionales son `anio`, `materia`, `carrera`, `depto`, `tipo` y `limit`; las consultas 11 y 15 requieren `correlativo`.

La colección base es `evaluaciones`, salvo la consulta 6 (que escribe y lee `resumen_carrera`) y las consultas 11/15 (que consultan `evaluaciones` y `aspirantes` respectivamente).

## Resumen

| # | Pregunta | Técnica principal | Colección |
|---|---|---|---|
| 1 | Aspirantes por tipo de institución | `$group` en dos niveles | `evaluaciones` |
| 2 | Aprobados por materia | `$match` + `$group` | `evaluaciones` |
| 3 | Aprobados por carrera y año | `$group` compuesto + únicos | `evaluaciones` |
| 4 | Porcentaje de aprobación por materia | `$cond`, `$divide`, `$round` | `evaluaciones` |
| 5 | Edad promedio por carrera | `$group` por aspirante + `$avg` | `evaluaciones` |
| 6 | Resumen por carrera | `$merge` | `evaluaciones` → `resumen_carrera` |
| 7 | Edad promedio de aprobados por carrera y tipo | `$group` + `$avg` | `evaluaciones` |
| 8 | Aprobados por municipio y carrera | `$group` + `$limit` | `evaluaciones` |
| 9 | Evaluaciones por mes y materia (públicas) | `$month` + `$group` | `evaluaciones` |
| 10 | Top 5 carreras demandadas por jóvenes de 16–18 | `$addToSet` + `$size` | `evaluaciones` |
| 11 | Historial de un aspirante | `find` ordenado | `evaluaciones` |
| 12 | Distribución por sexo y tipo | `$group` en dos niveles | `evaluaciones` |
| 13 | Tasa de aprobación por edad | `$cond` + `$divide` | `evaluaciones` |
| 14 | Intentos promedio por materia | `$max` + `$avg` | `evaluaciones` |
| 15 | Ficha completa de un aspirante | documento embebido | `aspirantes` |
| 16 | Carreras con más reprobados en primer intento | `$addToSet` + `$size` | `evaluaciones` |
| 17 | Top 5 municipios con más aspirantes | `$addToSet` + `$size` | `evaluaciones` |
| 18 | Tasa de aprobación por tipo de institución | `$cond` + `$divide` | `evaluaciones` |
| 19 | Top 3 carreras por departamento | `$setWindowFields` + `$rank` | `evaluaciones` |
| 20 | Evolución de aprobación por año | `$group` + porcentaje | `evaluaciones` |

## 1. Aspirantes por tipo de institución educativa

**Propósito:** contar aspirantes únicos según si provienen de institución pública, privada u otra.

```javascript
db.evaluaciones.aggregate([
  { $match: { /* filtros */ } },
  { $group: { _id: { tipo: "$tipo_ie", corr: "$correlativo_aspirante" } } },
  { $group: { _id: "$_id.tipo", aspirantes: { $sum: 1 } } },
  { $project: { _id: 0, tipo: "$_id", aspirantes: 1 } },
  { $sort: { aspirantes: -1 } }
])
```

**Lectura:** el primer `$group` elimina las múltiples evaluaciones de un mismo aspirante; el segundo cuenta aspirantes por tipo.

## 2. Cantidad de aprobados por materia

**Propósito:** total de evaluaciones aprobadas por materia.

```javascript
db.evaluaciones.aggregate([
  { $match: { aprobado: true, /* filtros */ } },
  { $group: { _id: "$materia", aprobados: { $sum: 1 } } },
  { $project: { _id: 0, materia: "$_id", aprobados: 1 } },
  { $sort: { aprobados: -1 } }
])
```

**Lectura:** cada evaluación aprobada cuenta una vez. No es un conteo de aspirantes únicos; para eso están las consultas 3, 8 y 16.

## 3. Aprobados por carrera y año de ingreso

**Propósito:** aspirantes únicos aprobados por carrera objetivo y año.

```javascript
db.evaluaciones.aggregate([
  { $match: { aprobado: true, /* filtros */ } },
  { $group: {
      _id: { carrera: "$carrera_objetivo", anio: "$anio_ingreso",
             corr: "$correlativo_aspirante" }
  } },
  { $group: {
      _id: { carrera: "$_id.carrera", anio: "$_id.anio" },
      aprobados: { $sum: 1 }
  } },
  { $project: { _id: 0, carrera: "$_id.carrera", anio: "$_id.anio", aprobados: 1 } },
  { $sort: { anio: 1 } }
])
```

**Lectura:** un aspirante que aprobó dos materias para la misma carrera y año cuenta una sola vez.

## 4. Porcentaje de aprobación por materia

**Propósito:** comparar la tasa de aprobación entre materias.

```javascript
db.evaluaciones.aggregate([
  { $match: { /* filtros */ } },
  { $group: {
      _id: "$materia",
      total: { $sum: 1 },
      aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } }
  } },
  { $project: {
      _id: 0, materia: "$_id", total: 1, aprobados: 1,
      porcentaje: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total"] }, 100] }, 2] }
  } },
  { $sort: { porcentaje: -1 } }
])
```

**Lectura:** el denominador es el total de evaluaciones de la materia, no el total de aspirantes.

## 5. Edad promedio por carrera

**Propósito:** promedio de edad de ingreso de los aspirantes de cada carrera.

```javascript
db.evaluaciones.aggregate([
  { $match: { edad_ingreso: { $ne: null }, /* filtros */ } },
  { $group: {
      _id: { carrera: "$carrera_objetivo", corr: "$correlativo_aspirante" },
      edad: { $first: "$edad_ingreso" }
  } },
  { $group: {
      _id: "$_id.carrera",
      edad_promedio: { $avg: "$edad" },
      muestras: { $sum: 1 }
  } },
  { $project: {
      _id: 0, carrera: "$_id",
      edad_promedio: { $round: ["$edad_promedio", 2] },
      muestras: 1
  } },
  { $sort: { edad_promedio: -1 } }
])
```

**Lectura:** `muestras` es el número de aspirantes únicos con edad válida, no el número de evaluaciones.

## 6. Resumen por carrera (`resumen_carrera`)

**Propósito:** precalcular métricas por carrera y demostrar `$merge`.

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
          { $eq: [{ $size: "$edades" }, 0] }, null,
          { $round: [{ $avg: "$edades" }, 2] }
      ] }
  } },
  { $unset: "edades" },
  { $set: { generado_en: "$$NOW" } },
  { $merge: { into: "resumen_carrera", on: "_id", whenMatched: "replace", whenNotMatched: "insert" } }
])
```

Se ejecuta con `POST /api/consultas/mongo/6/resumen`. La lectura posterior proyecta `_id` como `carrera` y ordena por `tasa_aprobacion`.

**Lectura:** `aprobados` cuenta evaluaciones; `total_aspirantes` cuenta correlativos únicos. El resumen es materializado y se regenera cuando se cargan datos nuevos.

## 7. Edad promedio de aprobados por carrera y tipo de institución

**Propósito:** comparar la edad de los aprobados según el tipo de institución de procedencia.

```javascript
db.evaluaciones.aggregate([
  { $match: { aprobado: true, edad_ingreso: { $ne: null }, /* filtros */ } },
  { $group: {
      _id: { carrera: "$carrera_objetivo", tipo: "$tipo_ie", corr: "$correlativo_aspirante" },
      edad: { $first: "$edad_ingreso" }
  } },
  { $group: {
      _id: { carrera: "$_id.carrera", tipo: "$_id.tipo" },
      edad_promedio: { $avg: "$edad" },
      muestras: { $sum: 1 }
  } },
  { $project: {
      _id: 0, carrera: "$_id.carrera", tipo: "$_id.tipo",
      edad_promedio: { $round: ["$edad_promedio", 2] }, muestras: 1
  } },
  { $sort: { carrera: 1 } }
])
```

**Lectura:** primero se reduce a un registro por aspirante y luego se promedia, evitando que un aspirante con muchas evaluaciones pese más.

## 8. Aprobados por municipio y carrera

**Propósito:** distribución geográfica de aspirantes únicos aprobados.

```javascript
db.evaluaciones.aggregate([
  { $match: { aprobado: true, /* filtros */ } },
  { $group: {
      _id: { municipio: "$municipio_ie", departamento: "$departamento_ie",
             carrera: "$carrera_objetivo", corr: "$correlativo_aspirante" }
  } },
  { $group: {
      _id: { municipio: "$_id.municipio", departamento: "$_id.departamento",
             carrera: "$_id.carrera" },
      aprobados: { $sum: 1 }
  } },
  { $project: {
      _id: 0, municipio: "$_id.municipio", departamento: "$_id.departamento",
      carrera: "$_id.carrera", aprobados: 1
  } },
  { $sort: { aprobados: -1 } },
  { $limit: 100 }
])
```

**Lectura:** un aprobado por municipio y carrera; el límite protege la interfaz ante combinaciones muy dispersas.

## 9. Evaluaciones por mes y materia en instituciones públicas

**Propósito:** estacionalidad de las evaluaciones en el sector público.

```javascript
db.evaluaciones.aggregate([
  { $match: { tipo_ie: "PUBLICO", fecha_asignacion: { $ne: null }, /* filtros */ } },
  { $group: {
      _id: { mes: { $month: "$fecha_asignacion" }, materia: "$materia" },
      total: { $sum: 1 }
  } },
  { $project: { _id: 0, mes: "$_id.mes", materia: "$_id.materia", total: 1 } },
  { $sort: { mes: 1 } }
])
```

**Lectura:** `mes` es 1–12. Las evaluaciones sin fecha válida se excluyen.

## 10. Top 5 carreras demandadas por aspirantes de 16 a 18 años

**Propósito:** identificar las carreras preferidas por el grupo etario más joven.

```javascript
db.evaluaciones.aggregate([
  { $match: { edad_ingreso: { $gte: 16, $lte: 18 }, /* filtros */ } },
  { $group: {
      _id: "$carrera_objetivo",
      aspirantes: { $addToSet: "$correlativo_aspirante" }
  } },
  { $project: { _id: 0, carrera: "$_id", total_aspirantes: { $size: "$aspirantes" } } },
  { $sort: { total_aspirantes: -1 } },
  { $limit: 5 }
])
```

**Lectura:** `$addToSet` + `$size` evita contar varias veces al mismo aspirante.

## 11. Historial de desempeño por aspirante

**Propósito:** mostrar todas las evaluaciones de un correlativo, ordenadas por año, oportunidad y fecha.

```javascript
db.evaluaciones.find({ correlativo_aspirante: "A1B2C3D4" })
  .sort({ anio_ingreso: 1, oportunidad: 1, fecha_asignacion: 1 })
```

Parámetro obligatorio: `correlativo`. Si no se envía, la API responde `400`.

**Lectura:** es una consulta de detalle, no una agregación. La interfaz la usa en el panel de historial del buscador.

## 12. Distribución por sexo y tipo de institución

**Propósito:** composición de los aspirantes únicos por sexo y tipo de institución.

```javascript
db.evaluaciones.aggregate([
  { $match: { /* filtros */ } },
  { $group: {
      _id: { sexo: "$sexo", tipo: "$tipo_ie", corr: "$correlativo_aspirante" }
  } },
  { $group: {
      _id: { sexo: "$_id.sexo", tipo: "$_id.tipo" },
      aspirantes: { $sum: 1 }
  } },
  { $project: { _id: 0, sexo: "$_id.sexo", tipo: "$_id.tipo", aspirantes: 1 } },
  { $sort: { aspirantes: -1 } }
])
```

**Lectura:** doble `$group` para eliminar duplicados por aspirante antes de contar.

## 13. Tasa de aprobación por edad

**Propósito:** relacionar la edad de ingreso con el porcentaje de evaluaciones aprobadas.

```javascript
db.evaluaciones.aggregate([
  { $match: { edad_ingreso: { $ne: null }, /* filtros */ } },
  { $group: {
      _id: "$edad_ingreso",
      total: { $sum: 1 },
      aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } }
  } },
  { $project: {
      _id: 0, edad: "$_id", total: 1, aprobados: 1,
      porcentaje: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total"] }, 100] }, 2] }
  } },
  { $sort: { edad: 1 } }
])
```

**Lectura:** cada edad es una fila; con el dataset real conviene agrupar por rangos si hay muchas edades.

## 14. Número promedio de intentos por materia

**Propósito:** estimar cuántas oportunidades usa en promedio un aspirante por materia.

```javascript
db.evaluaciones.aggregate([
  { $match: { /* filtros */ } },
  { $group: {
      _id: { materia: "$materia", corr: "$correlativo_aspirante" },
      max_oportunidad: { $max: "$oportunidad" }
  } },
  { $group: {
      _id: "$_id.materia",
      intentos_promedio: { $avg: "$max_oportunidad" },
      aspirantes: { $sum: 1 }
  } },
  { $project: {
      _id: 0, materia: "$_id",
      intentos_promedio: { $round: ["$intentos_promedio", 2] },
      aspirantes: 1
  } },
  { $sort: { intentos_promedio: -1 } }
])
```

**Lectura:** se toma el máximo de oportunidad por aspirante/materia y luego se promedia entre aspirantes. No es el promedio de filas.

## 15. Ficha completa de un aspirante (documento embebido)

**Propósito:** leer de una sola vez el documento reconstruido en `aspirantes`.

```javascript
db.aspirantes.findOne({ _id: "A1B2C3D4" })
```

Parámetro obligatorio: `correlativo`. Si no existe, la API responde `404` con mensaje en español.

**Lectura:** el documento incluye datos personales, totales, materias, carreras y el arreglo `evaluaciones`. Es la base de la ficha del buscador.

## 16. Carreras con más aspirantes reprobados en primer intento

**Propósito:** detectar carreras con mayor volumen de fracasos en la primera oportunidad.

```javascript
db.evaluaciones.aggregate([
  { $match: { aprobado: false, oportunidad: 1, /* filtros */ } },
  { $group: {
      _id: "$carrera_objetivo",
      reprobados: { $addToSet: "$correlativo_aspirante" }
  } },
  { $project: { _id: 0, carrera: "$_id", total_reprobados: { $size: "$reprobados" } } },
  { $sort: { total_reprobados: -1 } },
  { $limit: 10 }
])
```

**Lectura:** un aspirante que reprobó varias materias en primer intento cuenta una vez por carrera. No implica que nunca haya aprobado después.

## 17. Top 5 municipios con mayor cantidad de aspirantes

**Propósito:** ranking de procedencia geográfica.

```javascript
db.evaluaciones.aggregate([
  { $match: { /* filtros */ } },
  { $group: {
      _id: { municipio: "$municipio_ie", departamento: "$departamento_ie" },
      aspirantes: { $addToSet: "$correlativo_aspirante" }
  } },
  { $project: {
      _id: 0, municipio: "$_id.municipio", departamento: "$_id.departamento",
      total_aspirantes: { $size: "$aspirantes" }
  } },
  { $sort: { total_aspirantes: -1 } },
  { $limit: 5 }
])
```

**Lectura:** el municipio se identifica junto con su departamento para evitar ambigüedades.

## 18. Tasa de aprobación por tipo de institución

**Propósito:** comparar el porcentaje de evaluaciones aprobadas entre instituciones públicas y privadas.

```javascript
db.evaluaciones.aggregate([
  { $match: { /* filtros */ } },
  { $group: {
      _id: "$tipo_ie",
      total: { $sum: 1 },
      aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } }
  } },
  { $project: {
      _id: 0, tipo: "$_id", total: 1, aprobados: 1,
      porcentaje: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total"] }, 100] }, 2] }
  } },
  { $sort: { porcentaje: -1 } }
])
```

**Lectura:** es una tasa por evaluación, no por aspirante.

## 19. Top 3 carreras por departamento

**Propósito:** demanda relativa de carreras dentro de cada departamento.

```javascript
db.evaluaciones.aggregate([
  { $match: { /* filtros */ } },
  { $group: {
      _id: { departamento: "$departamento_ie", carrera: "$carrera_objetivo",
             corr: "$correlativo_aspirante" }
  } },
  { $group: {
      _id: { departamento: "$_id.departamento", carrera: "$_id.carrera" },
      demanda: { $sum: 1 }
  } },
  { $sort: { "_id.departamento": 1 } },
  { $setWindowFields: {
      partitionBy: "$_id.departamento",
      sortBy: { demanda: -1 },
      output: { rank: { $rank: {} } }
  } },
  { $match: { rank: { $lte: 3 } } },
  { $project: {
      _id: 0, departamento: "$_id.departamento", carrera: "$_id.carrera",
      demanda: 1, rank: 1
  } },
  { $sort: { departamento: 1 } }
])
```

**Lectura:** `$setWindowFields` permite rankear dentro de cada departamento sin traer todo a la aplicación. Los empates comparten `rank`.

## 20. Evolución de la aprobación por año de ingreso

**Propósito:** serie temporal de evaluaciones y porcentaje de aprobación.

```javascript
db.evaluaciones.aggregate([
  { $match: { /* filtros */ } },
  { $group: {
      _id: "$anio_ingreso",
      total: { $sum: 1 },
      aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } },
      no_aprobados: { $sum: { $cond: ["$aprobado", 0, 1] } }
  } },
  { $project: {
      _id: 0, anio: "$_id", total: 1, aprobados: 1, no_aprobados: 1,
      porcentaje_aprobacion: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total"] }, 100] }, 2] }
  } },
  { $sort: { anio: 1 } }
])
```

**Lectura:** el denominador incluye evaluaciones aprobadas y no aprobadas; las filas con año inválido no existen porque el ETL las rechaza.

## Filtros comunes

`baseMatch` aplica, si están presentes:

```javascript
{
  anio_ingreso: anio,
  materia: materia,
  carrera_objetivo: carrera,
  departamento_ie: depto,
  tipo_ie: tipo
}
```

Las consultas que fijan condiciones propias (por ejemplo, `aprobado: true`, `tipo_ie: "PUBLICO"`, `oportunidad: 1`) combinan esas condiciones con los filtros.

## Errores esperados

| Caso | Respuesta |
|---|---|
| Número de consulta fuera de 1–20 | `404` con mensaje en español |
| Consulta 11 o 15 sin `correlativo` | `400` |
| Correlativo inexistente en consulta 15 | `404` |
| `numero` no numérico en la ruta | `400` |
| MongoDB no disponible | `503` |
