package com.db2.proyectofinaldb2.neo4j;

/**
 * Cypher statements used by the application. Keeping them in one place makes
 * them easy to document and defend during the project presentation.
 */
public final class CypherCatalog {

    private CypherCatalog() {
    }

    public static final String[] CONSTRAINTS = {
            "CREATE CONSTRAINT aspirante_correlativo IF NOT EXISTS FOR (a:Aspirante) REQUIRE a.correlativo IS UNIQUE",
            "CREATE CONSTRAINT carrera_nombre IF NOT EXISTS FOR (c:Carrera) REQUIRE c.nombre IS UNIQUE",
            "CREATE CONSTRAINT materia_nombre IF NOT EXISTS FOR (m:Materia) REQUIRE m.nombre IS UNIQUE",
            "CREATE CONSTRAINT departamento_nombre IF NOT EXISTS FOR (d:Departamento) REQUIRE d.nombre IS UNIQUE",
            "CREATE CONSTRAINT municipio_key IF NOT EXISTS FOR (m:Municipio) REQUIRE m.key IS UNIQUE",
            "CREATE CONSTRAINT institucion_key IF NOT EXISTS FOR (i:Institucion) REQUIRE i.key IS UNIQUE",
            "CREATE CONSTRAINT evaluacion_key IF NOT EXISTS FOR (e:Evaluacion) REQUIRE e.key IS UNIQUE"
    };

    public static final String[] INDEXES = {
            "CREATE INDEX evaluacion_aprobado IF NOT EXISTS FOR (e:Evaluacion) ON (e.aprobado)",
            "CREATE INDEX evaluacion_anio IF NOT EXISTS FOR (e:Evaluacion) ON (e.anio_ingreso)",
            "CREATE INDEX evaluacion_correlativo IF NOT EXISTS FOR (e:Evaluacion) ON (e.correlativo_aspirante)",
            "CREATE INDEX aspirante_sexo IF NOT EXISTS FOR (a:Aspirante) ON (a.sexo)"
    };

    /**
     * Loads one batch of already validated rows. MERGE is used everywhere so
     * re-uploading the same CSV does not create duplicated nodes.
     */
    public static final String LOAD_BATCH = """
            UNWIND $rows AS row
            MERGE (a:Aspirante {correlativo: row.correlativo})
              ON CREATE SET a.created_at = datetime()
            SET a.sexo = row.sexo, a.anio_nacimiento = row.anio_nacimiento
            MERGE (c:Carrera {nombre: row.carrera})
            MERGE (a)-[asp:ASPIRA_A {anio_ingreso: row.anio_ingreso}]->(c)
            MERGE (mat:Materia {nombre: row.materia})
            MERGE (e:Evaluacion {key: row.evalKey})
              ON CREATE SET e.created_at = datetime()
            SET e.correlativo_aspirante = row.correlativo,
                e.materia = row.materia,
                e.anio_ingreso = row.anio_ingreso,
                e.oportunidad = row.oportunidad,
                e.aprobacion = row.aprobacion,
                e.aprobado = row.aprobado,
                e.edad_ingreso = row.edad,
                e.fecha_asignacion = CASE WHEN row.fecha IS NOT NULL THEN date(row.fecha) ELSE null END
            MERGE (a)-[:REALIZO]->(e)
            MERGE (e)-[:DE_MATERIA]->(mat)
            MERGE (e)-[:PARA_CARRERA {anio_ingreso: row.anio_ingreso}]->(c)
            MERGE (d:Departamento {nombre: row.departamento})
            MERGE (mu:Municipio {key: row.municipioKey})
            SET mu.nombre = row.municipio, mu.departamento = row.departamento
            MERGE (i:Institucion {key: row.institucionKey})
            SET i.tipo = row.tipo,
                i.municipio = row.municipio,
                i.departamento = row.departamento,
                i.nombre = row.tipo + ' - ' + row.municipio + ' - ' + row.departamento
            MERGE (i)-[:UBICADA_EN]->(mu)
            MERGE (mu)-[:PERTENECE_A]->(d)
            MERGE (a)-[:PROCEDE_DE]->(i)
            """;

    public static final String G1_SUBGRAPH_BY_CAREER = """
            MATCH (a:Aspirante)-[:ASPIRA_A]->(c:Carrera)
            WHERE ($carrera IS NULL OR c.nombre = $carrera)
            OPTIONAL MATCH (a)-[:REALIZO]->(e:Evaluacion)-[:DE_MATERIA]->(m:Materia)
            RETURN a.correlativo AS aspirante, c.nombre AS carrera,
                   m.nombre AS materia, e.aprobacion AS resultado,
                   e.anio_ingreso AS anio, e.oportunidad AS oportunidad
            LIMIT $limite
            """;

