# Manual de usuario

## 1. Introducción

**Pruebas Específicas USAC** es una aplicación web para explorar las evaluaciones de admisión de la Facultad de Ingeniería. Permite:

- cargar archivos CSV;
- revisar el resultado del ETL y los registros rechazados;
- consultar 20 análisis en MongoDB;
- explorar las relaciones entre aspirantes, carreras, materias, instituciones y departamentos en Neo4j;
- buscar aspirantes y consultar su ficha e historial.

La barra lateral muestra el estado de MongoDB y Neo4j. Si alguno está desconectado, aparece “sin conexión” y el sistema se marca como degradado.

## 2. Requisitos

- Navegador actualizado: Chrome, Firefox o Edge.
- Acceso a la URL del frontend (por defecto <http://localhost:5173>).
- Backend en ejecución (por defecto `http://localhost:8080`).
- Archivos CSV del conjunto de pruebas específicas.

## 3. Acceso y navegación

Al abrir la aplicación se muestra el **Dashboard**. El menú lateral contiene:

| Opción | Qué hace |
|---|---|
| **Dashboard** | Indicadores y seis gráficas de resumen. |
| **Carga de datos** | Sube archivos, muestra progreso, reporte, rechazos e historial. |
| **Consultas MongoDB** | Selector de las 20 consultas, filtros, gráfica/tabla y exportación CSV. |
| **Explorador de grafos** | Visualización interactiva de Neo4j, filtros y consultas Cypher G1–G7. |
| **Buscador** | Búsqueda por aspirante, carrera, materia, institución, municipio o departamento. |

En pantallas pequeñas, el menú se abre con el botón de tres líneas de la barra superior.

### Estados de servicio

- **Sistema operativo:** ambas bases responden.
- **Sistema degradado:** al menos una base no responde.
- En la parte inferior de la barra lateral se muestra el número de evaluaciones cargadas.

## 4. Carga de archivos CSV

1. Ir a **Carga de datos**.
2. Arrastrar uno o varios archivos `.csv` al recuadro, o hacer clic para seleccionarlos.
3. Revisar la lista y quitar los archivos que no correspondan.
4. Presionar **Subir y procesar**.
5. La carga se ejecuta en segundo plano. La barra de progreso se actualiza automáticamente.
6. Al finalizar, revisar el **Reporte de carga**:
   - estado;
   - leídos, procesados, rechazados y duplicados omitidos;
   - duración;
   - detalle por archivo.
7. Si hay rechazos, presionar **Ver registros rechazados**.
8. Para consultar una carga anterior, usar **Cargas realizadas** y **Abrir**.

### Lectura del reporte

| Campo | Significado |
|---|---|
| **Leídos** | Filas de datos leídas del CSV; excluye encabezados y filas vacías. |
| **Procesados** | Filas válidas que intentaron insertarse; incluye duplicados detectados. |
| **Rechazados** | Filas que no cumplieron las validaciones. |
| **Duplicados omitidos** | Filas válidas que ya existían (mismo `hash_fila`) y no se insertaron de nuevo. |
| **Duración** | Tiempo del procesamiento. |

Si se vuelve a subir el mismo archivo, los registros no se duplican: aumentan los duplicados omitidos y los conteos de MongoDB/Neo4j permanecen estables.

### Errores por fila

El reporte muestra hasta 100 errores por archivo con:

- número de fila;
- causa (`materia vacía`, `anio_de_ingreso inválido`, `aprobacion no reconocida`, etc.);
- una muestra truncada del contenido.

## 5. Dashboard

El dashboard responde a la pregunta “¿qué está pasando con las pruebas?”. Incluye:

| Indicador | Descripción |
|---|---|
| **Aspirantes únicos** | Documentos en la colección `aspirantes`. |
| **Evaluaciones** | Registros válidos en `evaluaciones`. |
| **Tasa de aprobación global** | Evaluaciones aprobadas / evaluaciones totales. |
| **Carreras objetivo** | Carreras distintas registradas. |

Gráficas:

1. Aspirantes por tipo de institución (consulta 1).
2. Aprobados por materia (consulta 2).
3. Porcentaje de aprobación por materia (consulta 4).
4. Top municipios por aspirantes (consulta 17).
5. Aprobación por tipo de institución (consulta 18).
6. Evolución de la aprobación por año (consulta 20).

Cada tarjeta indica la consulta que la alimenta. El botón **Actualizar datos** vuelve a calcular todo.

Si no hay datos, aparece un aviso con un botón directo a **Cargar datos**.

## 6. Consultas MongoDB

1. Ir a **Consultas MongoDB**.
2. Elegir una consulta en la lista (1–20).
3. Revisar la descripción y la técnica usada.
4. Aplicar filtros opcionales:
   - Año;
   - Materia;
   - Carrera;
   - Departamento;
   - Tipo de institución;
   - Límite de filas.
5. Presionar **Generar**.
6. Cambiar entre **Gráfica** y **Tabla** cuando la consulta tenga gráfica.
7. Exportar la tabla a CSV con el botón correspondiente.

### Consultas que requieren correlativo

- **Consulta 11:** historial de desempeño.
- **Consulta 15:** ficha completa del aspirante.

En esos casos aparece un campo adicional. Ejemplo de correlativo de la muestra: `A1B2C3D4`.

### Consulta 6

La consulta 6 tiene un botón **Regenerar resumen**. Ejecuta un `$merge` sobre `resumen_carrera` y muestra el resumen actualizado. Es útil después de una carga.

## 7. Explorador de grafos

1. Ir a **Explorador de grafos**.
2. Elegir filtros:
   - Carrera;
   - Departamento;
   - Materia;
   - Tipo de institución;
   - Año;
   - Límite de registros (1–500; cada registro puede aportar varios nodos).
3. Presionar **Explorar grafo**.
4. Interactuar con el lienzo:
   - arrastrar nodos;
   - hacer zoom con la rueda;
   - hacer clic en un nodo para abrir su detalle.
5. Revisar el panel **Nodo seleccionado**: tipo, propiedades y vecinos.
6. Usar la pestaña **Consultas Cypher (G1–G7)** para ejecutar las consultas tabulares.

### Colores de los nodos

La leyenda del grafo distingue aspirantes, carreras, materias, evaluaciones, instituciones, municipios y departamentos. Los colores ayudan a identificar visualmente los clústeres.

### Consultas Cypher

| Consulta | Para qué sirve |
|---|---|
| G1 | Evaluaciones de aspirantes por carrera. |
| G2 | Carreras más demandadas por departamento. |
| G3 | Aprobados por materia y año. |
| G4 | Aspirantes que comparten institución. |
| G5 | Materias compartidas entre carreras. |
| G6 | Ruta geográfica completa. |
| G7 | Ruta más corta entre un departamento y una carrera. |

G7 exige seleccionar **departamento** y **carrera**. Si falta uno, la aplicación muestra un mensaje de validación.

La tabla de resultados permite ordenar y exportar. En G5, `materias_compartidas` se muestra como una lista.

### Consejos

- Si el grafo está vacío, quitar filtros y volver a explorar.
- El límite protege el navegador: con datasets grandes, empezar con 100–150 nodos.
- Si se filtra por materia o año, solo aparecen evaluaciones que cumplen ese criterio.
- El detalle del nodo muestra las propiedades y relaciones; el `id` es interno de Neo4j.

## 8. Buscador

1. Ir a **Buscador**.
2. Elegir el criterio:
   - Aspirante (correlativo);
   - Carrera objetivo;
   - Materia;
   - Institución educativa;
   - Municipio;
   - Departamento.
3. Escribir el texto. En aspirantes se acepta el correlativo completo o un prefijo.
4. Presionar **Buscar**.
5. En resultados de aspirantes, hacer clic en **Ver ficha**.
6. La ficha muestra:
   - datos del aspirante;
   - total de evaluaciones;
   - materias y carreras;
   - historial detallado.

La búsqueda no distingue mayúsculas de minúsculas y usa coincidencias parciales.

## 9. Historial de cargas y errores

- **Historial:** sección inferior de **Carga de datos**. Muestra las últimas 50 cargas.
- **Abrir:** carga el reporte de una carga anterior.
- **Ver rechazados:** lista los registros de esa carga con archivo, fila, causa y valor.
- **Errores de red:** si el backend no responde, aparece un panel de error con botón **Reintentar**.
- **Rutas inexistentes:** la aplicación muestra una página 404 con enlace al dashboard.

## 10. Preguntas frecuentes

### ¿Por qué no veo datos?

Porque todavía no se ha cargado ningún CSV o la carga falló. Ir a **Carga de datos** y revisar el reporte.

### ¿Puedo subir varios archivos?

Sí. Se procesan en el mismo job y el reporte muestra el detalle por archivo.

### ¿Qué pasa si subo el mismo archivo dos veces?

No se duplican evaluaciones ni nodos. Los registros se cuentan como **duplicados omitidos**. Los rechazos se vuelven a registrar porque son parte del historial de esa carga.

### ¿Por qué un año aparece como “2018” y no “2,018”?

La interfaz detecta columnas de año y las muestra sin separador de miles. Los conteos sí usan separador.

### ¿Por qué una evaluación no aparece en el grafo?

Puede estar fuera del límite de nodos, no cumplir los filtros o no tener relaciones cargadas. Probar sin filtros y con un límite mayor.

### ¿Por qué G7 no devuelve nada?

No existe un camino entre el departamento y la carrera elegidos, o falta uno de los dos filtros. Probar con `GUATEMALA` y `MEDICINA` en la muestra.

### ¿Cómo interpreto “procesados” y “duplicados”?

“Procesados” son filas válidas que se intentaron insertar. “Duplicados” son las que ya existían. La suma de insertados y duplicados es el total de filas válidas.

### ¿La aplicación guarda mis archivos?

No de forma permanente. Se copian a un directorio temporal y se eliminan al terminar la carga. Los datos transformados quedan en MongoDB y Neo4j.

### ¿Puedo usar la aplicación desde otro equipo?

Sí, si el backend y el frontend son accesibles en la red y el origen del navegador está incluido en `CORS_ORIGINS`. Para un despliegue real se recomienda HTTPS y autenticación.

## 11. Buenas prácticas

- Cargar primero un archivo pequeño y revisar los rechazos.
- No modificar los encabezados del CSV original sin motivo.
- Guardar una copia del dataset oficial.
- Revisar el estado de MongoDB y Neo4j antes de una demostración.
- Regenerar `resumen_carrera` después de una carga importante.
- Usar límites moderados en el explorador de grafos.
