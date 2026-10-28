# Proyecto Final — Pruebas Específicas USAC

Aplicación web para cargar, transformar, almacenar y analizar las evaluaciones de las **pruebas específicas** de aspirantes de primer ingreso a la Facultad de Ingeniería de la Universidad de San Carlos de Guatemala.

El proyecto combina **MongoDB** para el análisis documental y **Neo4j** para el análisis de relaciones, con un backend **Java Spring Boot** y un frontend **React**.

## Funcionalidades

- ETL de archivos CSV con detección de separador, normalización de encabezados, validación por fila y reporte de rechazos.
- Carga asíncrona de uno o varios archivos con progreso, tiempos, duplicados omitidos y registros rechazados.
- Carga idempotente: la misma fila no se inserta dos veces gracias a un índice único sobre `hash_fila`.
- Dos representaciones en MongoDB: colección plana `evaluaciones` y colección embebida `aspirantes`.
- Colección auxiliar `resumen_carrera` generada con `$merge`.
- **20 consultas analíticas** de MongoDB con filtros y visualizaciones.
- **7 consultas Cypher** en Neo4j para explorar nodos, relaciones, comunidades y caminos.
- Dashboard con indicadores y gráficas; explorador de grafos con filtros y detalle de nodos; buscador unificado.
- Manejo global de errores con mensajes en español.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1.2, Maven Wrapper |
| Frontend | React 18.3.1, Vite 5.4.14, React Router 6, Axios, Recharts, react-force-graph-2d |
| Documental | MongoDB 7 |
| Grafos | Neo4j 5 Community |
| Infraestructura local | Docker Compose |

## Requisitos

- JDK 21 o superior (el proyecto se compiló con JDK 22).
- Docker y Docker Compose.
- Node.js 20 o superior y npm 10 o superior.
- Puertos libres: `8080` (backend), `5173` (frontend), `27017` (MongoDB), `7474` y `7687` (Neo4j).

## Inicio rápido

### 1. Bases de datos

```bash
docker compose up -d
docker compose ps
```

Servicios:

- MongoDB: `mongodb://localhost:27017/evaluaciones`
- Neo4j Browser: <http://localhost:7474>
- Neo4j Bolt: `bolt://localhost:7687`
- Usuario/contraseña Neo4j: `neo4j` / `evaluaciones2026`

### 2. Backend

```bash
cd proyecto-final-db2
./mvnw spring-boot:run
```

Verificar:

```bash
curl http://localhost:8080/api/salud
```

Debe responder `"estado": "OK"` y mostrar las colecciones.

### 3. Frontend

En otra terminal:

```bash
cd frontend-proyecto-final-db2
npm install
npm run dev
```

Abrir <http://localhost:5173>. Vite redirige `/api` al backend en `http://localhost:8080`.

### 4. Cargar datos

Desde la interfaz, entrar a **Carga de datos** y subir uno o varios CSV. También se puede usar `curl`:

```bash
curl -X POST \
  -F "files=@etl/muestras/pruebas_especificas_muestra.csv" \
  http://localhost:8080/api/cargas/archivos
```

El endpoint responde `202 Accepted` con el identificador de la carga. El estado se consulta en:

```bash
curl http://localhost:8080/api/cargas/{id}
```

## Configuración

El backend usa variables de entorno con valores locales por defecto:

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `MONGO_URI` | `mongodb://localhost:27017/evaluaciones` | URI de MongoDB |
| `NEO4J_URI` | `bolt://localhost:7687` | URI Bolt |
| `NEO4J_USER` | `neo4j` | Usuario de Neo4j |
| `NEO4J_PASSWORD` | `evaluaciones2026` | Contraseña de Neo4j |
| `ETL_BATCH_SIZE` | `1000` | Filas por lote |
| `ETL_TEMP_DIR` | vacío | Directorio base temporal para las cargas |
| `CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000` | Orígenes permitidos |

En el frontend, `VITE_API_URL` permite cambiar la URL base de la API. Si no se define, se usa `/api`.

## Estructura del repositorio

