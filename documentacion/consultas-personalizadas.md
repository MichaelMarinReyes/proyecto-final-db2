# Consultas personalizadas guardadas

## Uso

Las pantallas **Consultas MongoDB** y **Explorador de grafos** incluyen una biblioteca de consultas personalizadas, independiente de las 20 consultas MongoDB y 7 Cypher predefinidas.

1. Busca **Agregar y guardar nuevas consultas**.
2. Introduce la clave privada `APP_QUERIES_ADMIN_KEY` del archivo `proyecto-final-db2/.env` y pulsa **Abrir biblioteca**.
3. Escribe nombre, descripción opcional y código.
4. Pulsa **Guardar nueva consulta**. Guardar no ejecuta el código.
5. Selecciona una consulta en **Mis consultas guardadas** y pulsa **Ejecutar consulta guardada**.
6. Si modifica datos, escribe **EJECUTAR** en la confirmación para habilitar el botón. El backend también exige confirmación; no basta con habilitar el botón en el navegador.

Las definiciones se guardan en MongoDB, colección `consultas_personalizadas`, con UUID, nombre, descripción, motor, código, modo y fecha. No dependen de `localStorage`: permanecen después de recargar la página o reiniciar el backend. No se guardan resultados ni se ejecutan consultas automáticamente al abrir la aplicación.

Las predefinidas no se pueden reemplazar con este formulario. La primera versión permite agregar, listar y ejecutar definiciones; no incluye edición ni eliminación desde la interfaz. Se listan las 200 definiciones más recientes de cada motor.

## MongoDB

El editor acepta JSON válido, no comandos JavaScript `db...` de mongosh. Las operaciones se ejecutan únicamente en la base configurada en el backend, y solo sobre `evaluaciones`, `aspirantes` y `resumen_carrera`.

### Lectura con agregación

```json
{
  "operacion": "aggregate",
  "coleccion": "evaluaciones",
  "pipeline": [
    { "$group": { "_id": "$materia", "total": { "$sum": 1 } } },
    { "$sort": { "total": -1 } }
  ]
}
```

### Lectura con filtro

```json
{
  "operacion": "find",
  "coleccion": "evaluaciones",
  "filtro": { "materia": "MATEMATICA" }
}
```

### Actualización

```json
{
  "operacion": "updateMany",
  "coleccion": "evaluaciones",
  "filtro": { "correlativo_aspirante": "CORRELATIVO_A_CORREGIR" },
  "actualizacion": { "$set": { "carrera_objetivo": "CARRERA_CORREGIDA" } }
}
```

### Otras operaciones de escritura

- `insertMany`: recibe `documentos`, un arreglo de 1 a 1000 documentos.
- `deleteMany`: recibe `filtro`; borra todos los documentos que coincidan.
- `aggregate` con `$out` o `$merge`: se considera escritura y requiere confirmación. La colección destino también debe estar permitida; no se permite especificar otra base de datos.

`filtro` es obligatorio en `find`, `updateMany` y `deleteMany`. Un filtro `{}` significa **todos los documentos**; úsalo con mucho cuidado. `updateMany` no hace upsert y acepta operadores de actualización explícitos como `$set`, `$unset` o `$inc`.

No se permite JavaScript de servidor, acceso al catálogo de consultas ni administración de las bases. Las uniones `$lookup`, `$graphLookup` y `$unionWith` también se restringen a las colecciones permitidas.

## Neo4j

Se admite una sola sentencia Cypher, sin punto y coma final. La consulta debe contener sus valores literales; esta versión no tiene un formulario de parámetros.

Lectura:

```cypher
MATCH (a:Aspirante)-[:ASPIRA_A]->(c:Carrera)
RETURN c.nombre AS carrera, count(DISTINCT a) AS aspirantes
ORDER BY aspirantes DESC
LIMIT 20
```

Escritura de ejemplo —revisa el correlativo antes de ejecutar—:

```cypher
MATCH (a:Aspirante {correlativo: 'CORRELATIVO_A_CORREGIR'})
SET a.sexo = 'F'
RETURN a.correlativo AS correlativo, a.sexo AS sexo
```

