# Reglas del ETL

## 1. Objetivo

Convertir uno o varios archivos CSV del dataset **Pruebas específicas** en documentos de MongoDB y nodos/relaciones de Neo4j, con validación por fila, reporte de rechazos e idempotencia.

El ETL se ejecuta de forma asíncrona: el endpoint de carga responde `202 Accepted` y el trabajo continúa en un pool de dos hilos.

## 2. Entrada

- Uno o varios archivos con extensión `.csv`. El backend rechaza otras extensiones.
- Tamaño máximo por archivo: 100 MB; tamaño máximo por petición: 500 MB.
- Codificación: UTF-8, con o sin BOM. Los bytes UTF-8 inválidos se reemplazan; convierta los archivos Latin-1 antes de cargarlos para no perder acentos.
- Delimitador: coma, punto y coma o tabulador; se detecta con la primera línea fuera de comillas.
- Primera fila: encabezados.

## 3. Encabezados reconocidos

Los encabezados se normalizan (minúsculas, sin acentos, sin espacios sobrantes, separadores a `_`) y se mapean a un nombre canónico. Se aceptan, entre otras, estas variantes:

| Campo canónico | Encabezados aceptados |
|---|---|
| `correlativo` | `Correlativo aspirante`, `Correlativo del aspirante`, `Correlativo`, `Correlativo estudiante`, `No. correlativo` |
| `fecha_asignacion` | `Fecha asignación`, `Fecha de asignación`, `Fecha de evaluación`, `Fecha evaluación` |
| `sexo` | `Sexo`, `Género` |
| `anio_nacimiento` | `Año nacimiento`, `Año de nacimiento`, `Anio nacimiento`, `Ano de nacimiento` |
| `materia` | `Materia`, `Materia evaluada`, `Curso` |
| `oportunidad` | `Número de fecha de evaluación`, `Número de evaluación`, `Número de intento`, `No. de fecha de evaluación`, `Oportunidad` |
| `anio_ingreso` | `Año de ingreso`, `Año ingreso`, `Anio de ingreso`, `Ano ingreso` |
| `aprobacion` | `Aprobación`, `Resultado`, `Resultado evaluación` |
| `carrera` | `Carrera objetivo`, `Carrera a la que aspira`, `Carrera solicitada` |
| `departamento` | `Departamento institución educativa`, `Departamento de la institución educativa`, `Departamento IE` |
| `municipio` | `Municipio institución educativa`, `Municipio de la institución educativa`, `Municipio IE` |
| `tipo` | `Tipo institución educativa`, `Tipo de institución educativa`, `Tipo IE`, `Sector` |

Si falta un encabezado obligatorio, el archivo se marca con error antes de procesar sus filas. Los encabezados requeridos son `correlativo`, `materia`, `anio_ingreso` y `aprobacion`; `oportunidad` también es obligatorio en la práctica porque el modelo lo exige.

## 4. Pipeline por fila

```text
leer fila
  ↓
limpiar BOM, espacios y saltos
  ↓
mapear columnas por encabezado
  ↓
validar correlativo, materia, año, oportunidad y aprobación
  ↓
normalizar sexo, tipo de institución, carrera, departamento y municipio
  ↓
calcular edad, fecha, booleanos, claves de grafo y hash
  ↓
insertar en MongoDB (o registrar duplicado)
  ↓
acumular lote para Neo4j
  ↓
al finalizar: reconstruir aspirantes, persistir reporte y limpiar temporales
```

## 5. Validaciones y transformaciones

### 5.1 Campos obligatorios

| Campo | Regla | Causa de rechazo |
|---|---|---|
| `correlativo` | No vacío después de limpiar. | `correlativo_aspirante vacío` |
| `materia` | No vacía; se convierte a mayúsculas. | `materia vacía` |
| `anio_ingreso` | Entero entre 1990 y 2100. | `anio_de_ingreso inválido` |
| `oportunidad` | Entero ≥ 1. Si viene vacío, se rechaza. | `numero_de_fecha_de_evaluacion inválido` |
| `aprobacion` | `APROBADO`, `NO APROBADO`, `SI`, `SÍ`, `NO`, `1`, `0` (sin distinguir mayúsculas). | `aprobacion no reconocida: ...` |

### 5.2 Campos opcionales

| Campo | Transformación |
|---|---|
| `fecha_asignacion` | Se prueban formatos ISO, `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy/MM/dd`, `d/M/yyyy` y variantes con hora. Si no se reconoce, queda `null`; no rechaza la fila. |
| `anio_nacimiento` | Se parsea como entero; `SIN REGISTRO`, vacío o inválido produce `null`. |
| `edad_ingreso` | `anio_ingreso - anio_nacimiento`, solo si el resultado está entre 10 y 80; de lo contrario `null`. |
| `sexo` | `M`/`Masculino` → `M`; `F`/`Femenino` → `F`; otro valor → `DESCONOCIDO`. Se ignoran acentos. |
| `carrera_objetivo` | Mayúsculas; vacío → `NINGUNO`. |
| `departamento_ie`, `municipio_ie` | Mayúsculas; vacío → `SIN REGISTRO`. |
| `tipo_ie` | `Público`, `Pública`, `Municipal`, `Oficial` o `Estatal` → `PUBLICO`; `Privado`, `Privada` o `Particular` → `PRIVADO`; vacío → `SIN REGISTRO`; otro valor se conserva en mayúsculas. |

