# Consultas Neo4j

Las 7 consultas Cypher se implementan en `CypherCatalog` y se exponen en `GET /api/grafo/consultas/{n}`. Los parámetros opcionales son `carrera`, `depto`, `materia`, `tipo`, `anio` y `limit` (1–500). Si no se envía `limit`, se usan 100 filas para las consultas tabulares y 150 para la visualización.

> **Importante:** no todas las consultas usan todos los filtros. La tabla de cada sección indica cuáles aplican. La visualización del explorador sí combina todos los filtros disponibles.

## Resumen

| # | Consulta | Parámetros usados | Resultado |
|---|---|---|---|
| G1 | Subgrafo por carrera | `carrera`, `limit` | Aspirante, carrera, materia, resultado, año, oportunidad |
| G2 | Carreras por departamento | `depto`, `limit` | Carrera y aspirantes únicos |
| G3 | Aprobados por materia | `anio`, `limit` | Materia y evaluaciones aprobadas |
| G4 | Aspirantes que comparten institución | `carrera`, `tipo`, `limit` | Pares de aspirantes, institución y carrera |
| G5 | Materias compartidas entre carreras | `limit` | Par de carreras, materias y total |
| G6 | Ruta geográfica completa | `depto`, `carrera`, `limit` | Departamento, municipio, tipo, carrera y aspirantes |
| G7 | Ruta más corta | `depto`, `carrera` (obligatorios) | Nodos de la ruta y número de saltos |

## G1. Subgrafo por carrera

**Propósito:** listar las evaluaciones de los aspirantes que aspiran a una carrera, con materia, resultado, año y oportunidad.

```cypher
MATCH (a:Aspirante)-[:ASPIRA_A]->(c:Carrera)
WHERE ($carrera IS NULL OR c.nombre = $carrera)
OPTIONAL MATCH (a)-[:REALIZO]->(e:Evaluacion)-[:DE_MATERIA]->(m:Materia)
RETURN a.correlativo AS aspirante, c.nombre AS carrera,
       m.nombre AS materia, e.aprobacion AS resultado,
       e.anio_ingreso AS anio, e.oportunidad AS oportunidad
LIMIT $limite
```

**Lectura:** `OPTIONAL MATCH` conserva aspirantes sin evaluaciones. El filtro `carrera` es opcional; sin él se listan todas las carreras hasta el límite. En la muestra, G1 devolvió 27 filas (una por evaluación) para todas las carreras.

## G2. Carreras más demandadas por departamento

**Propósito:** contar aspirantes únicos de un departamento que aspiran a cada carrera.

```cypher
MATCH (a:Aspirante)-[:PROCEDE_DE]->(:Institucion)-[:UBICADA_EN]->(:Municipio)-[:PERTENECE_A]->(d:Departamento)
WHERE ($depto IS NULL OR d.nombre = $depto)
MATCH (a)-[:ASPIRA_A]->(c:Carrera)
RETURN c.nombre AS carrera, count(DISTINCT a) AS aspirantes
ORDER BY aspirantes DESC
LIMIT $limite
```

**Lectura:** el recorrido de cuatro relaciones llega del aspirante al departamento; `count(DISTINCT a)` evita contar al mismo aspirante varias veces. Sin `depto`, el resultado agrega todos los departamentos.

## G3. Aprobados por materia

**Propósito:** contar evaluaciones aprobadas por materia, opcionalmente en un año.

```cypher
MATCH (e:Evaluacion {aprobado: true})-[:DE_MATERIA]->(m:Materia)
WHERE ($anio IS NULL OR e.anio_ingreso = $anio)
RETURN m.nombre AS materia, count(e) AS aprobados
ORDER BY aprobados DESC
LIMIT $limite
```

**Lectura:** usa el índice `evaluacion_aprobado`. Cuenta evaluaciones, no aspirantes únicos; es comparable con la consulta MongoDB 2.

## G4. Aspirantes que comparten institución educativa

**Propósito:** encontrar pares de aspirantes que provienen de la misma institución y mostrar la carrera del segundo aspirante.

```cypher
MATCH (a1:Aspirante)-[:PROCEDE_DE]->(i:Institucion)<-[:PROCEDE_DE]-(a2:Aspirante)-[:ASPIRA_A]->(c:Carrera)
WHERE a1.correlativo < a2.correlativo
  AND ($carrera IS NULL OR c.nombre = $carrera)
  AND ($tipo IS NULL OR i.tipo = $tipo)
RETURN a1.correlativo AS aspirante1, a2.correlativo AS aspirante2,
       i.nombre AS institucion, c.nombre AS carrera
LIMIT $limite
```

**Lectura:** `a1.correlativo < a2.correlativo` evita pares duplicados y autopares. El filtro `carrera` se aplica al segundo aspirante; la consulta no exige que el primero aspire a esa misma carrera. El filtro `materia` no aplica aquí; sí `carrera` y `tipo`.

## G5. Materias compartidas entre carreras

**Propósito:** identificar carreras que comparten materias evaluadas y cuántas.

