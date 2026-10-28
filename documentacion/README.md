# Documentación del proyecto

Esta carpeta contiene los **entregables que se versionan en Git**. Las notas de desarrollo, el enunciado y los registros de conversación viven en `docs/`, que está ignorado por Git.

## Índice

| Documento | Estado | Contenido |
|---|---|---|
| [manual-tecnico.md](manual-tecnico.md) | Completo | Instalación, configuración, arquitectura de ejecución, API, pruebas y solución de problemas |
| [manual-usuario.md](manual-usuario.md) | Completo | Guía paso a paso de la interfaz |
| [arquitectura.md](arquitectura.md) | Completo | Capas, flujo de una petición, decisiones y despliegue |
| [modelamiento-mongodb.md](modelamiento-mongodb.md) | Completo | Colecciones, esquemas, índices y justificación del modelo |
| [modelamiento-neo4j.md](modelamiento-neo4j.md) | Completo | Nodos, relaciones, restricciones y recorridos |
| [etl-reglas.md](etl-reglas.md) | Completo | Reglas de lectura, validación, transformación, idempotencia y rechazo |
| [consultas-mongodb.md](consultas-mongodb.md) | Completo | Las 20 consultas: propósito, pipeline y lectura de resultados |
| [consultas-neo4j.md](consultas-neo4j.md) | Completo | Las 7 consultas Cypher y sus parámetros |
| [analisis-resultados.md](analisis-resultados.md) | Completo | Metodología, hallazgos de la muestra y limitaciones |
| [conclusiones.md](conclusiones.md) | Completo | Comparación MongoDB/Neo4j, aprendizajes y trabajo futuro |
| [pruebas.md](pruebas.md) | Completo | Evidencia de pruebas unitarias, API, ETL y navegador |
| [auditoria-arquitectura.md](auditoria-arquitectura.md) | Completo | Auditoría estática de capas, comunicaciones, responsabilidades y refactorizaciones recomendadas |
| [configuracion-local.md](configuracion-local.md) | Completo | Configuración privada con archivos `.env` no versionados |
| [consultas-personalizadas.md](consultas-personalizadas.md) | Completo | Biblioteca persistente MongoDB/Cypher, permisos, escrituras y ejemplos |

## Requisitos cubiertos

- [x] ETL desde CSV con validación, transformación, rechazos e idempotencia.
- [x] MongoDB con modelo documental plano, embebido y colección auxiliar.
- [x] Neo4j con nodos, relaciones, restricciones e índices.
- [x] 20 consultas MongoDB implementadas y visualizables.
- [x] 7 consultas Cypher (mínimo 5).
- [x] Dashboard, explorador de grafos con filtros, buscador y carga.
- [x] Manejo de errores en backend y frontend.
- [x] Pruebas unitarias y verificación de extremo a extremo.
- [x] Documentación técnica, manuales, análisis y conclusiones.
- [ ] Historial de cambios en Git con al menos cinco commits significativos (pendiente).

## Nota sobre el dataset

El enlace oficial de Datos Abiertos de la Facultad de Ingeniería respondió HTTP 500 durante la ventana de desarrollo. La aplicación se validó con `etl/muestras/pruebas_especificas_muestra.csv`, una muestra sintética de 29 filas. Los documentos distinguen explícitamente los resultados de la muestra de los resultados que se obtendrán con el dataset oficial.

Cuando el portal esté disponible:

1. Descargue los CSV oficiales.
2. Revise [`../etl/validar_dataset.md`](../etl/validar_dataset.md).
3. Súbalos desde **Carga de datos**.
4. Regeneren `resumen_carrera` y actualicen el análisis con los resultados reales.

## Convenciones

- Código e identificadores Java en inglés.
- Mensajes de interfaz y API en español.
- Fechas en formato ISO en la API; la interfaz las presenta en español.
- Los documentos no incluyen credenciales reales: las contraseñas de Docker son locales y deben cambiarse en un despliegue público.
