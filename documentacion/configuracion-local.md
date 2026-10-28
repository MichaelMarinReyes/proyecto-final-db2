# Configuración privada con `.env`

Los valores locales y credenciales no se versionan. El repositorio conserva únicamente código que lee los nombres de configuración y las rutas de los archivos. No se entrega un `.env.example` con valores operativos.

## Archivos que debes preparar en cada equipo

| Archivo privado | Consumidor |
|---|---|
| `proyecto-final-db2/.env` | Spring Boot mediante `DotenvEnvironmentPostProcessor` |
| `frontend-proyecto-final-db2/.env` | Vite |
| `infra/mongo/.env` | Contenedor MongoDB mediante `env_file` |
| `infra/neo4j/.env` | Contenedor Neo4j mediante `env_file` |

Estos cuatro archivos ya están preparados en el equipo de desarrollo actual, pero **no estarán al clonar GitHub**. Obtén sus valores por un canal privado o define tus propios valores para una instalación nueva. Crea también las carpetas `infra/mongo/` e `infra/neo4j/` si no existen.

Usa sintaxis dotenv (`NOMBRE=valor`), una entrada por línea y sin `export`. Las cadenas con espacios o `#` deben ir entre comillas. Mantén permisos restrictivos sobre estos archivos.

## Backend

Define en su `.env`:

| Nombre | Significado |
|---|---|
| `SERVER_PORT` | Puerto HTTP del backend |
| `SERVER_ERROR_INCLUDE_MESSAGE` | Política de mensajes del framework; evitar diagnósticos técnicos públicos |
| `SPRING_MONGODB_URI` | URI completa de MongoDB y base seleccionada |
| `SPRING_NEO4J_URI` | URI de Neo4j |
| `SPRING_NEO4J_AUTHENTICATION_USERNAME` | Usuario de Neo4j |
| `SPRING_NEO4J_AUTHENTICATION_PASSWORD` | Contraseña de Neo4j, sin fallback en Java |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | Tamaño máximo por archivo |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | Tamaño máximo total de la petición |
| `APP_ETL_BATCH_SIZE` | Tamaño positivo del lote ETL |
| `APP_ETL_TEMP_DIR` | Directorio base temporal; puede quedar vacío |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes autorizados, separados por comas |
| `LOGGING_LEVEL_COM_DB2_PROYECTOFINALDB2` | Nivel de logs del paquete de aplicación |
| `APP_QUERIES_ADMIN_KEY` | Clave privada de al menos 32 caracteres para las consultas personalizadas; nunca debe ir al frontend |
| `APP_QUERIES_MAX_ROWS` | Máximo de filas presentadas por una consulta personalizada |
| `APP_QUERIES_TIMEOUT_SECONDS` | Tiempo de espera de consultas personalizadas |

El cargador busca el `.env` del backend tanto al ejecutar desde la raíz del repositorio como desde `proyecto-final-db2/`. Para ejecutar un JAR desde otra ubicación, coloca el `.env` en el directorio de trabajo o proporciona la configuración desde el entorno del proceso. No se empaqueta el `.env` en el JAR.

La configuración de sistema/CLI prevalece sobre el archivo. Spring interpreta los nombres en mayúsculas como propiedades equivalentes, sin necesitar `${...}` en `application.yml`.

Durante la migración se comprobó que la configuración anterior de MongoDB no seleccionaba la base prevista: los datos existentes estaban en otra base. El `.env` local se ajustó para seguir accediendo a esos datos, sin copiarlos ni borrarlos. Para una instalación nueva, elige explícitamente la base en `SPRING_MONGODB_URI`; para renombrar una base existente, planifica una migración independiente.

## Frontend

| Nombre | Significado |
|---|---|
| `VITE_API_URL` | URL pública HTTP de la API, o ruta relativa al proxy |
| `API_PROXY_TARGET` | Destino HTTP del backend para el servidor de desarrollo Vite |
| `FRONTEND_PORT` | Puerto del servidor de desarrollo |

**Todo valor `VITE_*` llega al navegador.** Un `.env` ignorado por Git no convierte esas variables en secretos. No copies contraseñas, tokens privados ni URIs de bases de datos al frontend. `API_PROXY_TARGET` y `FRONTEND_PORT` se leen en la configuración de Vite sin ampliar el prefijo de exposición al cliente.

Después de cambiar el `.env`, reinicia el proceso correspondiente. Para cambiar variables `VITE_*` de un despliegue estático debes volver a compilar el frontend.

## Docker

- MongoDB: configura `MONGO_INITDB_DATABASE` en `infra/mongo/.env`.
- Neo4j: configura `NEO4J_AUTH` con el formato de autenticación requerido por la imagen y las opciones de memoria necesarias en `infra/neo4j/.env`.
- Mantén coherentes la autenticación de Neo4j y la configuración del backend.
- Cambiar `NEO4J_AUTH` no cambia automáticamente la contraseña ya almacenada en un volumen existente. Usa el mecanismo de cambio de contraseña de Neo4j y actualiza ambos archivos privados. **No borres los volúmenes para cambiar una contraseña.**

Validación de Compose sin imprimir los valores resueltos:

```bash
docker compose config --quiet
```

## Git y secretos publicados anteriormente

`.gitignore` excluye `.env` y sus variantes en cualquier nivel. Confirma antes de cada entrega:

```bash
git ls-files '*env*'
git status --short
```

Ninguno de los cuatro archivos privados debe aparecer como versionado. Si un secreto estuvo publicado anteriormente, quitarlo del árbol actual **no lo elimina del historial ni invalida copias existentes**. Rota ese secreto y coordina por separado una limpieza del historial si es necesaria; esta migración no reescribe Git.