Se detectan escrituras con `CREATE`, `INSERT`, `MERGE`, `SET`, `DELETE`, `REMOVE` y `FOREACH`. No se admiten `CALL`, `LOAD CSV`, cambio de base ni administración de usuarios, índices o restricciones. El control es deliberadamente conservador y puede rechazar identificadores que coincidan con palabras administrativas. No es un sandbox para ejecutar código de usuarios no confiables.

Además del análisis del código, el backend solicita a Neo4j un plan `EXPLAIN` al guardar y antes de ejecutar. Esto valida la sintaxis y obtiene del propio motor la clasificación lectura/escritura sin modificar datos. Por ello Neo4j debe estar disponible para guardar una definición Cypher. No escribas `EXPLAIN`, `PROFILE` ni directivas de ejecución en el editor: introduce la sentencia de datos directamente.

## Seguridad y límites

- Toda la biblioteca requiere `X-Query-Admin-Key`, incluso el listado; la clave se compara en el backend.
- La clave se obtiene exclusivamente del `.env` del backend. Nunca debe copiarse a `VITE_*` ni incluirse en el repositorio.
- React la mantiene solo en memoria mientras esa vista está abierta; **Bloquear biblioteca**, cambiar de pantalla o recargar elimina ese acceso local.
- `APP_QUERIES_ADMIN_KEY` debe tener al menos 32 caracteres. Si falta, la función queda deshabilitada con 503, sin impedir usar las predefinidas.
- `APP_QUERIES_MAX_ROWS` controla el máximo de filas mostradas (con un tope de 500).
- `APP_QUERIES_TIMEOUT_SECONDS` configura el tiempo de espera de las operaciones (con un tope de 60 segundos). Un timeout de una escritura puede dejar cambios parciales en MongoDB; no debe reintentarse sin revisar el estado.
- El código guardado admite hasta 20000 caracteres; nombre hasta 100 y descripción hasta 500.
- El límite de filas **no limita cuántos registros modifica una escritura**. No existe deshacer automático.
- Las escrituras MongoDB no son transacciones entre varios documentos: un error puede dejar cambios parciales. La confirmación se vacía en cada intento; revisa los datos antes de volver a ejecutar una operación fallida.
- Los contadores de cambios se muestran cuando están disponibles; las agregaciones de escritura MongoDB no proporcionan aquí un conteo exacto de documentos modificados.
- La autenticación es una clave administrativa compartida, no un sistema multiusuario con roles. Usa HTTPS y acceso de red restringido si despliegas la función fuera del equipo local.

**Importante:** las escrituras personalizadas no pasan por el ETL, no aplican sus reglas de normalización y no sincronizan MongoDB y Neo4j ni reconstruyen `aspirantes` o `resumen_carrera`. El administrador debe mantener explícitamente la coherencia de los modelos. Para importar datos normales, sigue usando **Carga de datos**.

## API

| Método | Ruta | Acción |
|---|---|---|
| GET | `/api/consultas/guardadas?motor=MONGODB` | Lista definiciones MongoDB |
| GET | `/api/consultas/guardadas?motor=NEO4J` | Lista definiciones Cypher |
| POST | `/api/consultas/guardadas` | Guarda nombre, descripción, motor y código |
| POST | `/api/consultas/guardadas/{id}/ejecutar` | Ejecuta una definición guardada |

El POST de ejecución de una escritura requiere `?confirmarEscritura=true`. Sin él responde 409, sin ejecutar la operación. Una clave incorrecta responde 401; definición o sintaxis no admitida, 400; identificador inexistente, 404. Guardar responde 201.

## Verificación

Se verificaron autorización, persistencia real en MongoDB, listado posterior y operaciones de lectura/escritura de ambos motores con registros temporales identificados. Se comprobaron las cinco operaciones MongoDB, Cypher de lectura, creación y eliminación de nodos, y rechazo de escrituras sin confirmación.

En Chrome se comprobó guardar, recargar y volver a abrir una consulta MongoDB, resultados Neo4j en tabla, borrado de la clave en memoria al recargar, bloqueo de escrituras hasta confirmar, ausencia de errores de React/consola y diseño a 1440 y 390 px. Los registros y definiciones temporales de estas pruebas fueron eliminados selectivamente; no se cargó el dataset de muestra.
