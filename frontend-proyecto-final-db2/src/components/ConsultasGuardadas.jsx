import { useState } from 'react';
import api from '../api/client';
import TablaResultados from './TablaResultados';

const EJEMPLOS = {
  MONGODB: JSON.stringify({ operacion: 'aggregate', coleccion: 'evaluaciones', pipeline: [
    { $group: { _id: '$materia', total: { $sum: 1 } } }, { $sort: { total: -1 } }
  ] }, null, 2),
  NEO4J: 'MATCH (a:Aspirante)-[:ASPIRA_A]->(c:Carrera)\nRETURN c.nombre AS carrera, count(DISTINCT a) AS aspirantes\nORDER BY aspirantes DESC\nLIMIT 20'
};

export default function ConsultasGuardadas({ motor }) {
  const [clave, setClave] = useState('');
  const [acceso, setAcceso] = useState(false);
  const [consultas, setConsultas] = useState([]);
  const [nombre, setNombre] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [codigo, setCodigo] = useState(EJEMPLOS[motor]);
  const [seleccionada, setSeleccionada] = useState(null);
  const [resultado, setResultado] = useState(null);
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [confirmacion, setConfirmacion] = useState('');
  const [editando, setEditando] = useState(true);
  const headers = { 'X-Query-Admin-Key': clave };

  const desbloquear = async (event) => {
    event.preventDefault();
    setOcupado(true);
    setError('');
    try {
      const { data } = await api.get('/consultas/guardadas', { params: { motor }, headers });
      setConsultas(data);
      setAcceso(true);
      setEditando(true);
    } catch (err) { setError(err.message); }
    finally { setOcupado(false); }
  };

  const guardar = async (event) => {
    event.preventDefault();
    setOcupado(true);
    setError('');
    setMensaje('');
    try {
      const { data } = await api.post('/consultas/guardadas', { nombre, descripcion, motor, codigo }, { headers });
      setConsultas((previas) => [data, ...previas]);
      setSeleccionada(data);
      setResultado(null);
      setConfirmacion('');
      setNombre('');
      setDescripcion('');
      setMensaje('Consulta guardada. Guardar no ejecuta ni modifica los datos.');
    } catch (err) { setError(err.message); }
    finally { setOcupado(false); }
  };

  const ejecutar = async () => {
    const escrituraConfirmada = seleccionada.modo === 'ESCRITURA' && confirmacion === 'EJECUTAR';
    setConfirmacion('');
    setOcupado(true);
    setError('');
    setResultado(null);
    setMensaje('');
    try {
      const { data } = await api.post(`/consultas/guardadas/${encodeURIComponent(seleccionada._id)}/ejecutar`, null, {
        headers, params: { confirmarEscritura: escrituraConfirmada }
      });
      setResultado(data);
      setConfirmacion('');
      setMensaje('Consulta ejecutada. Actualiza el dashboard o el grafo para ver los cambios.');
    } catch (err) { setError(err.message); }
    finally { setOcupado(false); }
  };

  const cancelar = () => {
    setNombre('');
    setDescripcion('');
    setCodigo(EJEMPLOS[motor]);
    setError('');
    setMensaje('');
    setEditando(false);
  };

  const bloquear = () => {
    cancelar();
    setAcceso(false); setClave(''); setConsultas([]); setSeleccionada(null);
    setResultado(null); setError(''); setMensaje(''); setConfirmacion('');
  };

  return (
    <section className="tarjeta consultas-guardadas" aria-label={`Consultas guardadas ${motor}`}>
      <div className="tarjeta-encabezado">
        <div>
          <p className="etiqueta">Consultas personalizadas · {motor}</p>
          <h3>Agregar y guardar nuevas consultas</h3>
          <p>Las consultas se conservan en el servidor y se añaden a tu biblioteca, sin reemplazar las predefinidas.</p>
        </div>
        {acceso && <button type="button" className="boton boton-secundario" onClick={bloquear} disabled={ocupado}>Bloquear biblioteca</button>}
      </div>

      {!acceso ? (
        <form onSubmit={desbloquear} className="guardadas-acceso">
          <label className="campo campo-largo">
            <span>Clave de administración</span>
            <input type="password" value={clave} onChange={(event) => setClave(event.target.value)} required autoComplete="off" disabled={ocupado} />
          </label>
          <button type="submit" className="boton boton-primario" disabled={ocupado}>{ocupado ? 'Verificando…' : 'Abrir biblioteca'}</button>
        </form>
      ) : (
        <div className="guardadas-grid">
          {editando ? <form onSubmit={guardar} className="guardadas-form">
            <label className="campo"><span>Nombre de la nueva consulta</span>
              <input value={nombre} onChange={(event) => setNombre(event.target.value)} required maxLength={100} disabled={ocupado} />
            </label>
            <label className="campo"><span>Descripción (opcional)</span>
              <input value={descripcion} onChange={(event) => setDescripcion(event.target.value)} maxLength={500} disabled={ocupado} />
            </label>
            <label className="campo"><span>{motor === 'MONGODB' ? 'Código JSON de MongoDB' : 'Código Cypher'}</span>
              <textarea className="editor-consulta" value={codigo} onChange={(event) => setCodigo(event.target.value)} required maxLength={20000} rows={12} spellCheck={false} disabled={ocupado} />
            </label>
            <p className="ayuda">{motor === 'MONGODB'
              ? 'JSON válido. Operaciones: find, aggregate, insertMany, updateMany y deleteMany. Colecciones: evaluaciones, aspirantes y resumen_carrera. No se admite JavaScript de mongosh.'
              : 'Una sola sentencia Cypher de datos. Admite lecturas y CREATE/MERGE/SET/DELETE; no procedimientos, LOAD CSV ni administración del servidor.'}</p>
            <button type="submit" className="boton boton-primario" disabled={ocupado || !nombre.trim() || !codigo.trim()}>{ocupado ? 'Procesando…' : 'Guardar nueva consulta'}</button>
            <button type="button" className="boton boton-secundario" onClick={cancelar} disabled={ocupado}>Cancelar</button>
          </form> : (
            <div>
              <button type="button" className="boton boton-primario" onClick={() => setEditando(true)} disabled={ocupado}>Nueva consulta</button>
            </div>
          )}

          <div className="guardadas-biblioteca">
            <h4>Mis consultas guardadas ({consultas.length})</h4>
            {consultas.length === 0 && <p className="vacio-mini">Todavía no has guardado consultas para este motor.</p>}
            <div className="guardadas-lista">
              {consultas.map((consulta) => (
                <button key={consulta._id} type="button" disabled={ocupado}
                  className={`consulta-item ${seleccionada?._id === consulta._id ? 'activa' : ''}`}
                  onClick={() => { setSeleccionada(consulta); setResultado(null); setConfirmacion(''); setMensaje(''); setError(''); }}>
                  <span><strong>{consulta.nombre}</strong><small>{consulta.modo} · {consulta.descripcion || 'Sin descripción'}</small></span>
                </button>
              ))}
            </div>
            {seleccionada && (
              <div className="guardada-detalle">
                <h4>{seleccionada.nombre}</h4>
                <pre>{seleccionada.codigo}</pre>
                {seleccionada.modo === 'ESCRITURA' && (
                  <div className="mensaje-aviso">
                    <p>Esta consulta puede modificar o borrar datos. No hay deshacer automático. Las escrituras personalizadas no sincronizan MongoDB con Neo4j ni reconstruyen las vistas derivadas.</p>
                    <label className="campo"><span>Escribe EJECUTAR para confirmar esta escritura</span>
                      <input value={confirmacion} onChange={(event) => setConfirmacion(event.target.value)} autoComplete="off" disabled={ocupado} />
                    </label>
                  </div>
                )}
                <button type="button" className="boton boton-primario" onClick={ejecutar}
                  disabled={ocupado || (seleccionada.modo === 'ESCRITURA' && confirmacion !== 'EJECUTAR')}>
                  {ocupado ? 'Procesando…' : 'Ejecutar consulta guardada'}
                </button>
              </div>
            )}
          </div>
        </div>
      )}
      {error && <p className="mensaje-error" role="alert">{error}</p>}
      {mensaje && <p className="ayuda" role="status">{mensaje}</p>}
      {resultado && (
        <div className="guardada-resultado">
          <h4>Resultado · {resultado.duracionMs} ms</h4>
          {Object.keys(resultado.cambios || {}).length > 0 && <pre>{JSON.stringify(resultado.cambios, null, 2)}</pre>}
          {resultado.truncado && <p className="mensaje-aviso">Se muestran solo las primeras {resultado.limiteFilas} filas. Esto no limita cuántos datos puede modificar una escritura.</p>}
          <TablaResultados titulo={`guardada-${seleccionada?._id}`} filas={resultado.filas || []} />
        </div>
      )}
    </section>
  );
}
