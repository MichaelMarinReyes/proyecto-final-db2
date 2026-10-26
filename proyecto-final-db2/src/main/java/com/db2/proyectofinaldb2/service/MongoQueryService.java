package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.exception.ApiException;
import com.db2.proyectofinaldb2.repository.EvaluationRepository;
import com.db2.proyectofinaldb2.util.MongoJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MongoQueryService {

    private final EvaluationRepository repository;

    public MongoQueryService(EvaluationRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> execute(int number, QueryFilters filters) {
        return switch (number) {
            case 1 -> aspirantsByInstitutionType(filters);
            case 2 -> approvedBySubject(filters);
            case 3 -> approvedByCareerAndYear(filters);
            case 4 -> approvalRateBySubject(filters);
            case 5 -> averageAgeByCareer(filters);
            case 6 -> careerSummary();
            case 7 -> averageAgeApprovedByCareerAndType(filters);
            case 8 -> approvedByMunicipalityAndCareer(filters);
            case 9 -> evaluationsByMonthAndSubject(filters);
            case 10 -> topCareersByAge(filters);
            case 11 -> performanceHistory(filters);
            case 12 -> distributionBySexAndType(filters);
            case 13 -> approvalRateByAge(filters);
            case 14 -> averageAttemptsBySubject(filters);
            case 15 -> fullAspirantHistory(filters);
            case 16 -> careersWithMostFirstAttemptFailures(filters);
            case 17 -> topMunicipalities(filters);
            case 18 -> approvalRateByInstitutionType(filters);
            case 19 -> topCareersByDepartment(filters);
            case 20 -> approvalEvolutionByYear(filters);
            default -> throw new ApiException(HttpStatus.NOT_FOUND,
                    "La consulta " + number + " no existe. Las consultas válidas van de 1 a 20");
        };
    }

    // 1. Aspirantes por tipo de institución educativa
    private List<Map<String, Object>> aspirantsByInstitutionType(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        addMatch(pipeline, filters);
        pipeline.add(parse("""
                { $group: { _id: { tipo: "$tipo_ie", corr: "$correlativo_aspirante" } } }
                """));
        pipeline.add(parse("""
                { $group: { _id: "$_id.tipo", aspirantes: { $sum: 1 } } }
                """));
        pipeline.add(parse("""
                { $project: { _id: 0, tipo: "$_id", aspirantes: 1 } }
                """));
        pipeline.add(sort("aspirantes", -1));
        return run(pipeline);
    }

    // 2. Cantidad de aprobados por materia
    private List<Map<String, Object>> approvedBySubject(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("aprobado", true);
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: { _id: "$materia", aprobados: { $sum: 1 } } }
                """));
        pipeline.add(parse("""
                { $project: { _id: 0, materia: "$_id", aprobados: 1 } }
                """));
        pipeline.add(sort("aprobados", -1));
        return run(pipeline);
    }

    // 3. Aprobados por carrera y año de ingreso
    private List<Map<String, Object>> approvedByCareerAndYear(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("aprobado", true);
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: {
                    _id: { carrera: "$carrera_objetivo", anio: "$anio_ingreso", corr: "$correlativo_aspirante" }
                } }
                """));
        pipeline.add(parse("""
                { $group: {
                    _id: { carrera: "$_id.carrera", anio: "$_id.anio" },
                    aprobados: { $sum: 1 }
                } }
                """));
        pipeline.add(parse("""
                { $project: { _id: 0, carrera: "$_id.carrera", anio: "$_id.anio", aprobados: 1 } }
                """));
        pipeline.add(sort("anio", 1));
        return run(pipeline);
    }

    // 4. Porcentaje de aprobación por materia
    private List<Map<String, Object>> approvalRateBySubject(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        addMatch(pipeline, filters);
        pipeline.add(parse("""
                { $group: {
                    _id: "$materia",
                    total: { $sum: 1 },
                    aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    materia: "$_id",
                    total: 1,
                    aprobados: 1,
                    porcentaje: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total"] }, 100] }, 2] }
                } }
                """));
        pipeline.add(sort("porcentaje", -1));
        return run(pipeline);
    }

    // 5. Promedio de edad por carrera
    private List<Map<String, Object>> averageAgeByCareer(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("edad_ingreso", new Document("$ne", null));
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: {
                    _id: { carrera: "$carrera_objetivo", corr: "$correlativo_aspirante" },
                    edad: { $first: "$edad_ingreso" }
                } }
                """));
        pipeline.add(parse("""
                { $group: {
                    _id: "$_id.carrera",
                    edad_promedio: { $avg: "$edad" },
                    muestras: { $sum: 1 }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    carrera: "$_id",
                    edad_promedio: { $round: ["$edad_promedio", 2] },
                    muestras: 1
                } }
                """));
        pipeline.add(sort("edad_promedio", -1));
        return run(pipeline);
    }

    // 6. Colección auxiliar resumen_carrera
    private List<Map<String, Object>> careerSummary() {
        List<Document> pipeline = List.of(
                parse("""
                        { $group: {
                            _id: "$carrera_objetivo",
                            total_evaluaciones: { $sum: 1 },
                            aspirantes: { $addToSet: "$correlativo_aspirante" },
                            aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } },
                            edades: { $push: "$edad_ingreso" }
                        } }
                        """),
                parse("""
                        { $project: {
                            total_evaluaciones: 1,
                            total_aspirantes: { $size: "$aspirantes" },
                            aprobados: 1,
                            reprobados: { $subtract: ["$total_evaluaciones", "$aprobados"] },
                            tasa_aprobacion: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total_evaluaciones"] }, 100] }, 2] },
                            edades: { $filter: { input: "$edades", as: "e", cond: { $ne: ["$$e", null] } } }
                        } }
                        """),
                parse("""
                        { $addFields: {
                            promedio_edad: { $cond: [
                                { $eq: [{ $size: "$edades" }, 0] },
                                null,
                                { $round: [{ $avg: "$edades" }, 2] }
                            ] }
                        } }
                        """),
                parse("{ $unset: \"edades\" }"),
                parse("{ $set: { generado_en: \"$$NOW\" } }"),
                parse("""
                        { $merge: {
                            into: "resumen_carrera",
                            on: "_id",
                            whenMatched: "replace",
                            whenNotMatched: "insert"
                        } }
                        """)
        );
        repository.aggregate(EvaluationRepository.EVALUATIONS, pipeline);

        List<Document> read = List.of(
                sort("tasa_aprobacion", -1),
                parse("""
                        { $project: {
                            _id: 0,
                            carrera: "$_id",
                            total_evaluaciones: 1,
                            total_aspirantes: 1,
                            aprobados: 1,
                            reprobados: 1,
                            tasa_aprobacion: 1,
                            promedio_edad: 1,
                            generado_en: 1
                        } }
                        """)
        );
        return MongoJson.toList(repository.aggregate(EvaluationRepository.CAREER_SUMMARY, read));
    }

    // 7. Promedio de edad de aprobados por carrera y tipo de institución
    private List<Map<String, Object>> averageAgeApprovedByCareerAndType(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("aprobado", true);
        match.put("edad_ingreso", new Document("$ne", null));
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: {
                    _id: { carrera: "$carrera_objetivo", tipo: "$tipo_ie", corr: "$correlativo_aspirante" },
                    edad: { $first: "$edad_ingreso" }
                } }
                """));
        pipeline.add(parse("""
                { $group: {
                    _id: { carrera: "$_id.carrera", tipo: "$_id.tipo" },
                    edad_promedio: { $avg: "$edad" },
                    muestras: { $sum: 1 }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    carrera: "$_id.carrera",
                    tipo: "$_id.tipo",
                    edad_promedio: { $round: ["$edad_promedio", 2] },
                    muestras: 1
                } }
                """));
        pipeline.add(sort("carrera", 1));
        return run(pipeline);
    }

    // 8. Distribución de aprobados por municipio y carrera objetivo
    private List<Map<String, Object>> approvedByMunicipalityAndCareer(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("aprobado", true);
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: {
                    _id: { municipio: "$municipio_ie", departamento: "$departamento_ie",
                            carrera: "$carrera_objetivo", corr: "$correlativo_aspirante" }
                } }
                """));
        pipeline.add(parse("""
                { $group: {
                    _id: { municipio: "$_id.municipio", departamento: "$_id.departamento",
                            carrera: "$_id.carrera" },
                    aprobados: { $sum: 1 }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    municipio: "$_id.municipio",
                    departamento: "$_id.departamento",
                    carrera: "$_id.carrera",
                    aprobados: 1
                } }
                """));
        pipeline.add(sort("aprobados", -1));
        pipeline.add(new Document("$limit", Math.min(limit(filters, 100), 500)));
        return run(pipeline);
    }

    // 9. Evaluaciones por mes y materia para instituciones públicas
    private List<Map<String, Object>> evaluationsByMonthAndSubject(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("tipo_ie", "PUBLICO");
        match.put("fecha_asignacion", new Document("$ne", null));
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: {
                    _id: { mes: { $month: "$fecha_asignacion" }, materia: "$materia" },
                    total: { $sum: 1 }
                } }
                """));
        pipeline.add(parse("""
                { $project: { _id: 0, mes: "$_id.mes", materia: "$_id.materia", total: 1 } }
                """));
        pipeline.add(sort("mes", 1));
        return run(pipeline);
    }

    // 10. Top 5 carreras más demandadas por aspirantes entre 16 y 18 años
    private List<Map<String, Object>> topCareersByAge(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("edad_ingreso", new Document("$gte", 16).append("$lte", 18));
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: {
                    _id: "$carrera_objetivo",
                    aspirantes: { $addToSet: "$correlativo_aspirante" }
                } }
                """));
        pipeline.add(parse("""
                { $project: { _id: 0, carrera: "$_id", total_aspirantes: { $size: "$aspirantes" } } }
                """));
        pipeline.add(sort("total_aspirantes", -1));
        pipeline.add(new Document("$limit", 5));
        return run(pipeline);
    }

    // 11. Historial de desempeño por aspirante
    private List<Map<String, Object>> performanceHistory(QueryFilters filters) {
        requireCorrelativo(filters);
        return MongoJson.toList(repository.findEvaluationsByAspirante(filters.correlativo()));
    }

    // 12. Distribución por sexo y tipo de institución educativa
    private List<Map<String, Object>> distributionBySexAndType(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        addMatch(pipeline, filters);
        pipeline.add(parse("""
                { $group: {
                    _id: { sexo: "$sexo", tipo: "$tipo_ie", corr: "$correlativo_aspirante" }
                } }
                """));
        pipeline.add(parse("""
                { $group: {
                    _id: { sexo: "$_id.sexo", tipo: "$_id.tipo" },
                    aspirantes: { $sum: 1 }
                } }
                """));
        pipeline.add(parse("""
                { $project: { _id: 0, sexo: "$_id.sexo", tipo: "$_id.tipo", aspirantes: 1 } }
                """));
        pipeline.add(sort("aspirantes", -1));
        return run(pipeline);
    }

    // 13. Tasa de aprobación por edad
    private List<Map<String, Object>> approvalRateByAge(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("edad_ingreso", new Document("$ne", null));
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: {
                    _id: "$edad_ingreso",
                    total: { $sum: 1 },
                    aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    edad: "$_id",
                    total: 1,
                    aprobados: 1,
                    porcentaje: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total"] }, 100] }, 2] }
                } }
                """));
        pipeline.add(sort("edad", 1));
        return run(pipeline);
    }

    // 14. Número promedio de intentos por materia
    private List<Map<String, Object>> averageAttemptsBySubject(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        addMatch(pipeline, filters);
        pipeline.add(parse("""
                { $group: {
                    _id: { materia: "$materia", corr: "$correlativo_aspirante" },
                    max_oportunidad: { $max: "$oportunidad" }
                } }
                """));
        pipeline.add(parse("""
                { $group: {
                    _id: "$_id.materia",
                    intentos_promedio: { $avg: "$max_oportunidad" },
                    aspirantes: { $sum: 1 }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    materia: "$_id",
                    intentos_promedio: { $round: ["$intentos_promedio", 2] },
                    aspirantes: 1
                } }
                """));
        pipeline.add(sort("intentos_promedio", -1));
        return run(pipeline);
    }

    // 15. Historial completo de un aspirante (documento embebido)
    private List<Map<String, Object>> fullAspirantHistory(QueryFilters filters) {
        requireCorrelativo(filters);
        Document aspirant = repository.findAspirante(filters.correlativo());
        if (aspirant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "No se encontró el aspirante con correlativo " + filters.correlativo());
        }
        return List.of(MongoJson.toMap(aspirant));
    }

    // 16. Carreras con más aspirantes reprobados en primer intento
    private List<Map<String, Object>> careersWithMostFirstAttemptFailures(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        Document match = baseMatch(filters);
        match.put("aprobado", false);
        match.put("oportunidad", 1);
        pipeline.add(new Document("$match", match));
        pipeline.add(parse("""
                { $group: {
                    _id: "$carrera_objetivo",
                    reprobados: { $addToSet: "$correlativo_aspirante" }
                } }
                """));
        pipeline.add(parse("""
                { $project: { _id: 0, carrera: "$_id", total_reprobados: { $size: "$reprobados" } } }
                """));
        pipeline.add(sort("total_reprobados", -1));
        pipeline.add(new Document("$limit", 10));
        return run(pipeline);
    }

    // 17. Top 5 municipios con mayor cantidad de aspirantes
    private List<Map<String, Object>> topMunicipalities(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        addMatch(pipeline, filters);
        pipeline.add(parse("""
                { $group: {
                    _id: { municipio: "$municipio_ie", departamento: "$departamento_ie" },
                    aspirantes: { $addToSet: "$correlativo_aspirante" }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    municipio: "$_id.municipio",
                    departamento: "$_id.departamento",
                    total_aspirantes: { $size: "$aspirantes" }
                } }
                """));
        pipeline.add(sort("total_aspirantes", -1));
        pipeline.add(new Document("$limit", 5));
        return run(pipeline);
    }

    // 18. Tasa de aprobación por tipo de institución educativa
    private List<Map<String, Object>> approvalRateByInstitutionType(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        addMatch(pipeline, filters);
        pipeline.add(parse("""
                { $group: {
                    _id: "$tipo_ie",
                    total: { $sum: 1 },
                    aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    tipo: "$_id",
                    total: 1,
                    aprobados: 1,
                    porcentaje: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total"] }, 100] }, 2] }
                } }
                """));
        pipeline.add(sort("porcentaje", -1));
        return run(pipeline);
    }

    // 19. Carreras más demandadas por departamento
    private List<Map<String, Object>> topCareersByDepartment(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        addMatch(pipeline, filters);
        pipeline.add(parse("""
                { $group: {
                    _id: { departamento: "$departamento_ie", carrera: "$carrera_objetivo",
                            corr: "$correlativo_aspirante" }
                } }
                """));
        pipeline.add(parse("""
                { $group: {
                    _id: { departamento: "$_id.departamento", carrera: "$_id.carrera" },
                    demanda: { $sum: 1 }
                } }
                """));
        pipeline.add(sort("_id.departamento", 1));
        pipeline.add(parse("""
                { $setWindowFields: {
                    partitionBy: "$_id.departamento",
                    sortBy: { demanda: -1 },
                    output: { rank: { $rank: {} } }
                } }
                """));
        pipeline.add(parse("""
                { $match: { rank: { $lte: 3 } } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    departamento: "$_id.departamento",
                    carrera: "$_id.carrera",
                    demanda: 1,
                    rank: 1
                } }
                """));
        pipeline.add(sort("departamento", 1));
        return run(pipeline);
    }

    // 20. Evolución de aprobación por año de ingreso
    private List<Map<String, Object>> approvalEvolutionByYear(QueryFilters filters) {
        List<Document> pipeline = new ArrayList<>();
        addMatch(pipeline, filters);
        pipeline.add(parse("""
                { $group: {
                    _id: "$anio_ingreso",
                    total: { $sum: 1 },
                    aprobados: { $sum: { $cond: ["$aprobado", 1, 0] } },
                    no_aprobados: { $sum: { $cond: ["$aprobado", 0, 1] } }
                } }
                """));
        pipeline.add(parse("""
                { $project: {
                    _id: 0,
                    anio: "$_id",
                    total: 1,
                    aprobados: 1,
                    no_aprobados: 1,
                    porcentaje_aprobacion: { $round: [{ $multiply: [{ $divide: ["$aprobados", "$total"] }, 100] }, 2] }
                } }
                """));
        pipeline.add(sort("anio", 1));
        return run(pipeline);
    }

    private List<Map<String, Object>> run(List<Document> pipeline) {
        return MongoJson.toList(repository.aggregate(EvaluationRepository.EVALUATIONS, pipeline));
    }

    private void addMatch(List<Document> pipeline, QueryFilters filters) {
        Document match = baseMatch(filters);
        if (!match.isEmpty()) {
            pipeline.add(new Document("$match", match));
        }
    }

    private Document baseMatch(QueryFilters filters) {
        Document match = new Document();
        if (filters != null) {
            if (filters.anio() != null) {
                match.put("anio_ingreso", filters.anio());
            }
            if (notBlank(filters.materia())) {
                match.put("materia", filters.materia());
            }
            if (notBlank(filters.carrera())) {
                match.put("carrera_objetivo", filters.carrera());
            }
            if (notBlank(filters.depto())) {
                match.put("departamento_ie", filters.depto());
            }
            if (notBlank(filters.tipo())) {
                match.put("tipo_ie", filters.tipo());
            }
        }
        return match;
    }

    private void requireCorrelativo(QueryFilters filters) {
        if (filters == null || !notBlank(filters.correlativo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Debe indicar el correlativo del aspirante");
        }
    }

    private int limit(QueryFilters filters, int defaultValue) {
        if (filters == null || filters.limit() == null) {
            return defaultValue;
        }
        return Math.min(Math.max(filters.limit(), 1), 1000);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private Document sort(String field, int direction) {
        return new Document("$sort", new Document(field, direction));
    }

    private Document parse(String json) {
        return Document.parse(json);
    }

    public record QueryFilters(
            Integer anio,
            String materia,
            String carrera,
            String depto,
            String tipo,
            Integer limit,
            String correlativo
    ) {
    }
}
