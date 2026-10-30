import { useEffect, useState } from 'react';
import api from '../api/client';
import TablaResultados from './TablaResultados';
import { formatearNumero } from '../utils/formato';

const ENDPOINTS = {
  MONGODB: '/consultas/mongo/ejecutar',
  NEO4J: '/grafo/ejecutar'
};

export default function ConsultaEjecutada({ motor = 'MONGODB', consulta }) {
  const original = consulta?.codigo || '';
  const [codigo, setCodigo] = useState(original);
  const [abierto, setAbierto] = useState(true);
  const [ejecutando, setEjecutando] = useState(false);
  const [error, setError] = useState(null);
  const [resultado, setResultado] = useState(null);

  useEffect(() => {
    setCodigo(original);
    setResultado(null);
    setError(null);
    setAbierto(true);
  }, [original]);

  if (!consulta?.codigo) {
    return null;
  }

  const modificado = codigo !== original;
  const lineas = codigo ? codigo.split('\n').length : 0;

  const ejecutar = async () => {
    if (!codigo.trim()) {
      setError('La consulta no puede estar vacía.');
      return;
    }
    if (motor === 'MONGODB') {
      try {
        JSON.parse(codigo);
      } catch {
        setError('El código MongoDB debe ser un objeto JSON válido (operacion, coleccion y filtro o pipeline).');
        return;
      }
    }
    setEjecutando(true);
    setError(null);
    try {
      const { data } = await api.post(ENDPOINTS[motor], {
        codigo,
        parametros: consulta.parametros || null
      });
      setResultado(data);
    } catch (consultaError) {
      setError(consultaError.message);
      setResultado(null);
    } finally {
      setEjecutando(false);
    }
  };

  const restaurar = () => {
    setCodigo(original);
    setResultado(null);
    setError(null);
  };

  return (
    <section className="consulta-ejecutada">
      <div className="consulta-ejecutada-encabezado">
        <div>
          <p className="etiqueta">{motor === 'NEO4J' ? 'Cypher' : 'MongoDB'} · consulta ejecutada</p>
          <h4>Consulta que generó este resultado</h4>
          <p>
            Puedes editarla y ejecutarla de nuevo. El servidor solo permite consultas de lectura y
            limita la cantidad de filas devueltas.
          </p>
        </div>
        <button type="button" className="boton boton-secundario boton-pequeno" onClick={() => setAbierto((valor) => !valor)}>
          {abierto ? 'Ocultar consulta' : 'Mostrar consulta'}
        </button>
      </div>

      {consulta.nota && <p className="mensaje-aviso">{consulta.nota}</p>}

      {abierto && (
        <div className="consulta-ejecutada-cuerpo">
          <label className="consulta-editor">
            <span>{motor === 'NEO4J' ? 'Sentencia Cypher' : 'Comando JSON de MongoDB'}</span>
            <textarea
              value={codigo}
              onChange={(event) => setCodigo(event.target.value)}
              spellCheck="false"
              wrap="off"
              rows={Math.min(Math.max(lineas, 8), 22)}
              aria-label="Consulta ejecutada editable"
            />
          </label>

          {consulta.parametros && Object.keys(consulta.parametros).length > 0 && (
            <details className="consulta-parametros">
              <summary>Parámetros usados: {Object.keys(consulta.parametros).length}</summary>
              <pre>{JSON.stringify(consulta.parametros, null, 2)}</pre>
            </details>
          )}

          <div className="consulta-editor-acciones">
            <button type="button" className="boton boton-primario" onClick={ejecutar} disabled={ejecutando}>
              {ejecutando ? 'Ejecutando…' : 'Ejecutar consulta editada'}
            </button>
            <button type="button" className="boton boton-secundario" onClick={restaurar} disabled={!modificado || ejecutando}>
              Restaurar original
            </button>
            <span className="ayuda">
              {modificado ? 'La consulta tiene cambios sin ejecutar.' : 'Esta es la consulta original.'}
            </span>
          </div>

          {error && <p className="mensaje-error">{error}</p>}

          {resultado && (
            <div className="consulta-editor-resultado">
              <div className="consulta-editor-resumen">
                <strong>Resultado de la consulta editada</strong>
                <span>
                  {formatearNumero(resultado.filas?.length || 0)} filas
                  {resultado.truncado ? ` (limitado a ${formatearNumero(resultado.limiteFilas)} por seguridad)` : ''}
                  {Number.isFinite(resultado.duracionMs) ? ` · ${formatearNumero(resultado.duracionMs)} ms` : ''}
                </span>
              </div>
              <TablaResultados titulo="consulta-editada" filas={resultado.filas || []} />
            </div>
          )}
        </div>
      )}
    </section>
  );
}
