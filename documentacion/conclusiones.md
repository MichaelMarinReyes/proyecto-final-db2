# Conclusiones

## 1. Sobre la solución

El proyecto demuestra que una arquitectura políglota puede resolver un mismo dominio desde dos perspectivas complementarias:

- **MongoDB** respondió con soltura a las preguntas de agregación: tasas, promedios, rankings, series anuales y resúmenes materializados.
- **Neo4j** simplificó las preguntas de relación: procedencia geográfica, aspirantes que comparten institución, materias compartidas entre carreras y caminos entre un departamento y una carrera.

La separación no fue accidental. Cada base se usó para el tipo de consulta que mejor expresa:

| Pregunta | Base más natural | Motivo |
|---|---|---|
| ¿Cuál es la tasa de aprobación por materia? | MongoDB | Agrupación y cálculo dentro de una colección. |
| ¿Qué carreras comparten materias? | Neo4j | Patrón de dos evaluaciones y una materia común. |
| ¿Cuál es la ruta de un departamento a una carrera? | Neo4j | `shortestPath` y recorridos de longitud variable. |
| ¿Cómo evolucionó la aprobación por año? | MongoDB | Serie temporal con `$group`. |
| ¿Qué aspirantes comparten institución? | Neo4j | Coincidencia de vecinos. |

## 2. Sobre MongoDB

### Aciertos

- El modelo plano `evaluaciones` fue la decisión correcta para el análisis: permitió escribir las 20 consultas sin `$lookup` ni esquemas intermedios.
- Los campos derivados (`aprobado`, `edad_ingreso`, `hash_fila`) simplificaron filtros, índices e idempotencia.
- `$out` mantiene `aspirantes` coherente con la fuente de verdad sin duplicar la lógica de transformación.
- `$merge` permitió materializar `resumen_carrera` y demostrar una técnica de agregación avanzada.
- `$setWindowFields` resolvió el top 3 por departamento sin traer todos los datos a Java.

### Lecciones

- Un índice único sobre un **hash de fila** es más robusto que un índice compuesto por muchos campos, especialmente cuando hay valores nulos.
- Es fácil confundir “aprobados” (evaluaciones) con “aspirantes aprobados” (personas). Las consultas 3, 5, 7, 8, 12, 16, 17 y 19 usan doble `$group` o `$addToSet` para contar personas; las consultas 2, 4, 13, 18 y 20 miden evaluaciones. Documentar esa diferencia es esencial.
- La agregación con `$out` es destructiva por diseño: si se elimina una evaluación, la reconstrucción la refleja; eso es deseable aquí, pero exige ejecutarla después de cada carga.

## 3. Sobre Neo4j

### Aciertos

- El modelo de nodos y relaciones representa el dominio sin tablas puente.
- Las restricciones de unicidad sobre claves de negocio permiten un ETL idempotente con `MERGE`.
- `shortestPath` resolvió en una consulta lo que en MongoDB requeriría varios `$lookup` y lógica de caminos.
- El explorador visual hizo tangible la estructura del dataset: los clústeres por institución y carrera se identifican a simple vista.

### Lecciones

- El nombre de la institución no estaba en el dataset; la clave `tipo|municipio|departamento` es una aproximación útil, pero no sustituye el nombre real.
- `MERGE` debe usarse con cuidado: si se mergea por una propiedad que puede cambiar, se crean nodos nuevos. Por eso las claves se calcularon de forma estable.
- La dirección de las relaciones importa para la legibilidad. `PROCEDE_DE`, `UBICADA_EN` y `PERTENECE_A` describen la jerarquía de forma natural.
- El límite de nodos es necesario para el navegador; sin él, un dataset grande puede congelar la visualización.

## 4. Sobre el ETL

- La detección automática del delimitador reduce la fricción con archivos reales; el contenido debe estar codificado en UTF-8.
- La normalización de encabezados con alias permite tolerar variantes entre años sin cambiar el código.
- Rechazar por fila y continuar es preferible a abortar todo el archivo: el reporte conserva la trazabilidad y el usuario puede corregir.
- La idempotencia se verificó reejecutando la muestra: 28 duplicados omitidos y conteos estables.
- La separación entre Mongo y Neo4j no es transaccional. La estrategia correcta es reintentar; el `MERGE` y el `hash_fila` evitan duplicar.

## 5. Sobre la interfaz

- El dashboard resume el estado en segundos y enlaza cada gráfica con la consulta que la alimenta.
- El explorador de grafos cumple el requisito de filtro y añade detalle de nodo, consultas Cypher y exportación.
- El buscador unifica criterios y ofrece ficha e historial sin obligar al usuario a conocer la estructura de las bases.
- Los mensajes de error en español y el botón **Reintentar** mejoran la experiencia cuando una base no responde.
- La interfaz responsive se verificó en 1440 px y 390 px, sin desbordamiento horizontal.

## 6. Sobre los resultados

La muestra sintética permitió validar el flujo completo, pero **no permite conclusiones sustantivas sobre la USAC**:

- 12 aspirantes y 27 evaluaciones son demasiado pocos para inferir tendencias;
- la composición por tipo de institución está sesgada;
- los porcentajes por materia y carrera tienen denominadores pequeños;
- no se controló por variables confusoras.

Los hallazgos de la muestra deben leerse como **prueba de que las consultas funcionan**, no como diagnóstico institucional. La conclusión metodológica es que el sistema está listo para recibir el dataset real y que el análisis debe actualizarse cuando el portal esté disponible.

## 7. Limitaciones del proyecto

1. **Dataset oficial no disponible:** el portal respondió HTTP 500 durante el desarrollo.
2. **Sin autenticación:** adecuado para un entorno local, insuficiente para producción.
3. **Sin transacción distribuida:** la consistencia entre MongoDB y Neo4j es eventual y depende de reintentos.
4. **Progreso en memoria:** un reinicio del backend durante una carga deja el job interrumpido.
5. **Institución aproximada:** no se modela el nombre real del establecimiento.
6. **Carreras sin normalizar:** pueden existir variantes semánticas como `DERECHO` y `CIENCIAS JURIDICAS`.
7. **Bundle grande del frontend:** el grafo y las gráficas elevan el tamaño; es aceptable para el alcance, pero se puede optimizar con carga diferida.

## 8. Trabajo futuro

- Descargar y analizar el dataset oficial completo.
- Agregar autenticación y roles (consulta, carga, administración).
- Implementar reintentos automáticos y persistencia del progreso del ETL.
- Normalizar carreras y materias con catálogos maestros.
- Incorporar el nombre real de la institución si se consigue.
- Añadir pruebas de integración con Testcontainers.
- Optimizar el frontend con `React.lazy`, división de bundles y caché de catálogos.
- Añadir paginación del lado del servidor para las consultas con muchas filas.
- Exportar reportes en PDF/Excel.
- Programar respaldos y monitoreo de MongoDB y Neo4j.

## 9. Conclusión final

El proyecto cumple los objetivos técnicos: ETL robusto, 20 consultas MongoDB, 7 consultas Cypher, dashboard, explorador de grafos con filtros, buscador, manejo de errores, pruebas y documentación.

La principal deuda no es de implementación sino de **datos**: el portal oficial no estuvo accesible. La aplicación quedó preparada para cargar el dataset real sin cambios de código, y el análisis incluye una ruta clara para reemplazar la muestra por resultados oficiales. Esa es, a la vez, la mayor limitación y el siguiente paso más importante.
