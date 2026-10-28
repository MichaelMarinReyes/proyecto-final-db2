# Auditoría de arquitectura y comunicación

**Fecha:** 27 de octubre de 2026.  
**Revisión auditada:** `4507864`, con árbol de trabajo limpio al comenzar.  
**Modalidad:** análisis estático del código y de la configuración. No se modificó código de aplicación ni se iniciaron/detuvieron servicios.

## 1. Veredicto ejecutivo

**El proyecto cumple la separación funcional principal React → API Spring Boot → MongoDB/Neo4j, pero no cumple de forma estricta todos los límites de responsabilidad descritos.**

- No se encontraron conexiones programáticas, drivers de bases de datos, credenciales de bases de datos ni consultas ejecutables a MongoDB/Neo4j en el frontend.
- Las consultas de la aplicación se construyen y ejecutan en el backend.
- Existe un enlace desde la interfaz a Neo4j Browser que ofrece una ruta administrativa fuera de la API. No debe confundirse con una conexión directa realizada por React.
- El dashboard calcula un indicador de negocio en React, en lugar de recibirlo calculado por el backend.
- La separación interna del backend no es uniforme: `HealthController` utiliza directamente los clientes de bases de datos; algunos servicios también mezclan orquestación y persistencia.
- La validación y el manejo de errores existen, pero presentan las brechas detalladas más adelante.

**Resultado:** cumplimiento parcial respecto de una interpretación estricta; no se justifica declarar un incumplimiento general de aislamiento programático frontend/BD, porque tal conexión no existe en el código revisado.

## 2. Alcance y criterios

Se revisaron los 23 archivos de `frontend-proyecto-final-db2/src/` —22 JavaScript/JSX y una hoja de estilos—, los 29 archivos Java de producción del backend, sus 3 archivos de pruebas, los manifiestos de dependencias, el lockfile del frontend, las entradas HTML/Vite, `application.yml` y `docker-compose.yml`.

Se excluyeron de la revisión manual del código propio las dependencias instaladas, binarios generados, cachés, archivos de IDE y el historial completo de Git. El lockfile sí se inspeccionó para detectar dependencias de conexión a bases de datos.

La referencia utilizada es la arquitectura y las reglas escritas en la solicitud. La etiqueta `[cite: 1]` no contiene una fuente externa adjunta que permita verificar requisitos adicionales. Las convenciones internas de `docs/PLAN.md` se distinguen de los requisitos de tres capas: exigir un repositorio separado para cada driver es una decisión adicional, no una consecuencia automática del patrón de tres capas.

Esta auditoría no constituye una prueba de penetración, una revisión de vulnerabilidades de todas las dependencias ni una nueva ejecución de pruebas funcionales. Las conclusiones sobre conexiones y responsabilidades se basan en el código actual, no en pruebas históricas de sesiones anteriores.

## 3. Estructura y flujo observado

```text
frontend-proyecto-final-db2/
  src/pages/ + components/       Presentación y estado de interacción
  src/hooks/                    Peticiones HTTP y seguimiento de cargas
  src/api/client.js             Axios con base /api o VITE_API_URL
          |
          | HTTP / JSON / multipart
          v
proyecto-final-db2/
  controller/                   Endpoints REST
  service/                      ETL, validación, agregaciones y orquestación
  repository/                   Acceso MongoDB
  neo4j/                        Catálogo Cypher
  config/                       Clientes, índices, restricciones y CORS
  dto/ + exception/ + util/      Contratos, errores y serialización
          |
          +---- MongoDB
          +---- Neo4j
```

### Evidencia de comunicación del frontend

Las rutas siguientes usan el cliente `api`, no drivers:

| Función | Evidencia en frontend | Destino |
|---|---|---|
| Configuración HTTP | `src/api/client.js:1-6` | `/api` o URL configurada del backend |
| Proxy de desarrollo | `vite.config.js:8-12` | `http://localhost:8080` |
| Consultas MongoDB | `src/hooks/useConsultas.js:20-38,44-56` | `/consultas/mongo/{numero}` y POST de resumen |
| Carga y polling | `src/hooks/useCarga.js:20-60` | `/cargas/archivos`, `/cargas/{id}` |
| Catálogos | `src/hooks/useCatalogos.js:18-32` | `/catalogos/{tipo}` |
| Búsqueda y ficha | `src/pages/Buscador.jsx:29-65` | `/buscar`, `/aspirantes/{correlativo}` |
| Grafos | `src/pages/ExploradorGrafos.jsx:81-148` | `/grafo/visualizacion`, `/grafo/nodo/{id}`, `/grafo/consultas/{numero}` |
| Salud | `src/components/Layout.jsx:34-49` | `/salud` |

