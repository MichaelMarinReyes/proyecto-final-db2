package com.db2.proyectofinaldb2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;

class GraphServiceTest {

    @Test
    void appliesExplorerFiltersAfterOptionalMatchAndUsesEvaluationCareer() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        TransactionContext transaction = mock(TransactionContext.class);
        Result result = mock(Result.class);
        when(driver.session()).thenReturn(session);
        when(transaction.run(anyString(), anyMap())).thenReturn(result);
        when(result.list()).thenReturn(List.of());
        when(session.executeRead(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(transaction);
        });

        new GraphService(driver).visualization("MEDICINA", "GUATEMALA", "BIOLOGIA", "PUBLICO", 2021, 1);

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(transaction).run(query.capture(), parameters.capture());
        assertTrue(query.getValue().contains("WHERE (e)-[:PARA_CARRERA]->(c)"));
        assertTrue(query.getValue().indexOf("WITH a, i, mu, d, c, e, mat")
                < query.getValue().indexOf("AND d.nombre = $depto"));
        assertTrue(query.getValue().contains("AND mat.nombre = $materia"));
        assertTrue(query.getValue().contains("AND e.anio_ingreso = $anio"));
        assertEquals(Map.of("carrera", "MEDICINA", "depto", "GUATEMALA", "materia", "BIOLOGIA",
                "tipo", "PUBLICO", "anio", 2021, "limite", 1), parameters.getValue());
        verify(session).close();
    }
}
