# Manual técnico

## 1. Descripción general

Aplicación web para cargar, transformar, almacenar, consultar y visualizar las evaluaciones de las **pruebas específicas** de aspirantes a la Facultad de Ingeniería de la Universidad de San Carlos de Guatemala.

La solución usa persistencia políglota:

- **MongoDB** para documentos de evaluaciones, aspirantes, cargas, rechazos y resumen por carrera.
- **Neo4j** para aspirantes, carreras, materias, evaluaciones, instituciones y la jerarquía geográfica.
- **Spring Boot** como API REST y orquestador del ETL.
- **React** como interfaz de usuario.

Documentos relacionados:

- [Arquitectura](arquitectura.md)
- [Modelamiento MongoDB](modelamiento-mongodb.md)
- [Modelamiento Neo4j](modelamiento-neo4j.md)
- [Reglas ETL](etl-reglas.md)
- [Consultas MongoDB](consultas-mongodb.md)
- [Consultas Neo4j](consultas-neo4j.md)

## 2. Requisitos

| Componente | Versión mínima | Verificación |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven Wrapper | incluido | `./mvnw -v` |
| Node.js | 20 | `node -v` |
| npm | 10 | `npm -v` |
| Docker | 24 | `docker --version` |
| Docker Compose | v2 | `docker compose version` |

Puertos usados:

| Servicio | Puerto |
|---|---|
| Frontend Vite | 5173 |
| Backend Spring Boot | 8080 |
| MongoDB | 27017 |
| Neo4j HTTP | 7474 |
| Neo4j Bolt | 7687 |

## 3. Estructura del repositorio

```text
ProyectoFinal/
├── README.md
├── docker-compose.yml
├── .gitignore
├── etl/
│   ├── muestras/pruebas_especificas_muestra.csv
│   └── validar_dataset.md
├── proyecto-final-db2/
│   ├── pom.xml
│   ├── mvnw
│   └── src/
│       ├── main/java/com/db2/proyectofinaldb2/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── exception/
│       │   ├── neo4j/
│       │   ├── repository/
│       │   ├── service/
│       │   └── util/
│       ├── main/resources/application.yml
│       └── test/java/...
├── frontend-proyecto-final-db2/
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/
│       ├── components/
│       ├── hooks/
│       ├── pages/
│       └── utils/
├── documentacion/
└── docs/                 # notas internas, ignoradas por Git
```

## 4. Instalación y ejecución

### 4.1 Clonar y entrar al proyecto

```bash
git clone <URL-del-repositorio>
cd ProyectoFinal
```

### 4.2 Bases de datos

```bash
docker compose up -d
docker compose ps
```

El `docker-compose.yml` define:

- `db2-mongo`: MongoDB 7, puerto `27017`, volumen persistente.
- `db2-neo4j`: Neo4j 5 Community, puertos `7474` y `7687`, autenticación definida en su `.env` privado.

Verificar:

```bash
docker logs db2-mongo --tail 20
docker logs db2-neo4j --tail 20
```

Neo4j Browser: <http://localhost:7474>.

Para detener sin borrar datos:

```bash
docker compose down
```

Para borrar también los volúmenes (pierde los datos):

```bash
docker compose down -v
```

### 4.3 Backend

```bash
cd proyecto-final-db2
./mvnw spring-boot:run
```

La primera ejecución descarga Maven 3.10 y las dependencias. Al arrancar, el backend:

1. crea los índices de MongoDB;
2. crea las restricciones e índices de Neo4j;
3. queda escuchando en `http://localhost:8080`.

Verificar:

```bash
curl http://localhost:8080/api/salud
```

Respuesta esperada:

```json
{
  "estado": "OK",
  "mongo": { "disponible": true, "detalle": "Conectado" },
  "neo4j": { "disponible": true, "detalle": "Conectado" },
  "colecciones": {
    "evaluaciones": 27,
    "aspirantes": 12,
    "resumen_carrera": 6,
    "registros_rechazados": 1
  }
}
```

Los conteos dependen de los datos cargados.