```text
ProyectoFinal/
├── README.md
├── docker-compose.yml
├── etl/
│   ├── muestras/
│   │   └── pruebas_especificas_muestra.csv
│   └── validar_dataset.md
├── proyecto-final-db2/          # Backend Spring Boot
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/db2/proyectofinaldb2/
│       └── test/java/com/db2/proyectofinaldb2/
├── frontend-proyecto-final-db2/ # Frontend React + Vite
│   └── src/
├── documentacion/              # Entregables versionados
└── docs/                       # Notas internas, ignoradas por Git
```

## Endpoints principales

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/salud` | Estado de MongoDB, Neo4j y conteos |
| POST | `/api/cargas/archivos` | Carga asíncrona de CSV (`multipart`) |
| GET | `/api/cargas` | Historial de cargas |
| GET | `/api/cargas/{id}` | Estado y reporte de una carga |
| GET | `/api/cargas/{id}/rechazados` | Registros rechazados paginados |
| GET | `/api/consultas/mongo/1..20` | Consultas analíticas de MongoDB |
| POST | `/api/consultas/mongo/6/resumen` | Regenera `resumen_carrera` |
| GET | `/api/grafo/consultas/1..7` | Consultas Cypher tabulares |
| GET | `/api/grafo/visualizacion` | Subgrafo con filtros |
| GET | `/api/grafo/nodo/{id}` | Detalle y vecinos de un nodo |
| GET | `/api/buscar?tipo=...&q=...` | Búsqueda unificada |
| GET | `/api/catalogos/{tipo}` | Catálogos para filtros |
| GET | `/api/aspirantes/{correlativo}` | Ficha embebida del aspirante |
| GET | `/api/aspirantes/{correlativo}/historial` | Historial de evaluaciones |

La referencia completa está en [`documentacion/manual-tecnico.md`](documentacion/manual-tecnico.md).

## Pruebas

```bash
cd proyecto-final-db2
./mvnw test
```

Incluye pruebas unitarias del ETL (`TransformationServiceTest`) y de detección de separadores CSV (`CsvSupportTest`).

Compilación del frontend:

```bash
cd frontend-proyecto-final-db2
npm run build
```

## Datos

El portal oficial de Datos Abiertos de la Facultad de Ingeniería no estuvo disponible durante el desarrollo (respondió HTTP 500). Por ello, el repositorio incluye una **muestra sintética pequeña** para validar el ETL, las consultas y la interfaz. No debe interpretarse como el dataset real ni como resultados oficiales.

Para cargar el dataset real, descargue los CSV desde el portal y súbalos desde la interfaz. El ETL reconoce los encabezados documentados y varias variantes habituales. Consulte [`etl/validar_dataset.md`](etl/validar_dataset.md).

## Documentación

- [Manual técnico](documentacion/manual-tecnico.md)
- [Manual de usuario](documentacion/manual-usuario.md)
- [Arquitectura](documentacion/arquitectura.md)
- [Modelamiento MongoDB](documentacion/modelamiento-mongodb.md)
- [Modelamiento Neo4j](documentacion/modelamiento-neo4j.md)
- [Reglas ETL](documentacion/etl-reglas.md)
- [Consultas MongoDB](documentacion/consultas-mongodb.md)
- [Consultas Neo4j](documentacion/consultas-neo4j.md)
- [Análisis de resultados](documentacion/analisis-resultados.md)
- [Conclusiones](documentacion/conclusiones.md)
- [Pruebas y validación](documentacion/pruebas.md)
- [Validación del dataset oficial](etl/validar_dataset.md)

## Estado

Proyecto funcional en entorno local: backend, frontend, MongoDB y Neo4j probados de extremo a extremo con la muestra de validación. La principal limitación externa es la indisponibilidad temporal del portal de datos para incorporar el conjunto oficial completo.

Antes de entregar: incorporar el dataset oficial si vuelve a estar disponible, actualizar el análisis y completar el historial de al menos cinco commits significativos. El frontend activo está en `frontend-proyecto-final-db2/`.