Todas las rutas relativas de esa tabla quedan prefijadas por la URL del cliente Axios. `VITE_API_URL` es configuración pública del endpoint HTTP; no debe contener credenciales ni una URI de base de datos.

### Dependencias y falsos positivos descartados

- `package.json` y `package-lock.json` no declaran `mongodb`, `mongoose`, `neo4j-driver`, `neo4j-client`, `neovis.js` ni `gremlin`.
- No se encontraron instancias de `MongoClient`, sesiones Neo4j, peticiones HTTP a endpoints de consultas de las bases, conexiones Bolt ni URIs MongoDB en el código fuente del frontend.
- `react-force-graph-2d`, utilizado en `src/components/GrafoCanvas.jsx:23-55`, recibe nodos y relaciones como datos JSON. Es una biblioteca de visualización, no un cliente Neo4j.
- Los textos `$group`, `$merge`, `MATCH` o `shortestPath` de los catálogos de la interfaz son descripciones de consultas. No se ejecutan como MongoDB o Cypher desde React.
- No se encontraron archivos `.env*` en la raíz, en la raíz del frontend ni en la raíz del backend al realizar esta revisión.

## 4. Ubicación de consultas y conexiones

Los archivos de la tabla son relativos a `proyecto-final-db2/src/main/java/com/db2/proyectofinaldb2/`:

| Archivo | Responsabilidad verificada |
|---|---|
| `service/MongoQueryService.java:22-559` | Selección de C1–C20, construcción de pipelines y conversión de resultados |
| `repository/EvaluationRepository.java:36-165` | Índices, inserciones, consultas, reconstrucción con `$out` y persistencia de cargas |
| `service/SearchService.java:60-159` | Consultas y agregaciones de búsqueda; escape de expresiones regulares |
| `service/CatalogService.java:22-63` | Agregaciones de catálogos con campos seleccionados por el servidor |
| `neo4j/CypherCatalog.java:12-149` | Restricciones, índices, carga y consultas G1–G7, visualización y detalle |
| `service/GraphService.java:31-158` | Selección de Cypher, parámetros y ejecución en sesiones del driver |
| `service/EtlService.java:124-158` | Escritura de lotes MongoDB/Neo4j |
| `config/Neo4jConfig.java:18-23` | Creación del driver y autenticación en el backend |
| `config/Neo4jSchemaInitializer.java:26-37` | Ejecución de DDL de Neo4j al iniciar |
| `controller/HealthController.java:18-55` | Ping MongoDB, conectividad Neo4j y conteos; excepción al orden interno de capas |

Las consultas MongoDB usan `Document`, `Criteria` y pipelines construidos por el servidor. Las consultas Cypher reciben parámetros separados (`transaction.run(query, params)`); no se recibe Cypher arbitrario desde React. Los fragmentos dinámicos del explorador son constantes del backend, no texto de consulta proporcionado por el cliente.

Los ejemplos de consultas en `documentacion/` y los comandos administrativos de Docker no son consultas ejecutadas por la aplicación frontend.

## 5. Hallazgos y refactorizaciones

### A-01. Acceso a la consola de Neo4j desde la interfaz

**Prioridad:** media para el cumplimiento estricto; mayor relevancia al desplegar públicamente.  
**Archivo:** `frontend-proyecto-final-db2/src/pages/ExploradorGrafos.jsx:168`.

```jsx
<a href="http://localhost:7474" target="_blank" rel="noreferrer">
  Abrir Neo4j Browser
</a>
```

**Evaluación:** el enlace abre directamente la interfaz administrativa de Neo4j. React no consulta ni se autentica por sí mismo, pero el usuario puede continuar en una herramienta que consulta la base sin pasar por la API del proyecto, si dispone de conectividad y credenciales. Por ello no debe presentarse como una conexión programática frontend/BD, aunque sí contradice una experiencia de usuario estrictamente mediada por el backend. Además, `localhost` corresponde al equipo del usuario, no necesariamente al servidor de la aplicación.

**Refactorización:** retirar el botón de la interfaz funcional y dejar el acceso administrativo documentado fuera de ella. No sustituirlo por un endpoint que acepte Cypher arbitrario ni por un túnel genérico a la base. Mantener los endpoints acotados de `GrafoController`.

