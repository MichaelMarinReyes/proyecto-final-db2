import { useCallback, useEffect, useState } from 'react';
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
    const resultado = await subir(archivos);
    if (resultado) {
      setArchivos([]);
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
          onArchivos={(nuevos) => setArchivos((actuales) => [...actuales, ...nuevos])}
          onQuitar={(indice) => setArchivos((actuales) => actuales.filter((_, posicion) => posicion !== indice))}
          onSubir={iniciar}
          cargando={cargando}
          progreso={reporte?.progreso ?? 0}
          error={error}
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
          <p className="ayuda">Columnas mínimas: correlativo_aspirante, materia, año de ingreso y aprobación.</p>
        </aside>
      </div>

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
          </div>

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
