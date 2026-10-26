package com.db2.proyectofinaldb2.util;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

/**
 * Converts Mongo documents into plain Java maps so Jackson can serialise them
 * without exposing ObjectId/Date internals to the frontend.
 */
public final class MongoJson {

    private MongoJson() {
    }

    public static Map<String, Object> toMap(Document document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        document.forEach((key, value) -> result.put(key, normalize(value)));
        return result;
    }

    public static List<Map<String, Object>> toList(List<Document> documents) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document document : documents) {
            result.add(toMap(document));
        }
        return result;
    }

    public static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (value instanceof Date date) {
            return date.toInstant().atOffset(ZoneOffset.UTC).toString();
        }
        if (value instanceof Decimal128 decimal) {
            BigDecimal number = decimal.bigDecimalValue();
            return number;
        }
        if (value instanceof Document document) {
            return toMap(document);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), normalize(mapValue)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(normalize(item)));
            return result;
        }
        return value;
    }
}