### A-02. Cálculo de un indicador de negocio en la vista React

**Prioridad:** media.  
**Archivo:** `frontend-proyecto-final-db2/src/pages/Dashboard.jsx:60-67,95-96`.

El dashboard suma aprobaciones y evaluaciones de C18 y calcula `tasaGlobal` en JavaScript. La fórmula de la tasa de aprobación es una definición del dominio, no únicamente formato visual. Bajo la separación estricta solicitada, debe ser responsabilidad del backend.

También existe un fallback en la tarjeta de carreras: `carreras.length || (datos.c18 || []).length`. La longitud de C18 representa tipos de institución, no carreras; no debe sustituir ese indicador.

**Refactorización:** crear `DashboardService` y un endpoint como `GET /api/dashboard/resumen` que entregue `totalEvaluaciones`, `totalAspirantes`, `totalCarreras`, `totalAprobados` y `tasaAprobacion`. Mantener en React únicamente el renderizado, formato numérico y selección visual de las series. Separar estos indicadores de `/api/salud` para que la supervisión de infraestructura no sea el contrato de datos del dashboard.

La ordenación, paginación y exportación de una tabla ya recibida no se consideran por sí mismas violaciones de la arquitectura de tres capas.

### A-03. El controlador de salud contiene acceso a persistencia

**Prioridad:** media si se exige también `controller → service → repository/cliente`; recomendación interna respecto de la arquitectura de tres capas.  
**Archivo:** `proyecto-final-db2/src/main/java/com/db2/proyectofinaldb2/controller/HealthController.java:18-55`.

Inyecta `MongoTemplate`, `Driver` y `EvaluationRepository`; ejecuta el ping, verifica conectividad y obtiene conteos dentro del controlador HTTP.

**Impacto:** mezcla transporte HTTP con detalles de infraestructura y dificulta sustituir o probar la supervisión sin drivers. No supone una conexión frontend/BD: todo este código sigue ejecutándose dentro del backend.

**Refactorización:** introducir `HealthService` y, si se busca mayor aislamiento, adaptadores `MongoHealthProbe` y `Neo4jHealthProbe`. El controlador debería limitarse a delegar y devolver un `HealthResponse` definido.

`SearchService.java:19-24,60-66`, `GraphService.java:25-28,63-69` y `EtlService.java:29-38,149-158` también utilizan clientes de persistencia desde servicios. Esto es válido en una arquitectura de tres capas, pero no en una convención adicional que reserve todos los drivers exclusivamente a repositorios/adaptadores. Para esa convención, extraer `AspirantRepository` y `GraphRepository`/`GraphClient` y reutilizarlos en el ETL.

### A-04. Validación de aprobación demasiado permisiva

**Prioridad:** media, afecta integridad de datos.  
**Archivo:** `proyecto-final-db2/src/main/java/com/db2/proyectofinaldb2/service/TransformationService.java:75-86`.

Se usa `contains("NO APROB")` y después `contains("APROB")`. Por inspección del código, valores no incluidos en el catálogo documentado, como `DESAPROBADO`, `NO_APROBADO` o `APROBACION PENDIENTE`, alcanzan la rama de aprobación positiva. No se trata de una consulta desde el frontend: la ubicación de la validación es correcta, pero la regla es insuficiente.

**Refactorización:** normalizar los separadores permitidos y usar un mapa cerrado de equivalencias o un enum. Los valores fuera de ese catálogo deben rechazarse, no inferirse por subcadenas. Añadir pruebas negativas de esos valores, además de las pruebas actuales con `PENDIENTE`.

La validación del backend sí existe: extensión y archivo vacío en `CargaService.java:47-87`, encabezados en `EtlService.java:81-94`, campos obligatorios en `TransformationService.java:54-86`, tipo/término de búsqueda en `SearchService.java:27-44` y requisitos de G7 en `GraphService.java:58-60`. Las validaciones del frontend son auxiliares, no la única defensa.

### A-05. Control de errores y limpieza incompletos

**Prioridad:** media.  
**Archivos:**

- `controller/HealthController.java:35-47`: devuelve mensajes originales de las excepciones en `detalle`.
- `service/EtlService.java:52-56`: copia el mensaje original de cualquier excepción en el reporte de archivo.
- `service/CargaService.java:91-105`: operaciones de persistencia pueden impedir la limpieza de temporales.

Las rutas de los tres archivos anteriores son relativas al paquete Java del backend.