### 4.4 Frontend

En otra terminal:

```bash
cd frontend-proyecto-final-db2
npm install
npm run dev
```

Abrir <http://localhost:5173>. Vite redirige `/api` a `http://localhost:8080`.

Para producción:

```bash
npm run build
npm run preview
```

El resultado queda en `frontend-proyecto-final-db2/dist`.

## 5. Configuración

### 5.1 Backend

Archivo privado: `proyecto-final-db2/.env`. El backend lo carga antes de configurar los drivers. Los valores del sistema operativo y de la línea de comandos tienen prioridad. No hay contraseñas predeterminadas en Java ni conexiones interpoladas en YAML.

La lista de nombres requeridos y las instrucciones de instalación están en [configuracion-local.md](configuracion-local.md). Debes preparar los `.env` antes del primer arranque, ya que no se incluyen al clonar el repositorio.

### 5.2 Frontend

Archivo privado: `frontend-proyecto-final-db2/.env`. Vite lee la URL pública de la API, el destino privado del proxy de desarrollo y el puerto del frontend. Reinicia Vite después de modificarlo. Las variables `VITE_*` son públicas y nunca deben incluir credenciales.

### 5.3 Límites de carga

Se configuran exclusivamente en el `.env` del backend mediante `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` y `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`.

Si el dataset real supera estos límites, ajuste los valores y reinicie el backend.

## 6. API REST

Base: `http://localhost:8080/api`.

### 6.1 Salud

`GET /salud`

Comprueba MongoDB y Neo4j con un `ping` y devuelve conteos estimados. Si una base falla, el campo `disponible` es `false` y el estado global pasa a `DEGRADADO`.

### 6.2 Cargas

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/cargas/archivos` | Sube uno o varios CSV (`multipart/form-data`, campo `files`). Responde `202`. |
| GET | `/cargas` | Últimas 50 cargas. |
| GET | `/cargas/{id}` | Estado y reporte de una carga. |
| GET | `/cargas/{id}/rechazados?page=0&size=50` | Rechazos paginados de la carga. |

Ejemplo:

```bash
curl -X POST \
  -F "files=@etl/muestras/pruebas_especificas_muestra.csv" \
  http://localhost:8080/api/cargas/archivos
```

Estados:

- `EN_PROCESO`: el job sigue ejecutándose.
- `EXITOSA`: no hubo rechazos ni errores.
- `EXITOSA_CON_RECHAZOS`: se insertaron filas y al menos una fue rechazada.
- `ERROR`: falló una base o el archivo no se pudo procesar.

### 6.3 Consultas MongoDB

`GET /consultas/mongo/{1..20}`

Parámetros opcionales: `anio`, `materia`, `carrera`, `depto`, `tipo`, `limit`, `correlativo`.

```bash
curl "http://localhost:8080/api/consultas/mongo/4?materia=MATEMATICA"
curl "http://localhost:8080/api/consultas/mongo/11?correlativo=A1B2C3D4"
```

`POST /consultas/mongo/6/resumen` regenera `resumen_carrera` y devuelve el resumen resultante.

`GET /aspirantes/{correlativo}` devuelve la ficha embebida (consulta 15).

`GET /aspirantes/{correlativo}/historial` devuelve el historial plano (consulta 11).

### 6.4 Grafos

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/grafo/consultas/{1..7}` | Consultas Cypher tabulares. |
| GET | `/grafo/visualizacion` | Subgrafo para el explorador. |
| GET | `/grafo/nodo/{elementId}` | Detalle de un nodo y sus vecinos. |

Parámetros de filtro: `carrera`, `depto`, `materia`, `tipo`, `anio`, `limit`.

```bash
curl "http://localhost:8080/api/grafo/consultas/3?anio=2018&limit=20"
curl "http://localhost:8080/api/grafo/consultas/7?depto=GUATEMALA&carrera=MEDICINA"
curl "http://localhost:8080/api/grafo/visualizacion?depto=GUATEMALA&limit=100"
```

El identificador de nodo contiene dos puntos; debe codificarse en la URL:

