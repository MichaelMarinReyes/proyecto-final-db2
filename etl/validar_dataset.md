# Validación del dataset oficial

## Estado

El 26 de octubre de 2026, el portal oficial seguía devolviendo HTTP 500:

<https://datosabiertos.ingenieria.usac.edu.gt/en/dataset/pruebas-especificas>

Mientras no se encuentre disponible, utilice `muestras/pruebas_especificas_muestra.csv` exclusivamente para pruebas. No sustituye los datos oficiales.

## Antes de cargar

1. Descargue los CSV oficiales y conserve los originales sin editar.
2. Registre URL, fecha de descarga, año y nombre de cada archivo.
3. Obtenga un hash para identificar la versión:

   ```bash
   sha256sum archivo.csv
   ```

4. Confirme que sean UTF-8. Si usan Latin-1, conviértalos a un archivo nuevo:

   ```bash
   iconv -f LATIN1 -t UTF-8 archivo.csv > archivo-utf8.csv
   ```

5. Revise los encabezados contra `documentacion/etl-reglas.md`. Los obligatorios son correlativo, materia, año de ingreso y aprobación. Cada fila también necesita una oportunidad válida.
6. Confirme que el separador sea coma, punto y coma o tabulador, y que los campos que contienen separadores estén entre comillas.
7. Revise el tamaño: máximo 100 MB por archivo y 500 MB por petición con la configuración predeterminada.

## Carga controlada

1. Inicie las bases, el backend y el frontend siguiendo `documentacion/manual-tecnico.md`.
2. Compruebe `GET /api/salud`.
3. Cargue primero un archivo pequeño desde **Carga de datos**.
4. Espere el estado final y conserve el reporte.
5. Revise rechazos por archivo, fila y causa. Un encabezado obligatorio faltante produce un error de archivo, no un rechazo por cada fila.
6. Corrija alias o formatos solo después de contrastarlos con el original; no descarte filas para mejorar artificialmente los porcentajes.

## Controles posteriores

En MongoDB:

```javascript
db.evaluaciones.countDocuments()
db.aspirantes.countDocuments()
db.evaluaciones.aggregate([
  { $group: { _id: "$anio_ingreso", evaluaciones: { $sum: 1 } } },
  { $sort: { _id: 1 } }
])
db.evaluaciones.aggregate([
  { $group: { _id: "$tipo_ie", evaluaciones: { $sum: 1 } } }
])
```

En Neo4j:

```cypher
MATCH (n) RETURN labels(n)[0] AS tipo, count(*) AS total;
MATCH (a:Aspirante) RETURN count(a) AS aspirantes;
MATCH (e:Evaluacion) RETURN count(e) AS evaluaciones;
```

Compare los aspirantes de ambos modelos. Las evaluaciones pueden diferir si el origen contiene versiones distintas de una misma clave `correlativo|materia|año|oportunidad`: MongoDB deduplica por hash de fila y Neo4j consolida esa clave con `MERGE`. Investigue diferencias antes de interpretar resultados.

## Idempotencia

Vuelva a cargar el mismo archivo sin modificarlo. Debe aumentar el contador de duplicados sin aumentar las evaluaciones únicas de MongoDB ni los nodos y relaciones de Neo4j. Los rechazos sí se registran nuevamente como parte del historial de la nueva carga.

## Actualizar entregables

- Regenerar `resumen_carrera` con `POST /api/consultas/mongo/6/resumen`.
- Ejecutar las 20 consultas MongoDB y G1–G7.
- Revisar valores `SIN REGISTRO`, años, edades y variantes de carreras.
- Actualizar `documentacion/analisis-resultados.md` y `documentacion/conclusiones.md`.
- Distinguir siempre resultados oficiales de resultados de muestra.