**Impacto:** algunos errores pueden revelar direcciones o detalles técnicos de los drivers. Además, si `ensureIndexes()` o el registro inicial de la carga fallan, los temporales ya copiados no quedan cubiertos por la limpieza de esa fase. En el worker, si `replaceLoad()` falla dentro de `finally`, no se ejecuta `cleanup(tempDir)`. La carga puede quedar registrada en memoria sin una ejecución o persistencia final coherente.

**Refactorización:** devolver mensajes públicos estables y registrar el diagnóstico detallado solo en logs con un identificador de incidente. Envolver la preparación completa en una gestión de recursos que limpie ante fallos. Usar un `try/finally` anidado para que el fallo al guardar el reporte no impida liberar temporales; retirar o marcar el job fallido y gestionar también el rechazo del executor.

`GlobalExceptionHandler.java:26-89` ya ofrece un contrato JSON y maneja errores frecuentes, pero no elimina estas excepciones locales de la política de errores. No todos los fallos Neo4j/MongoDB son necesariamente errores de conectividad: conviene distinguir indisponibilidad, error de consulta y error interno, en lugar de traducir cualquier excepción del driver a 503.

### A-06. La configuración de despliegue no garantiza aislamiento de red

**Prioridad:** alta si se publica el despliegue; endurecimiento adicional para el entorno local.  
**Archivos:**

- `docker-compose.yml:6-9,22-26`: publica puertos de MongoDB y Neo4j sin restringir la dirección de escucha; no configura autenticación de MongoDB.
- `proyecto-final-db2/src/main/resources/application.yml:11-16`: admite una contraseña Neo4j predeterminada.
- `proyecto-final-db2/src/main/java/com/db2/proyectofinaldb2/config/Neo4jConfig.java:20-23`: repite un valor predeterminado de contraseña.

**Evaluación:** esto no demuestra que React esté conectando a las bases ni que los puertos sean accesibles desde Internet; el acceso efectivo depende de la red y el firewall. Sí significa que el aislamiento «solo backend» no está impuesto por esta configuración. Las credenciales locales se encuentran en el repositorio/configuración, no en el bundle fuente de React.

**Refactorización:**

- En desarrollo con backend en el host, enlazar los puertos de datos a `127.0.0.1` y evitar la consola administrativa desde la UI.
- En producción, colocar backend y bases en una red privada; no publicar los puertos de las bases. Usar nombres de servicio en las URIs internas del backend.
- Activar autenticación MongoDB, usar cuentas de aplicación de mínimo privilegio y exigir secretos de entorno sin contraseña predeterminada de producción.
- Mantener Neo4j Browser accesible únicamente mediante una vía administrativa restringida, si se necesita.

CORS en `WebConfig.java:21-27` regula llamadas HTTP del navegador a la API; no sustituye autenticación ni firewall de las bases de datos.

## 6. Otras mejoras recomendadas, no conexiones frontend/BD

| Evidencia | Observación | Acción concreta |
|---|---|---|
| `ConsultaMongoController.java:24-39`; `MongoQueryService.java:29,162-203` | GET C6 ejecuta `$merge`, aunque también existe un POST de regeneración | Separar lectura del resumen y regeneración; GET solo lee y POST materializa |
| `util/MongoJson.java:23-29`; `service/SearchService.java:47-53`; `service/CargaService.java:110-123` | Convertir BSON a mapas normaliza tipos, pero no define una lista permitida de campos de respuesta | Usar DTOs/proyecciones para contratos de aspirante, carga y salud; no confiar en la conversión genérica como filtro |
| `ConsultaMongoController.java:25-33`; `GrafoController.java:24-41` | Hay conversión de tipos, pero no un contrato uniforme para rangos de año, longitud de cadenas y límites | Introducir DTOs de filtros validados y definir cuándo se devuelve 400 y cuándo se ajusta un límite |
| Pruebas actuales del backend | No hay reglas automatizadas que impidan futuras dependencias de drivers en controladores o frontend | Añadir ArchUnit si se adopta la convención interna; restringir imports del frontend y verificar destinos HTTP en CI |

Los DTOs tipados no son obligatorios para que exista separación frontend/backend/BD; ayudan a mantenerla sin exponer accidentalmente cambios del modelo almacenado.

## 7. Matriz de cumplimiento