```bash
curl "http://localhost:8080/api/grafo/nodo/4%3Aabc%3A123"
```

### 6.5 Búsqueda y catálogos

`GET /buscar?tipo={aspirante|carrera|materia|institucion|municipio|departamento}&q=texto`

`GET /catalogos/{tipo}` con `tipo` en `materias`, `carreras`, `departamentos`, `municipios`, `tipos`, `anios`.

### 6.6 Errores

La biblioteca administrativa agrega `/api/consultas/guardadas` para listar/guardar definiciones y `POST /api/consultas/guardadas/{id}/ejecutar`. Todas sus rutas requieren la clave privada; la escritura requiere confirmación. Ver [consultas-personalizadas.md](consultas-personalizadas.md) para contratos, ejemplos y límites. Las consultas predefinidas conservan sus rutas originales.

Formato uniforme:

```json
{
  "timestamp": "2026-10-26T17:38:39.821Z",
  "status": 404,
  "error": "Not Found",
  "mensaje": "La ruta solicitada no existe",
  "ruta": "/api/nope"
}
```

Códigos:

| Código | Significado |
|---|---|
| 400 | Parámetro faltante, inválido o consulta mal formada. |
| 404 | Recurso, consulta o ruta inexistente. |
| 413 | Archivo supera el tamaño permitido. |
| 500 | Error inesperado. |
| 503 | MongoDB o Neo4j no disponible. |

## 7. ETL

Resumen del flujo:

1. El controlador recibe los archivos y `CargaService` los copia a un directorio temporal único.
2. Se crea un `CargaJob` y se registra la carga en MongoDB.
3. El executor procesa cada archivo en segundo plano.
4. `CsvSupport` detecta el delimitador y lee el contenido como UTF-8.
5. `TransformationService` mapea encabezados, valida y transforma cada fila.
6. `EvaluationRepository.insertBatch` inserta en `evaluaciones`; los duplicados se cuentan.
7. `EtlService` envía lotes a Neo4j con `MERGE`.
8. Al finalizar, se reconstruye `aspirantes` con `$out` y se persiste el reporte.
9. Se elimina el directorio temporal de esa carga.

Las reglas completas están en [etl-reglas.md](etl-reglas.md).

## 8. Consultas y código

| Recurso | Ubicación |
|---|---|
| 20 consultas MongoDB | `service/MongoQueryService.java` |
| 7 consultas Cypher | `neo4j/CypherCatalog.java` |
| Visualización y detalle de nodos | `service/GraphService.java` |
| Acceso a MongoDB | `repository/EvaluationRepository.java` |
| Búsqueda | `service/SearchService.java` |
| Catálogos | `service/CatalogService.java` |
| Rutas REST | `controller/` |
| Errores | `exception/GlobalExceptionHandler.java` |

## 9. Pruebas

### 9.1 Backend

```bash
cd proyecto-final-db2
./mvnw test
```

Cubre:

- `TransformationServiceTest`: alias de encabezados, normalización de sexo/tipo, edad, hash, claves de grafo y rechazos.
- `CsvSupportTest`: detección de coma, punto y coma, tabulador y comillas.

Resultado esperado: 21 pruebas, sin fallas ni errores, incluyendo la regresión del explorador, configuración privada y políticas de consultas personalizadas.

### 9.2 Frontend

```bash
cd frontend-proyecto-final-db2
npm run build
```

Debe terminar con `built in ...`. El aviso de tamaño del bundle no es un error.

### 9.3 Prueba de humo manual

1. `GET /api/salud` → `estado: OK`.
2. Subir `etl/muestras/pruebas_especificas_muestra.csv` → `202`.
3. Consultar la carga hasta `EXITOSA_CON_RECHAZOS`.
4. Abrir el dashboard y verificar los KPI.
5. Probar la consulta 4 y su gráfica.
6. Probar la consulta 11 con `A1B2C3D4`.
7. Abrir el explorador, filtrar por `GUATEMALA` y hacer clic en un nodo.
8. Buscar `MEDICINA` y abrir una ficha.
9. Verificar respuestas 404/400 en rutas y parámetros inválidos.

