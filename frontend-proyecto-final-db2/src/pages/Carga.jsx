import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../api/client';
import UploadDragDrop from '../components/UploadDragDrop';
import TablaResultados from '../components/TablaResultados';
import Cargando from '../components/Cargando';
import { formatearDuracion, formatearFecha, formatearNumero } from '../utils/formato';
import useCarga from '../hooks/useCarga';

function EstadoBadge({ estado }) {
  return <span className={`badge estado-${String(estado || '').toLowerCase()}`}>{String(estado || '—').replaceAll('_', ' ')}</span>;
}

export default function Carga() {
  const { reporte, cargando, error, rechazados, subir, cargarRechazados, abrir } = useCarga();
  const [archivos, setArchivos] = useState([]);
  const [revision, setRevision] = useState(null);
  const [validando, setValidando] = useState(false);
  const [errorRevision, setErrorRevision] = useState('');
  const workerRef = useRef(null);
  useEffect(() => () => workerRef.current?.terminate(), []);
  const cambiarArchivos = (update) => {
    workerRef.current?.terminate();
    setValidando(false);
    setRevision(null);
    setErrorRevision('');
    setArchivos(update);
  };
  const [historial, setHistorial] = useState([]);
  const [cargandoHistorial, setCargandoHistorial] = useState(false);
  const [rechazadosVisibles, setRechazadosVisibles] = useState(false);
  const [cargandoRechazados, setCargandoRechazados] = useState(false);

  const cargarHistorial = useCallback(async () => {
    setCargandoHistorial(true);
    try {
      const { data } = await api.get('/cargas');
      setHistorial(data);
    } catch {
      setHistorial([]);
    } finally {
      setCargandoHistorial(false);
    }
  }, []);

  useEffect(() => {
    cargarHistorial();
  }, [cargarHistorial]);

  const estadoFinal = reporte && reporte.estado !== 'EN_PROCESO';

  useEffect(() => {
    if (estadoFinal) {
      cargarHistorial();
    }
  }, [estadoFinal, cargarHistorial]);

  const iniciar = async () => {
    setErrorRevision('');
    if (archivos.length > 20 || archivos.reduce((total, file) => total + file.size, 0) > 100 * 1024 * 1024) {
      setErrorRevision('Selecciona hasta 20 archivos y 100 MB en total. Divide los lotes mayores.');
      return;
    }
    setRevision(null);
    setValidando(true);
    try {
      workerRef.current?.terminate();
      const worker = new Worker(new URL('../workers/csvValidation.worker.js', import.meta.url), { type: 'module' });
      workerRef.current = worker;
      worker.onmessage = ({ data }) => {
        setValidando(false);
        if (data.error) setErrorRevision(data.error);
        else setRevision(data.results);
        worker.terminate();
      };
      worker.onerror = () => {
        setValidando(false);
        setErrorRevision('No fue posible revisar el CSV. Verifica su estructura y codificación UTF-8.');
        worker.terminate();
      };
      worker.postMessage(archivos);
    } catch {
      setValidando(false);
      setErrorRevision('No se pudo iniciar la revisión local. No se enviaron datos.');
    }
  };

  const confirmarEnvio = async () => {
    if (!revision?.length || revision.some((item) => item.csv === null)) return;
    const preparados = revision.map((item) => new File(['\uFEFF', item.csv], item.nombre, { type: 'text/csv;charset=utf-8' }));
    const resultado = await subir(preparados);
    if (resultado) {
      cambiarArchivos([]);
      setRechazadosVisibles(false);
    }
  };

  const verRechazados = async (id) => {
    setCargandoRechazados(true);
    setRechazadosVisibles(true);
    await cargarRechazados(id, 0, 200);
    setCargandoRechazados(false);
  };

  const abrirHistorial = async (id) => {
    const data = await abrir(id);
    if (data) {
      setRechazadosVisibles(false);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const filasHistorial = historial.map((carga) => ({
    id: carga.id || carga._id,
    estado: carga.estado,
    fechaInicio: carga.fechaInicio,
    duracionMs: carga.duracionMs,
    leidos: carga.totales?.leidos ?? 0,
    procesados: carga.totales?.procesados ?? 0,
    rechazados: carga.totales?.rechazados ?? 0,
    duplicados: carga.totales?.duplicados ?? 0
  }));

  const archivosReporte = (reporte?.archivos || []).map((archivo) => ({
    nombre: archivo.nombre,
    registrosLeidos: archivo.registrosLeidos,
    registrosProcesados: archivo.registrosProcesados,
    registrosRechazados: archivo.registrosRechazados,
    duplicadosOmitidos: archivo.duplicadosOmitidos,
    duracionMs: archivo.duracionMs,
    estado: archivo.estado
  }));

  return (
    <div className="pagina">
      <header className="pagina-encabezado">
        <div>
          <p className="eyebrow">Proceso ETL</p>
          <h2>Carga, validación y transformación</h2>
          <p>Los archivos se procesan en segundo plano y se cargan en MongoDB y Neo4j con operaciones idempotentes.</p>
        </div>
        <button type="button" className="boton boton-secundario" onClick={cargarHistorial} disabled={cargandoHistorial}>
          {cargandoHistorial ? 'Actualizando…' : 'Actualizar historial'}
        </button>
      </header>

      <div className="grid-carga">
        <UploadDragDrop
          archivos={archivos}
          onArchivos={(nuevos) => cambiarArchivos((actuales) => [...actuales, ...nuevos])}
          onQuitar={(indice) => cambiarArchivos((actuales) => actuales.filter((_, posicion) => posicion !== indice))}
          onSubir={iniciar}
          cargando={cargando || validando}
          progreso={reporte?.progreso ?? 0}
          error={errorRevision || error}
        />

        <aside className="tarjeta reglas-etl">
          <h3>Reglas aplicadas</h3>
          <ul>
            <li>Se detecta automáticamente el separador (<code>,</code>, <code>;</code> o tabulador).</li>
            <li>Las columnas se normalizan (tildes, mayúsculas y espacios).</li>
            <li><code>aprobacion</code> se convierte a <strong>APROBADO</strong> / <strong>NO APROBADO</strong>.</li>
            <li>Las filas con correlativo, materia, año u oportunidad inválidos se rechazan y se registran con su causa.</li>
            <li>Los duplicados se omiten mediante un índice único sobre <code>hash_fila</code>.</li>
            <li>La colección <code>aspirantes</code> se reconstruye al finalizar la carga.</li>
          </ul>
          <p className="ayuda">Columnas mínimas: correlativo_aspirante, materia, oportunidad, año de ingreso y aprobación.</p>
        </aside>
      </div>

      {validando && <p role="status">Revisando estructura y registros localmente. Todavía no se han enviado datos…</p>}
      {revision && (
        <section className="tarjeta" aria-label="Revisión previa" aria-live="polite">
          <h3>Revisión previa al envío</h3>
          <p>Los originales no se modifican. Solo se enviará el CSV preparado después de confirmar. El backend volverá a validar los datos.</p>
          <p>Fecha y año de nacimiento ausentes se conservan como nulos; carrera vacía se convierte a NINGUNO. Los avisos no bloquean el envío.</p>
          {revision.map((item, index) => (
            <div key={`${item.nombre}-${index}`}>
              <h4>{item.nombre}</h4>
              <p>Leídos: {item.leidos} · Registros inválidos: {item.invalidos} · Avisos: {item.avisos}</p>
              {item.errores.map((message) => <p role="alert" className="mensaje-error" key={message}>{message}</p>)}
              {item.detalles.length > 0 && <details><summary>Ver incidencias (máximo 100; numeración de registros sin encabezado)</summary><ul>{item.detalles.map((message, i) => <li key={i}>{message}</li>)}</ul></details>}
            </div>
          ))}
          {revision.some((item) => item.csv === null) && <p role="alert" className="mensaje-error">Envío bloqueado: corrige las filas o la estructura indicadas y vuelve a seleccionar los archivos. No se omiten filas silenciosamente.</p>}
          <button type="button" className="boton boton-primario" onClick={confirmarEnvio} disabled={cargando || revision.some((item) => item.csv === null)}>Confirmar y enviar datos preparados</button>
          <button type="button" className="boton boton-secundario" onClick={() => setRevision(null)} disabled={cargando}>Cancelar revisión</button>
        </section>
      )}

      {reporte && (
        <section className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Reporte de carga</p>
              <h3>{reporte.id}</h3>
              <p>{formatearFecha(reporte.fechaInicio)} {reporte.fechaFin ? `· finalizó ${formatearFecha(reporte.fechaFin)}` : ''}</p>
            </div>
            <div className="reporte-estado">
              <EstadoBadge estado={reporte.estado} />
              <span className="nota-tecnica">{formatearDuracion(reporte.duracionMs)}</span>
            </div>
          </div>

          {reporte.estado === 'EN_PROCESO' && (
            <div className="progreso-wrapper">
              <div className="progreso-texto">
                <span>Procesando en segundo plano…</span>
                <strong>{reporte.progreso ?? 0}%</strong>
              </div>
              <div className="progreso-barra">
                <div className="progreso-valor" style={{ width: `${Math.max(reporte.progreso ?? 0, 3)}%` }} />
              </div>
            </div>
          )}

          <div className="grid-resumen">
            <div><span>Leídos</span><strong>{formatearNumero(reporte.totales?.leidos)}</strong></div>
            <div><span>Procesados</span><strong>{formatearNumero(reporte.totales?.procesados)}</strong></div>
            <div><span>Rechazados</span><strong>{formatearNumero(reporte.totales?.rechazados)}</strong></div>
            <div><span>Duplicados omitidos</span><strong>{formatearNumero(reporte.totales?.duplicados)}</strong></div>
            <div><span>Pendientes de confirmar en MongoDB</span><strong>{formatearNumero(reporte.totales?.pendientes ?? 0)}</strong></div>
            <div><span>Sincronizados con Neo4j</span><strong>{formatearNumero(reporte.totales?.sincronizadosNeo4j ?? 0)}</strong></div>
          </div>
          {reporte.error && <p role="alert" className="mensaje-error">{reporte.error}</p>}
          {reporte.estado === 'ERROR' && <p className="mensaje-aviso">La carga no se completó. Puede haber datos parciales; procesados cuenta filas confirmadas en MongoDB (incluye duplicados), no garantiza sincronización con Neo4j.</p>}

          <TablaResultados
            titulo={`archivos-${reporte.id}`}
            filas={archivosReporte}
            columnas={['nombre', 'registrosLeidos', 'registrosProcesados', 'registrosRechazados', 'duplicadosOmitidos', 'duracionMs', 'estado']}
            paginada={false}
          />

          <div className="reporte-acciones">
            <button
              type="button"
              className="boton boton-secundario"
              onClick={() => verRechazados(reporte.id)}
              disabled={!reporte.totales?.rechazados}
            >
              Ver registros rechazados ({formatearNumero(reporte.totales?.rechazados)})
            </button>
          </div>

          {(reporte.archivos || []).some((archivo) => archivo.errores?.length > 0) && (
            <details className="detalle-errores" open>
              <summary>Errores detectados por fila (máximo 100 por archivo)</summary>
              {reporte.archivos.map((archivo) => (
                archivo.errores?.length > 0 && (
                  <div key={archivo.nombre} className="bloque-errores">
                    <h4>{archivo.nombre}</h4>
                    <ul>
                      {archivo.errores.map((filaError, indice) => (
                        <li key={`${archivo.nombre}-${indice}`}>
                          <span className="fila-numero">Fila {filaError.fila}</span>
                          <span>{filaError.causa}</span>
                          {filaError.valor && <code>{filaError.valor}</code>}
                        </li>
                      ))}
                    </ul>
                  </div>
                )
              ))}
            </details>
          )}
        </section>
      )}

      {rechazadosVisibles && (
        <section className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Auditoría</p>
              <h3>Registros rechazados</h3>
            </div>
            <button type="button" className="boton boton-secundario boton-pequeno" onClick={() => setRechazadosVisibles(false)}>Cerrar</button>
          </div>
          {cargandoRechazados ? (
            <Cargando mensaje="Consultando registros rechazados…" />
          ) : (
            <TablaResultados
              titulo="registros-rechazados"
              filas={rechazados}
              columnas={['archivo', 'fila', 'causa', 'valor']}
            />
          )}
        </section>
      )}

      <section className="tarjeta">
        <div className="tarjeta-encabezado">
          <div>
            <p className="etiqueta">Historial</p>
            <h3>Cargas realizadas</h3>
            <p>Se conservan los últimos 50 procesos registrados en la colección <code>cargas</code>.</p>
          </div>
        </div>
        {cargandoHistorial ? (
          <Cargando mensaje="Leyendo historial de cargas…" />
        ) : filasHistorial.length === 0 ? (
          <p className="vacio-mini">Todavía no se ha realizado ninguna carga.</p>
        ) : (
          <>
            <TablaResultados
              titulo="historial-cargas"
              filas={filasHistorial}
              columnas={['id', 'estado', 'fechaInicio', 'duracionMs', 'leidos', 'procesados', 'rechazados', 'duplicados']}
            />
            <div className="historial-acciones">
              {filasHistorial.slice(0, 5).map((fila) => (
                <button key={fila.id} type="button" className="boton boton-secundario boton-pequeno" onClick={() => abrirHistorial(fila.id)}>
                  Abrir {fila.id.slice(-8)}
                </button>
              ))}
            </div>
          </>
        )}
      </section>
    </div>
  );
}
