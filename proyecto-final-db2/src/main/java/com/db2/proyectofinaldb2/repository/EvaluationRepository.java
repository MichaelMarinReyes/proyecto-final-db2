package com.db2.proyectofinaldb2.repository;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class EvaluationRepository {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRepository.class);

    public static final String EVALUATIONS = "evaluaciones";
    public static final String ASPIRANTS = "aspirantes";
    public static final String LOADS = "cargas";
    public static final String REJECTED = "registros_rechazados";
    public static final String CAREER_SUMMARY = "resumen_carrera";

    private final MongoTemplate mongoTemplate;

    public EvaluationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void ensureIndexes() {
        MongoCollection<Document> collection = mongoTemplate.getCollection(EVALUATIONS);
        collection.createIndex(Indexes.ascending("correlativo_aspirante"));
        collection.createIndex(Indexes.ascending("materia", "aprobado"));
        collection.createIndex(Indexes.ascending("carrera_objetivo", "anio_ingreso"));
        collection.createIndex(Indexes.ascending("tipo_ie"));
        collection.createIndex(Indexes.ascending("municipio_ie"));
        collection.createIndex(Indexes.ascending("departamento_ie"));
        collection.createIndex(Indexes.ascending("anio_ingreso"));
        collection.createIndex(Indexes.ascending("edad_ingreso"));
        collection.createIndex(Indexes.ascending("sexo", "tipo_ie"));
        collection.createIndex(Indexes.ascending("hash_fila"), new IndexOptions().unique(true).sparse(true));
        mongoTemplate.getCollection(REJECTED).createIndex(Indexes.ascending("carga_id"));
        log.info("Índices de MongoDB verificados");
    }

    public InsertResult insertBatch(List<Document> documents) {
        if (documents.isEmpty()) {
            return new InsertResult(0, 0, 0);
        }
        try {
            mongoTemplate.getCollection(EVALUATIONS)
                    .insertMany(documents, new InsertManyOptions().ordered(false));
            return new InsertResult(documents.size(), 0, 0);
        } catch (MongoBulkWriteException exception) {
            if (exception.getWriteConcernError() != null) {
                // A write concern failure has an uncertain outcome, not a confirmed rejection.
                throw exception;
            }
            int duplicates = 0;
            int failed = 0;
            List<Integer> failedIndexes = new ArrayList<>();
            for (var error : exception.getWriteErrors()) {
                if (error.getCode() == 11000) {
                    duplicates++;
                } else {
                    failed++;
                    failedIndexes.add(error.getIndex());
                    log.error("Error al insertar evaluación: {}", error.getMessage());
                }
            }
            return new InsertResult(exception.getWriteResult().getInsertedCount(), duplicates, failed, failedIndexes);
        }
    }

    public void insertRejected(List<Document> documents) {
        if (!documents.isEmpty()) {
            mongoTemplate.getCollection(REJECTED)
                    .insertMany(documents, new InsertManyOptions().ordered(false));
        }
    }

    /**
     * Rebuilds the embedded aspirante documents from the flat evaluation
     * collection. This keeps both representations consistent and makes the ETL
     * idempotent (running it twice produces the same result).
     */
    public void rebuildAspirantes() {
        List<Document> pipeline = List.of(
                Document.parse("{ $sort: { anio_ingreso: 1, oportunidad: 1, fecha_asignacion: 1, _id: 1 } }"),
                Document.parse("""
                        { $group: {
                            _id: "$correlativo_aspirante",
                            sexo: { $first: "$sexo" },
                            anio_nacimiento: { $first: "$anio_nacimiento" },
                            total_evaluaciones: { $sum: 1 },
                            materias: { $addToSet: "$materia" },
                            carreras_objetivo: { $addToSet: "$carrera_objetivo" },
                            evaluaciones: { $push: "$$ROOT" }
                        } }
                        """),
                Document.parse("{ $set: { actualizado_en: \"$$NOW\" } }"),
                Document.parse("{ $out: \"aspirantes\" }")
        );
        mongoTemplate.getCollection(EVALUATIONS).aggregate(pipeline).into(new ArrayList<>());
        log.info("Colección de aspirantes reconstruida");
    }

    public List<Document> aggregate(String collection, List<Document> pipeline) {
        return mongoTemplate.getCollection(collection)
                .aggregate(pipeline)
                .into(new ArrayList<>());
    }

    public Document findAspirante(String correlativo) {
        return mongoTemplate.getCollection(ASPIRANTS)
                .find(new Document("_id", correlativo))
                .first();
    }

    public List<Document> findEvaluationsByAspirante(String correlativo) {
        Query query = new Query(Criteria.where("correlativo_aspirante").is(correlativo))
                .with(Sort.by(Sort.Direction.ASC, "anio_ingreso", "oportunidad", "fecha_asignacion"));
        return mongoTemplate.find(query, Document.class, EVALUATIONS);
    }

    public List<Document> findRejected(String cargaId, int page, int size) {
        Query query = new Query(Criteria.where("carga_id").is(cargaId))
                .with(Sort.by(Sort.Direction.ASC, "archivo", "fila"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Document.class, REJECTED);
    }

    public long count(String collection) {
        return mongoTemplate.getCollection(collection).estimatedDocumentCount();
    }

    public void saveLoad(Document document) {
        mongoTemplate.getCollection(LOADS).insertOne(document);
    }

    public void replaceLoad(Document document) {
        Document id = new Document("_id", document.get("_id"));
        mongoTemplate.getCollection(LOADS).replaceOne(id, document,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    public List<Document> findLoads(int limit) {
        return mongoTemplate.getCollection(LOADS)
                .find()
                .sort(new Document("fechaInicio", -1))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public Document findLoad(String id) {
        return mongoTemplate.getCollection(LOADS).find(new Document("_id", id)).first();
    }

    public record InsertResult(int inserted, int duplicates, int failed, List<Integer> failedIndexes) {
        public InsertResult(int inserted, int duplicates, int failed) {
            this(inserted, duplicates, failed, List.of());
        }
    }
}
