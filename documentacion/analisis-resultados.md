# Análisis de resultados

## 1. Alcance y advertencia

El portal oficial de Datos Abiertos de la Facultad de Ingeniería (`datosabiertos.ingenieria.usac.edu.gt`) respondió **HTTP 500** durante el desarrollo, por lo que no fue posible descargar el dataset completo.

Los resultados de este documento provienen de:

- `etl/muestras/pruebas_especificas_muestra.csv`, una **muestra sintética de 29 filas**;
- la carga real en MongoDB y Neo4j;
- las 20 consultas y las 7 consultas Cypher implementadas.

**No son resultados oficiales ni representativos de la USAC.** Su función es:

1. validar el ETL, el modelo y las consultas;
2. comprobar que la interfaz presenta los datos correctamente;
3. dejar una plantilla de análisis que se actualizará cuando el dataset oficial esté disponible.

## 2. Resumen de la carga

| Métrica | Valor |
|---|---|
| Filas leídas | 29 |
| Filas válidas procesadas | 28 |
| Evaluaciones únicas insertadas | 27 |
| Duplicados omitidos | 1 |
| Rechazos | 1 |
| Aspirantes únicos | 12 |
| Carreras objetivo | 6 |
| Materias | 5 |
| Departamentos | 3 |
| Municipios | 4 |
| Carga en Neo4j | 62 nodos y 114 relaciones |

El rechazo correspondió a una fila con `materia` vacía. El duplicado era una fila idéntica a otra ya insertada; el índice único sobre `hash_fila` lo omitió sin detener la carga.

## 3. Composición de los aspirantes

### 3.1 Tipo de institución

| Tipo | Aspirantes | Porcentaje |
|---|---:|---:|
| Público | 8 | 66.7% |
| Privado | 4 | 33.3% |
| **Total** | **12** | **100%** |

La muestra está sesgada hacia instituciones públicas. Con el dataset real se espera una proporción más equilibrada y un volumen mucho mayor.

### 3.2 Sexo y tipo de institución

| Sexo | Tipo | Aspirantes |
|---|---|---:|
| M | Público | 5 |
| F | Público | 3 |
| F | Privado | 3 |
| M | Privado | 1 |

La combinación más frecuente es masculino de institución pública. Nuevamente, el tamaño de la muestra impide generalizar.

## 4. Rendimiento por materia

### 4.1 Aprobaciones y tasa

| Materia | Evaluaciones | Aprobadas | Tasa |
|---|---:|---:|---:|
| BIOLOGIA | 3 | 3 | 100.00% |
| LENGUAJE | 5 | 4 | 80.00% |
| MATEMATICA | 9 | 7 | 77.78% |
| FISICA | 6 | 4 | 66.67% |
| QUIMICA | 4 | 2 | 50.00% |

**Lectura:**

- Matemática es la materia con más evaluaciones y más aprobaciones absolutas (7), pero no la de mayor tasa.
- Química presenta la tasa más baja de la muestra (50%).
- Biología tiene 100%, pero solo 3 evaluaciones; un caso aislado puede mover mucho el porcentaje.
- El orden de materias por volumen (Matemática, Física, Lenguaje, Química, Biología) es consistente con un proceso de admisión que evalúa competencias básicas.

### 4.2 Intentos promedio

| Materia | Aspirantes | Intentos promedio |
|---|---:|---:|
| FISICA | 4 | 1.50 |
| LENGUAJE | 4 | 1.25 |
| MATEMATICA | 8 | 1.12 |
| QUIMICA | 4 | 1.00 |
| BIOLOGIA | 3 | 1.00 |

Física es la materia donde los aspirantes usan más oportunidades en promedio; Química y Biología se resuelven en el primer intento en esta muestra. La relación entre intentos y aprobación no es causal: un aspirante puede repetir para mejorar, no solo porque reprobó.

## 5. Carreras objetivo

### 5.1 Volumen

