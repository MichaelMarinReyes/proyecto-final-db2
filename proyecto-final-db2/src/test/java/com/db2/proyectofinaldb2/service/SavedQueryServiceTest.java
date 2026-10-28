package com.db2.proyectofinaldb2.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.db2.proyectofinaldb2.exception.ApiException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.FindIterable;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;

class SavedQueryServiceTest {
    @Test
    void requiresAnExplicitlyConfiguredAdministrativeKey() {
        SavedQueryService disabled = new SavedQueryService(null, null, new CustomQueryPolicy(), "", 200, 10);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ApiException.class, () -> disabled.authorize("anything")).getStatus());
        String key = "test-only-administrative-key-32-characters";
        SavedQueryService service = new SavedQueryService(null, null, new CustomQueryPolicy(), key, 200, 10);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ApiException.class, () -> service.authorize(null)).getStatus());
        assertThrows(ApiException.class, () -> service.authorize("incorrect"));
        assertDoesNotThrow(() -> service.authorize(key));
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusesAnUnconfirmedWriteBeforeExecutingEitherDatabase() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Driver driver = mock(Driver.class);
        MongoCollection<Document> catalog = mock(MongoCollection.class);
        FindIterable<Document> found = mock(FindIterable.class);
        when(mongo.getCollection("consultas_personalizadas")).thenReturn(catalog);
        when(catalog.find(any(Document.class))).thenReturn(found);
        when(found.first()).thenReturn(new Document("motor", "NEO4J").append("codigo", "MATCH (n) DETACH DELETE n"));
        SavedQueryService service = new SavedQueryService(mongo, driver, new CustomQueryPolicy(), "", 200, 10);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.execute("id", false)).getStatus());
        verifyNoInteractions(driver);
        verify(mongo, times(1)).getCollection("consultas_personalizadas");
    }
}