```cypher
MATCH (c1:Carrera)<-[:PARA_CARRERA]-(e1:Evaluacion)-[:DE_MATERIA]->(m:Materia)
      <-[:DE_MATERIA]-(e2:Evaluacion)-[:PARA_CARRERA]->(c2:Carrera)
WHERE c1.nombre < c2.nombre
RETURN c1.nombre AS carrera1, c2.nombre AS carrera2,
       collect(DISTINCT m.nombre)[..5] AS materias_compartidas,
       count(DISTINCT m) AS total
ORDER BY total DESC
LIMIT $limite
```

**Lectura:** el patrón pasa por dos evaluaciones y una materia común. `c1.nombre < c2.nombre` evita duplicar el par y el autopar. Se muestran hasta cinco materias y el total.

## G6. Ruta geográfica completa

**Propósito:** resumir la ruta departamento → municipio → institución → aspirante → carrera.

```cypher
MATCH (d:Departamento)<-[:PERTENECE_A]-(m:Municipio)<-[:UBICADA_EN]-(i:Institucion)
      <-[:PROCEDE_DE]-(a:Aspirante)-[:ASPIRA_A]->(c:Carrera)
WHERE ($depto IS NULL OR d.nombre = $depto)
  AND ($carrera IS NULL OR c.nombre = $carrera)
RETURN d.nombre AS departamento, m.nombre AS municipio, i.tipo AS tipo_institucion,
       c.nombre AS carrera, count(DISTINCT a) AS aspirantes
ORDER BY aspirantes DESC
LIMIT $limite
```

**Lectura:** combina la jerarquía geográfica con la intención de carrera. Es la consulta más cercana a un informe de procedencia.

## G7. Ruta más corta entre un departamento y una carrera

**Propósito:** mostrar un camino de 1 a 6 relaciones que conecte un departamento con una carrera.

```cypher
MATCH path = shortestPath(
  (d:Departamento {nombre: $depto})-[*1..6]-(c:Carrera {nombre: $carrera})
)
RETURN [n IN nodes(path) | labels(n)[0] + ': ' + coalesce(n.nombre, n.correlativo)] AS ruta,
       length(path) AS saltos
```

**Parámetros obligatorios:** `depto` y `carrera`. Si falta alguno, la API responde `400` con un mensaje que indica qué seleccionar.

**Lectura:** la ruta de la muestra para `GUATEMALA` → `MEDICINA` tiene 4 saltos:

```json
{
  "ruta": [
    "Departamento: GUATEMALA",
    "Municipio: GUATEMALA",
    "Institucion: PUBLICO - GUATEMALA - GUATEMALA",
    "Aspirante: A7B8C9D0",
    "Carrera: MEDICINA"
  ],
  "saltos": 4
}
```

Si no existe un camino, el resultado es una lista vacía. La interfaz lo muestra como “sin ruta”.

## Explorador visual

`GET /api/grafo/visualizacion` no es una de las 7 consultas tabulares, pero usa el mismo modelo:

```cypher
MATCH (a:Aspirante)-[:PROCEDE_DE]->(i:Institucion)-[:UBICADA_EN]->(mu:Municipio)-[:PERTENECE_A]->(d:Departamento)
MATCH (a)-[:ASPIRA_A]->(c:Carrera)
OPTIONAL MATCH (a)-[:REALIZO]->(e:Evaluacion)-[:DE_MATERIA]->(mat:Materia)
WHERE (e)-[:PARA_CARRERA]->(c)
WITH a, i, mu, d, c, e, mat
WHERE 1 = 1
  AND c.nombre = $carrera
  AND d.nombre = $depto
  AND i.tipo = $tipo
  AND mat.nombre = $materia
  AND e.anio_ingreso = $anio
RETURN a, i, mu, d, c, e, mat
LIMIT $limite
```

Los filtros se agregan solo si el usuario los envía. La etapa `WITH` asegura que se apliquen al resultado global, no solo al `OPTIONAL MATCH`. La evaluación debe pertenecer a la carrera mostrada. El backend deduplica nodos y relaciones antes de devolver el grafo a `react-force-graph-2d`.

`limit` restringe los registros de coincidencias de Cypher: cada registro puede aportar varios nodos y relaciones, por lo que no es un máximo estricto de nodos distintos.

## Detalle de un nodo

`GET /api/grafo/nodo/{id}` usa:

```cypher
MATCH (n)
WHERE elementId(n) = $id
OPTIONAL MATCH (n)-[r]-(neighbor)
RETURN n, collect(DISTINCT r) AS relaciones, collect(DISTINCT neighbor) AS vecinos
```

El `elementId` contiene dos puntos (`4:...:123`), por lo que el frontend lo codifica con `encodeURIComponent` antes de llamar a la API.

## Errores esperados

| Caso | Respuesta |
|---|---|
| Consulta fuera de 1–7 | `404` |
| G7 sin `depto` o sin `carrera` | `400` |
| Nodo inexistente | `404` |
| Neo4j no disponible | `503` |
| `limit` fuera de rango | Se ajusta a 1–500 |