| Carrera | Evaluaciones | Aspirantes | Aprobadas | Tasa |
|---|---:|---:|---:|---:|
| INGENIERIA EN SISTEMAS | 8 | 3 | 6 | 75.00% |
| MEDICINA | 7 | 3 | 5 | 71.43% |
| ARQUITECTURA | 4 | 2 | 3 | 75.00% |
| INGENIERIA CIVIL | 4 | 2 | 4 | 100.00% |
| CIENCIAS JURIDICAS | 2 | 1 | 1 | 50.00% |
| DERECHO | 2 | 1 | 1 | 50.00% |

**Lectura:**

- Ingeniería en Sistemas y Medicina concentran la mayor cantidad de evaluaciones y aspirantes de la muestra.
- Ingeniería Civil tiene 100% de aprobación, pero solo 2 aspirantes.
- Derecho y Ciencias Jurídicas aparecen como carreras separadas; en el dataset real podría tratarse de la misma carrera con nombres distintos. Es un punto de calidad de datos a revisar.

### 5.2 Edad promedio por carrera

| Carrera | Edad promedio | Aspirantes |
|---|---:|---:|
| DERECHO | 19.00 | 1 |
| INGENIERIA CIVIL | 18.50 | 2 |
| MEDICINA | 18.33 | 3 |
| INGENIERIA EN SISTEMAS | 18.33 | 3 |
| ARQUITECTURA | 18.00 | 2 |
| CIENCIAS JURIDICAS | 18.00 | 1 |

Las diferencias son de un año o menos. La muestra no permite afirmar que exista una relación entre edad y elección de carrera.

## 6. Cobertura geográfica

### 6.1 Municipios con más aspirantes

| Municipio | Departamento | Aspirantes |
|---|---|---:|
| GUATEMALA | GUATEMALA | 6 |
| QUETZALTENANGO | QUETZALTENANGO | 3 |
| MIXCO | GUATEMALA | 2 |
| ESCUINTLA | ESCUINTLA | 1 |

### 6.2 Aprobados por municipio y carrera

Los municipios de Guatemala y Mixco concentran la mayor cantidad de aprobados. En la muestra:

- Guatemala → Arquitectura y Medicina: 2 aprobados cada una.
- Mixco → Ingeniería Civil: 2 aprobados.
- Quetzaltenango → Medicina, Ciencias Jurídicas e Ingeniería en Sistemas: 1 cada una.
- Escuintla → Ingeniería en Sistemas: 1.

**Lectura:** la capital y su área metropolitana dominan la muestra, pero el tamaño es demasiado pequeño para inferir patrones de acceso por territorio. Con el dataset completo, esta consulta será una de las más relevantes para detectar brechas regionales.

## 7. Evolución anual

| Año | Evaluaciones | Aprobadas | No aprobadas | Tasa |
|---:|---:|---:|---:|---:|
| 2016 | 2 | 1 | 1 | 50.00% |
| 2017 | 2 | 2 | 0 | 100.00% |
| 2018 | 3 | 2 | 1 | 66.67% |
| 2019 | 3 | 2 | 1 | 66.67% |
| 2020 | 4 | 3 | 1 | 75.00% |
| 2021 | 5 | 4 | 1 | 80.00% |
| 2022 | 4 | 3 | 1 | 75.00% |
| 2023 | 4 | 3 | 1 | 75.00% |

La tasa se mantiene entre 66% y 80% desde 2018, con un máximo de 80% en 2021. El 100% de 2017 corresponde a solo dos evaluaciones; no debe interpretarse como una tendencia. La serie sugiere estabilidad, pero **no hay base estadística para concluir una mejora o deterioro**.

## 8. Comparación público vs. privado

| Tipo | Evaluaciones | Aprobadas | Tasa |
|---|---:|---:|---:|
| Público | 19 | 15 | 78.95% |
| Privado | 8 | 5 | 62.50% |

En la muestra, la tasa de aprobación es mayor en instituciones públicas. Sin embargo:

- la diferencia absoluta es de 5 evaluaciones;
- el grupo privado tiene solo 8 evaluaciones;
- no se controla por materia, carrera, edad ni año.

Por lo tanto, **no se puede afirmar que el tipo de institución cause un mejor desempeño**. El dato es una señal para explorar con el dataset completo.

## 9. Edad y aprobación