    public static final String G2_CAREERS_BY_DEPARTMENT = """
            MATCH (a:Aspirante)-[:PROCEDE_DE]->(:Institucion)-[:UBICADA_EN]->(:Municipio)-[:PERTENECE_A]->(d:Departamento)
            WHERE ($depto IS NULL OR d.nombre = $depto)
            MATCH (a)-[:ASPIRA_A]->(c:Carrera)
            RETURN c.nombre AS carrera, count(DISTINCT a) AS aspirantes
            ORDER BY aspirantes DESC
            LIMIT $limite
            """;

    public static final String G3_APPROVED_BY_SUBJECT = """
            MATCH (e:Evaluacion {aprobado: true})-[:DE_MATERIA]->(m:Materia)
            WHERE ($anio IS NULL OR e.anio_ingreso = $anio)
            RETURN m.nombre AS materia, count(e) AS aprobados
            ORDER BY aprobados DESC
            LIMIT $limite
            """;

    public static final String G4_CO_ASPIRANTS = """
            MATCH (a1:Aspirante)-[:PROCEDE_DE]->(i:Institucion)<-[:PROCEDE_DE]-(a2:Aspirante)-[:ASPIRA_A]->(c:Carrera)
            WHERE a1.correlativo < a2.correlativo
              AND ($carrera IS NULL OR c.nombre = $carrera)
              AND ($tipo IS NULL OR i.tipo = $tipo)
            RETURN a1.correlativo AS aspirante1, a2.correlativo AS aspirante2,
                   i.nombre AS institucion, c.nombre AS carrera
            LIMIT $limite
            """;

    public static final String G5_SHARED_SUBJECTS = """
            MATCH (c1:Carrera)<-[:PARA_CARRERA]-(e1:Evaluacion)-[:DE_MATERIA]->(m:Materia)
                  <-[:DE_MATERIA]-(e2:Evaluacion)-[:PARA_CARRERA]->(c2:Carrera)
            WHERE c1.nombre < c2.nombre
            RETURN c1.nombre AS carrera1, c2.nombre AS carrera2,
                   collect(DISTINCT m.nombre)[..5] AS materias_compartidas,
                   count(DISTINCT m) AS total
            ORDER BY total DESC
            LIMIT $limite
            """;

    public static final String G6_FULL_PATH = """
            MATCH (d:Departamento)<-[:PERTENECE_A]-(m:Municipio)<-[:UBICADA_EN]-(i:Institucion)
                  <-[:PROCEDE_DE]-(a:Aspirante)-[:ASPIRA_A]->(c:Carrera)
            WHERE ($depto IS NULL OR d.nombre = $depto)
              AND ($carrera IS NULL OR c.nombre = $carrera)
            RETURN d.nombre AS departamento, m.nombre AS municipio, i.tipo AS tipo_institucion,
                   c.nombre AS carrera, count(DISTINCT a) AS aspirantes
            ORDER BY aspirantes DESC
            LIMIT $limite
            """;

    public static final String G7_SHORTEST_PATH = """
            MATCH path = shortestPath(
              (d:Departamento {nombre: $depto})-[*1..6]-(c:Carrera {nombre: $carrera})
            )
            RETURN [n IN nodes(path) | labels(n)[0] + ': ' + coalesce(n.nombre, n.correlativo)] AS ruta,
                   length(path) AS saltos
            """;

    /** Base query for the graph explorer. Optional filters are appended at runtime. */
    public static final String VISUALIZATION_BASE = """
            MATCH (a:Aspirante)-[:PROCEDE_DE]->(i:Institucion)-[:UBICADA_EN]->(mu:Municipio)-[:PERTENECE_A]->(d:Departamento)
            MATCH (a)-[:ASPIRA_A]->(c:Carrera)
            OPTIONAL MATCH (a)-[:REALIZO]->(e:Evaluacion)-[:DE_MATERIA]->(mat:Materia)
            WHERE (e)-[:PARA_CARRERA]->(c)
            WITH a, i, mu, d, c, e, mat
            WHERE 1 = 1
            """;

    public static final String NODE_DETAIL = """
            MATCH (n)
            WHERE elementId(n) = $id
            OPTIONAL MATCH (n)-[r]-(neighbor)
            RETURN n, collect(DISTINCT r) AS relaciones, collect(DISTINCT neighbor) AS vecinos
            """;
}