### 5.3 Campos derivados

| Campo | Cálculo |
|---|---|
| `aprobacion` | Texto canónico `APROBADO` / `NO APROBADO`. |
| `aprobado` | Booleano usado en índices y agregaciones. |
| `hash_fila` | SHA-256 de correlativo, fecha, sexo, año de nacimiento, materia, oportunidad, año de ingreso, aprobación, carrera, departamento, municipio y tipo. |
| `evalKey` (grafo) | `correlativo|materia|anio_ingreso|oportunidad`. |
| `municipioKey` (grafo) | `departamento|municipio`. |
| `institucionKey` (grafo) | `tipo|municipio|departamento`. |

## 6. Idempotencia y duplicados

- MongoDB tiene un índice único y sparse sobre `hash_fila`.
- La inserción usa `insertMany` con `ordered(false)`; los errores de clave duplicada (código 11000) se cuentan como `duplicadosOmitidos`.
- Neo4j usa `MERGE` por clave de negocio, por lo que volver a cargar el mismo archivo actualiza propiedades y no crea nodos duplicados.
- Reconstruir `aspirantes` con `$out` garantiza que la vista embebida no acumule versiones viejas.

### Ejemplo real de la muestra

Segunda carga del mismo archivo:

```json
{
  "leidos": 29,
  "procesados": 28,
  "rechazados": 1,
  "duplicados": 28
}
```

Las colecciones permanecieron en 27 evaluaciones y 12 aspirantes. El rechazo se volvió a registrar porque `registros_rechazados` conserva el historial de cada carga.

## 7. Procesamiento por lotes

- `ETL_BATCH_SIZE` define cuántas filas se acumulan antes de insertar en Mongo y enviar a Neo4j (1000 por defecto).
- Si un lote de Mongo falla por causas distintas a duplicados, el job termina con error y el reporte conserva el mensaje.
- Si falla Neo4j, el lote de Mongo ya pudo haberse insertado. La carga se marca con error y puede reintentarse; la idempotencia evita duplicar lo ya insertado.
- No hay transacción distribuida entre MongoDB y Neo4j; la consistencia se obtiene por reintento idempotente y por la reconstrucción de agregados desde `evaluaciones`.

## 8. Reporte de carga

Cada carga tiene:

- `id`, estado (`EN_PROCESO`, `EXITOSA`, `EXITOSA_CON_RECHAZOS`, `ERROR`);
- fechas de inicio/fin, duración y progreso;
- totales: leídos, procesados, rechazados y duplicados;
- detalle por archivo: leídos, procesados, rechazados, duplicados, duración, estado y errores;
- errores con número de fila, causa y una muestra truncada del contenido.

Los rechazos se guardan en `registros_rechazados` con `carga_id`, archivo, fila, causa, valor y fecha. La API los expone paginados en `GET /api/cargas/{id}/rechazados?page=0&size=50`.

## 9. Limpieza de temporales

Cada carga crea un subdirectorio único dentro del directorio temporal del sistema (o dentro de `ETL_TEMP_DIR` si está configurado). Al finalizar, se elimina **solo ese subdirectorio**, no todo el directorio base. Esto evita borrar archivos de otras cargas o del usuario.

## 10. Ejecución manual

```bash
curl -X POST \
  -F "files=@etl/muestras/pruebas_especificas_muestra.csv" \
  http://localhost:8080/api/cargas/archivos
```

Respuesta:

```json
{
  "id": "carga-...",
  "estado": "EN_PROCESO",
  "progreso": 0,
  "totales": { "leidos": 0, "procesados": 0, "rechazados": 0, "duplicados": 0 },
  "archivos": []
}
```

Consultar:

```bash
curl http://localhost:8080/api/cargas/{id}
curl "http://localhost:8080/api/cargas/{id}/rechazados?page=0&size=50"
```

## 11. Checklist para el dataset real

1. Confirmar que los encabezados estén entre los aceptados; si no, agregar el alias en `TransformationService`.
2. Verificar que el separador no esté dentro de campos sin comillas.
3. Revisar una muestra de filas rechazadas antes de una carga masiva.
4. Confirmar que `anio_ingreso` venga como año de cuatro dígitos.
5. Revisar que `aprobacion` use valores reconocidos.
6. Cargar primero un archivo pequeño y validar el reporte.
7. Cargar el resto y regenerar `resumen_carrera`.
8. Comparar conteos entre MongoDB y Neo4j.

Ver también [`../etl/validar_dataset.md`](../etl/validar_dataset.md).