| Edad | Evaluaciones | Aprobadas | Tasa |
|---:|---:|---:|---:|
| 18 | 19 | 13 | 68.42% |
| 19 | 8 | 7 | 87.50% |

Los aspirantes de 19 años aprueban a una tasa mayor en esta muestra, pero el grupo es pequeño y podría estar concentrado en carreras o materias específicas. Se recomienda repetir el análisis por rangos de edad y controlar por materia con el dataset real.

## 10. Resultados del grafo

La visualización cargó 62 nodos y 114 relaciones. Las consultas Cypher permitieron observar:

- **G2:** Medicina e Ingeniería en Sistemas son las carreras con más aspirantes por departamento en la muestra (3 cada una).
- **G4:** hay pares de aspirantes que comparten institución; por ejemplo, `A1B2C3D4` y `A7B8C9D0` comparten `PUBLICO - GUATEMALA - GUATEMALA`, y el segundo aspira a Medicina.
- **G5:** Arquitectura comparte Matemática y Física con Ingeniería en Sistemas e Ingeniería Civil; Ciencias Jurídicas comparte Matemática y Lenguaje con Ingeniería en Sistemas.
- **G7:** existe una ruta de 4 saltos entre el departamento de Guatemala y Medicina:

```text
Departamento: GUATEMALA
  → Municipio: GUATEMALA
  → Institucion: PUBLICO - GUATEMALA - GUATEMALA
  → Aspirante: A7B8C9D0
  → Carrera: MEDICINA
```

Estos resultados muestran el valor del grafo para responder preguntas de conexión que en MongoDB requerirían varios `$lookup`.

## 11. Calidad de datos

Observaciones de la muestra:

1. **Una fila rechazada** por materia vacía. El ETL la registró sin detener la carga.
2. **Un duplicado exacto** omitido por `hash_fila`.
3. **Posible duplicidad semántica** entre `DERECHO` y `CIENCIAS JURIDICAS`.
4. **Valores ausentes** en año de nacimiento, carrera, departamento, municipio o tipo; el ETL los normaliza a `null`, `NINGUNO` o `SIN REGISTRO`.
5. **Institución educativa sin nombre:** el dataset solo ofrece tipo y ubicación. El grafo usa una clave compuesta.
6. **Fechas:** la muestra usa fechas válidas; el ETL acepta varios formatos y deja `null` si no puede interpretarlas.

Con el dataset real se recomienda:

- revisar la distribución de `SIN REGISTRO`;
- normalizar nombres de carreras;
- verificar años fuera de rango;
- medir el porcentaje de rechazos antes de analizar;
- documentar la versión y el año de cada archivo.

## 12. Metodología

1. Descargar los CSV oficiales (pendiente por indisponibilidad del portal).
2. Cargarlos desde la interfaz o con `curl`.
3. Revisar el reporte de carga y los rechazos.
4. Regenerar `resumen_carrera`.
5. Ejecutar las 20 consultas y las 7 consultas Cypher.
6. Exportar las tablas necesarias.
7. Comparar los resultados con la muestra para detectar anomalías.
8. Redactar hallazgos separando **descripción** de **interpretación causal**.

## 13. Limitaciones

- **Muestra sintética de 29 filas:** no representa a la población real.
- **Portal no disponible:** no se pudo validar el esquema exacto de todos los archivos ni su volumen.
- **Sin ponderación:** los porcentajes son descriptivos; no hay intervalos de confianza ni pruebas de hipótesis.
- **Datos faltantes:** los valores `SIN REGISTRO` reducen la base de algunos análisis.
- **Institución aproximada:** no se dispone del nombre del establecimiento.
- **Carreras duplicadas:** posibles variantes de nombre que deben normalizarse.
- **Periodo:** la muestra cubre 2016–2023, pero con muy pocas observaciones por año.

## 14. Próximos pasos con el dataset oficial

1. Descargar todos los archivos disponibles.
2. Cargarlos y verificar los conteos.
3. Reemplazar las tablas de este documento con los resultados reales.
4. Analizar la evolución completa por año.
5. Comparar tasas por tipo de institución, materia, carrera y departamento.
6. Identificar brechas regionales y carreras de alta demanda.
7. Actualizar las conclusiones y las recomendaciones.