Evidencia detallada en [pruebas.md](pruebas.md).

### 9.4 Empaquetado

```bash
cd proyecto-final-db2
./mvnw -DskipTests package
java -jar target/proyecto-final-db2-0.0.1-SNAPSHOT.jar
```

## 10. Solución de problemas

### El backend no conecta a MongoDB

- Verificar `docker compose ps`.
- Revisar `docker logs db2-mongo`.
- Confirmar `SPRING_MONGODB_URI` en el `.env` del backend.
- Si el puerto 27017 está ocupado, cambiar el mapeo en `docker-compose.yml` y la URI.

### El backend no conecta a Neo4j

- Esperar 20–40 segundos después de `docker compose up`; Neo4j tarda en iniciar.
- Revisar `docker logs db2-neo4j`.
- Confirmar usuario y contraseña.
- Probar `http://localhost:7474`.

### `Port 8080 was already in use`

```bash
lsof -i :8080
kill <PID>
```

O cambiar `server.port`.

### El frontend muestra errores de red

- Confirmar que el backend responde en `http://localhost:8080/api/salud`.
- Revisar la consola del navegador.
- Si se usa `VITE_API_URL`, confirmar que no termine en `/` duplicado.
- Si se accede desde otro host, agregar el origen a `APP_CORS_ALLOWED_ORIGINS` en el `.env` del backend.

### Los acentos se ven mal en el CSV

El ETL lee UTF-8 y reemplaza bytes inválidos. Si un archivo usa Latin-1, conviértalo a UTF-8 antes de cargarlo:

```bash
iconv -f LATIN1 -t UTF-8 archivo.csv > archivo-utf8.csv
```

### Muchas filas rechazadas

- Abrir **Carga → Ver rechazados** y revisar la causa.
- Verificar encabezados contra la tabla de alias.
- Revisar que el separador no esté dentro de campos sin comillas.
- Confirmar que `aprobacion` use un valor reconocido.
- Confirmar que `anio_ingreso` sea un año de cuatro dígitos.

### Los conteos de MongoDB y Neo4j no coinciden

- Ejecutar una nueva carga del mismo archivo: el ETL es idempotente.
- Verificar que la carga haya terminado en `EXITOSA` o `EXITOSA_CON_RECHAZOS`.
- En Neo4j, contar nodos:

```cypher
MATCH (n) RETURN labels(n)[0] AS tipo, count(*) AS total ORDER BY total DESC;
```

### El grafo está vacío

- Confirmar que hay datos en Neo4j.
- Quitar filtros agresivos.
- Probar `GET /api/grafo/visualizacion?limit=50`.
- Si se filtra por materia o año, recordar que la visualización usa `OPTIONAL MATCH`; un filtro sobre `e` o `mat` exige que exista una evaluación que cumpla.

## 11. Despliegue recomendado

1. Compilar backend y frontend.
2. Servir `frontend-proyecto-final-db2/dist` con Nginx.
3. Configurar Nginx para redirigir `/api` al backend.
4. Ejecutar MongoDB y Neo4j en una red privada.
5. Cambiar credenciales y usar secretos.
6. No exponer los puertos 27017/7687 a Internet.
7. Configurar respaldos de MongoDB y Neo4j.
8. Definir `APP_CORS_ALLOWED_ORIGINS` con el dominio real en la configuración privada.

## 12. Limitaciones conocidas

- No hay autenticación ni autorización; el proyecto es local/académico.
- No hay transacción distribuida entre MongoDB y Neo4j; la consistencia se logra con reintentos idempotentes.
- El portal oficial de datos no estuvo disponible durante el desarrollo; la validación se hizo con una muestra sintética.
- La institución educativa se aproxima por tipo + municipio + departamento porque el dataset no incluye el nombre.
- El progreso de una carga se conserva en memoria; si el backend se reinicia a mitad de un job, la carga queda en `EN_PROCESO` y debe reintentarse.
