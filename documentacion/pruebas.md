# Pruebas y validación

Fecha de verificación: **26 de octubre de 2026**.

## Alcance

Las pruebas se realizaron localmente con MongoDB 7, Neo4j 5, el backend Spring Boot y el frontend React. Los datos son la muestra sintética `etl/muestras/pruebas_especificas_muestra.csv`; no constituyen evidencia sobre el dataset oficial.

## Pruebas unitarias y empaquetado

Desde `proyecto-final-db2/`:

```bash
./mvnw test package
```

| Clase | Pruebas | Fallas | Errores |
|---|---:|---:|---:|
| `TransformationServiceTest` | 5 | 0 | 0 |
| `CsvSupportTest` | 4 | 0 | 0 |
| `GraphServiceTest` | 1 | 0 | 0 |
| **Total** | **10** | **0** | **0** |

La ejecución final de `./mvnw -q test package` terminó con código 0. El JAR ejecutable se genera en `proyecto-final-db2/target/`. Los reportes completos están en `target/surefire-reports/` y no se versionan.

Las pruebas unitarias no requieren bases de datos. Cubren transformación, alias, normalización, validaciones, detección de delimitadores y construcción del Cypher del explorador; no sustituyen las pruebas de integración. Mockito emite una advertencia de carga dinámica del agente en JDK 22; la ejecución final terminó sin fallas.

## Compilación del frontend

Desde `frontend-proyecto-final-db2/`:

```bash
npm run build
```

Resultado: correcto, 1775 módulos transformados. Bundle principal: aproximadamente 854.48 kB (254.46 kB comprimido). Vite advierte que supera 500 kB; no impide compilar, pero queda como mejora pendiente dividir el código por rutas.

## API REST

Verificación final contra `http://localhost:8080/api`:

| Caso | Resultado |
|---|---|
| `GET /salud` | 200 |
| Consultas MongoDB 1–20 | Todas 200 |
| C11 y C15 con `correlativo=A1B2C3D4` | 200 |
| Consultas Cypher G1–G7 | Todas 200 |
| G7 con `depto=GUATEMALA&carrera=MEDICINA` | Ruta de 4 saltos |
| `GET /nope` | 404, mensaje en español |
| `GET /consultas/mongo/abc` | 400, parámetro inválido |

También se verificaron durante el desarrollo los catálogos, la búsqueda, la visualización, el detalle del nodo y `POST /consultas/mongo/6/resumen`.

## ETL e idempotencia

Primera carga de la muestra:

| Métrica | Resultado |
|---|---:|
| Filas de datos leídas | 29 |
| Procesadas | 28 |
| Rechazadas | 1 |
| Evaluaciones únicas almacenadas | 27 |
| Aspirantes únicos | 12 |

La fila 30 del archivo fue rechazada por `materia vacía`. Una fila válida repetida se omitió por el índice único de `hash_fila`.

En una segunda carga: 28 duplicados omitidos y conteos estables (27 evaluaciones y 12 aspirantes). Los rechazos acumulados pasan a 2 porque cada carga conserva su propia trazabilidad. El grafo se mantuvo en 62 nodos y 114 relaciones.

## Navegador

Se utilizó Google Chrome sin interfaz gráfica mediante Puppeteer. Los scripts auxiliares de esta sesión están en `/tmp/opencode/browser-test/`; no forman parte del repositorio ni son una suite permanente.

Las **18 comprobaciones de interacción** finales pasaron:

- Consulta 4: gráfica y cambio a tabla.
- Consulta 11: historial del aspirante de muestra.
- Grafo: renderizado, consulta G1 y filtro por departamento.
- Buscador: ficha e historial del aspirante.
- Cargas: historial, apertura de reporte y registros rechazados.
- Cinco páginas sin desbordamiento horizontal en escritorio (1440 px).
- Botón de menú móvil visible, apertura del menú y ausencia de desbordamiento a 390 px.

No se registraron errores de consola ni excepciones de página. Las tres fallas iniciales del script fueron comparaciones sensibles a mayúsculas de encabezados transformados por CSS; se corrigieron las aserciones y se repitió la prueba.

## Fechas y presentación

- Los años se presentan sin separador de miles.
- Los encabezados camelCase se separan en palabras.
- Las fechas de asignación se formatean en UTC porque el ETL guarda días calendario a medianoche UTC. Esto evita mostrarlas como el día anterior en Guatemala.
- Las fechas y horas de cargas siguen usando la zona local del navegador.

## Límites de la evidencia