| Regla solicitada | Estado | Justificación |
|---|---|---|
| Frontend React/Angular | Cumple | React 18, React Router y componentes JSX |
| Backend Spring Boot/Node.js | Cumple | Spring Boot, Java y controladores REST |
| MongoDB y Neo4j gestionados por backend | Cumple para las operaciones de aplicación | Drivers y ejecución de consultas solo en Java |
| Sin conexiones programáticas frontend/BD | Cumple | Axios a la API; sin drivers ni consultas directas en React |
| Interfaz estrictamente mediada por API, sin salida administrativa | Con observación | Enlace a Neo4j Browser, A-01 |
| Lógica de aplicación concentrada en backend | Parcial bajo criterio estricto | Indicador global calculado en la vista, A-02 |
| Backend recibe y valida datos | Parcial | Validación implementada; catálogo de aprobación permisivo, A-04 |
| Backend ejecuta y procesa consultas MongoDB/Neo4j | Cumple en lo principal | Pipelines, Cypher, parámetros y serialización en Java |
| Backend envía resultados a la UI | Cumple | JSON mediante controladores y DTOs/mapas |
| Errores controlados de manera uniforme | Parcial | Manejador global existente; brechas locales de diagnóstico y limpieza, A-05 |
| Subcapas controller/service/persistencia uniformes | Parcial, requisito interno adicional | Controlador de salud y servicios con clientes directos, A-03 |
| Aislamiento de red impuesto por despliegue | No garantizado | Puertos publicados y credenciales predeterminadas, A-06 |

## 8. Plan de corrección y criterios de aceptación

1. Retirar el acceso administrativo Neo4j de la UI funcional y restringir los puertos/secretos según el ambiente.
2. Mover los KPIs a `DashboardService` y ofrecer un contrato HTTP propio para el dashboard.
3. Extraer `HealthService` y, si se exige una capa de persistencia uniforme, encapsular los drivers en adaptadores.
4. Cerrar el catálogo de aprobación y añadir pruebas de valores ambiguos.
5. Garantizar limpieza de temporales y mensajes de error públicos estables ante fallas de persistencia.
6. Separar GET/POST del resumen y añadir pruebas arquitectónicas automatizadas.

Antes de declarar cumplimiento estricto, verificar:

- Ningún flujo de la UI abre una consola o endpoint de bases de datos.
- Las solicitudes de datos del navegador apuntan únicamente a la API autorizada.
- No hay drivers ni secretos de bases de datos en fuentes o artefactos del frontend.
- El backend calcula la tasa global y el frontend únicamente la presenta.
- Los valores de aprobación no reconocidos son rechazados de forma determinista.
- Un fallo al registrar una carga o su reporte final no deja temporales sin limpiar.
- Los errores públicos no incluyen mensajes técnicos crudos del driver.
- En el despliegue objetivo, un cliente externo no puede acceder directamente a los puertos de datos.

## 9. Conclusión

No es necesario rehacer la arquitectura: sus tres componentes principales existen y la ruta programática de acceso a datos es correcta. Sin embargo, **no corresponde certificar cumplimiento estricto sin observaciones**. Se requieren correcciones acotadas en la consola administrativa enlazada, el KPI calculado en React, la uniformidad interna del backend y el control de validaciones/errores. La exposición de red y las credenciales predeterminadas deben resolverse antes de un despliegue público.

## 10. Seguimiento: configuración privada

Posteriormente a la instantánea auditada, se migró la configuración operativa a cuatro `.env` ignorados por Git, se eliminaron las contraseñas predeterminadas de YAML/Java/documentación y se retiró el enlace a Neo4j Browser de la interfaz. `application.yml` conserva únicamente configuración estática. Vite mantiene la exposición al navegador restringida a `VITE_*`.

Esto atiende A-01 y la parte de A-06 relativa a configuración privada; no implementa autenticación MongoDB, aislamiento adicional de red ni rotación de secretos publicados previamente. Los demás hallazgos del informe no se consideran corregidos por esta migración. Ver [configuracion-local.md](configuracion-local.md).

## 11. Cambio de alcance: biblioteca administrativa

Posteriormente se solicitó permitir consultas personalizadas de lectura y escritura para ambos motores. Ahora existe una API administrativa que recibe definiciones, las persiste en MongoDB y las ejecuta exclusivamente desde el backend. Todas sus rutas requieren una clave privada, las escrituras exigen confirmación y se aplican restricciones de operaciones, colecciones y comandos administrativos. No se distribuyen drivers ni credenciales de las bases al frontend. Esta capacidad no existía en la instantánea inicial de la auditoría; consultar sus riesgos y límites en [consultas-personalizadas.md](consultas-personalizadas.md).