- No se hicieron pruebas de carga masiva ni de concurrencia.
- No se verificó recuperación automática después de reiniciar un ETL activo.
- No existe todavía una suite de integración automatizada con Testcontainers.
- El dataset real no se pudo descargar: el último intento al portal oficial devolvió HTTP 500.
- Falta repetir ETL, conteos y análisis con los archivos oficiales cuando estén disponibles.

## Revisión ampliada de integración

Después de consolidar el frontend en `frontend-proyecto-final-db2/`, se verificó que `frontend/` solo contenía caché residual de Vite y se eliminó. La aplicación y el servidor de desarrollo usan exclusivamente la carpeta correcta.

### Problemas encontrados y corregidos

1. **Parámetro de límite:** el explorador enviaba `limite`, pero el controlador REST recibe `limit`. Se corrigió tanto para la visualización como para las consultas G1–G7 y se comprobó la petición real del navegador con `limit=1`.
2. **Filtros del grafo:** el `WHERE` asociado a `OPTIONAL MATCH` permitía conservar aspirantes que no cumplían el filtro. Se añadió una etapa `WITH` antes de los filtros globales. Los filtros por departamento, carrera, materia, tipo y año sin coincidencias ahora devuelven un grafo vacío.
3. **Coherencia de relaciones visualizadas:** las evaluaciones se restringen a las que tienen una relación real `PARA_CARRERA` con la carrera mostrada; no se fabrican conexiones entre evaluaciones de una carrera y otra carrera del mismo aspirante.

`GraphServiceTest` deja cubierta la construcción de esta consulta. La integración real confirmó que combinar Guatemala, Medicina, Biología y 2021 devuelve 7 nodos y 7 relaciones coherentes con los filtros.

### Resultados adicionales

| Prueba | Resultado |
|---|---|
| Las 20 consultas desde la interfaz, con sus tablas | Correcto |
| Las 7 consultas Cypher desde la interfaz | Correcto |
| Los 6 criterios del buscador desde la interfaz | Correcto |
| C2, C4 y C10 contrastadas con el contenido del CSV | Conteos y porcentajes correctos |
| Subida real desde la interfaz, respuesta 202 y polling | Correcto |
| Recarga de muestra desde la interfaz | 29 leídos, 28 procesados y duplicados, 1 rechazo |
| Petición con dos copias de la muestra y un archivo mal estructurado | 58 leídos, 56 procesados y duplicados, 2 rechazos; error identificado en el tercer archivo |
| Archivo sin columnas obligatorias | Job en ERROR con causa clara |
| Extensión `.txt` o archivo vacío | 400 controlado |
| Trece casos de parámetros o recursos inválidos | 400/404 con mensaje |
| Catálogos y detalle de nodo por identificador codificado | Correcto |
| Build de producción servido con `vite preview` en las seis rutas | Sin errores de consola |
| Pruebas responsive y de interacción anteriores | 18/18 correctas |

La revisión ampliada del navegador (`/tmp/opencode/browser-test/full-review.js`) terminó con **36 comprobaciones aprobadas**, además de las 18 comprobaciones anteriores. Los scripts auxiliares siguen fuera del repositorio.

### Caída y recuperación real de las bases

Se detuvo cada contenedor por separado y se volvió a iniciar mediante un bloque de limpieza garantizada:

| Base detenida | Petición dependiente | Petición a la otra base | Recuperación |
|---|---|---|---|
| Neo4j | G3 → 503 con mensaje en español | C2 → 200 | G3 volvió a responder 200 sin reiniciar el backend |
| MongoDB | C2 → 503 con mensaje en español | G3 → 200 | C2 volvió a responder 200 sin reiniciar el backend |

El fallo se informa después de aproximadamente **30 segundos**, debido al tiempo de espera de conexión de los drivers. No se trata de una respuesta inmediata. Ambas bases quedaron iniciadas y el endpoint de salud volvió a indicar `OK`.

### Efecto sobre los datos

Las cargas de prueba no añadieron evaluaciones ni aspirantes: se conservaron **27 evaluaciones y 12 aspirantes**. Sí aumentaron el historial de cargas y los rechazos acumulados, como corresponde al diseño de auditoría. No se borraron datos de MongoDB ni Neo4j.

### Dictamen y limitaciones vigentes

El flujo local frontend → API → MongoDB/Neo4j funciona con la muestra disponible, tanto en desarrollo como en el build del frontend. Continúan pendientes la validación con el dataset oficial, las pruebas de volumen y la recuperación de jobs interrumpidos por reinicio del backend. El bundle de aproximadamente 854 kB sigue generando una advertencia de tamaño, sin impedir su ejecución.
