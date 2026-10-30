import { useEffect, useState } from 'react';
import api from '../api/client';
import TablaResultados from './TablaResultados';
import { formatearNumero } from '../utils/formato';

const ENDPOINTS = {
  MONGODB: '/consultas/mongo/ejecutar',
  NEO4J: '/grafo/ejecutar'
};

function operacionMongo(codigo) {
  try {
    const comando = JSON.parse(codigo);
    return comando?.operacion || null;
  } catch {
    return null;
  }
}

function describirConsulta(motor, codigo) {
  if (motor === 'NEO4J') {
    return {
      motor: 'Neo4j · Sentencia Cypher',
      titulo: 'Sentencia Cypher que generó este resultado',
      editor: 'Sentencia Cypher'
    };
  }
  const operacion = operacionMongo(codigo);
  if (operacion === 'aggregate') {
    return {
      motor: 'MongoDB · Aggregation pipeline',
      titulo: 'Aggregation pipeline que generó este resultado',
      editor: 'Comando JSON · Aggregation pipeline'
    };
  }
  if (operacion === 'find') {
    return {
      motor: 'MongoDB · Consulta documental',
      titulo: 'Consulta documental que generó este resultado',
      editor: 'Comando JSON · Consulta documental'
    };
  }
  return {
    motor: 'MongoDB · Comando JSON',
    titulo: 'Consulta de MongoDB que generó este resultado',
    editor: 'Comando JSON de MongoDB'
  };
}

export default function ConsultaEjecutada({ motor = 'MONGODB', consulta }) {
  const original = consulta?.codigo || '';
  const defaultCypher = 'MATCH (n) RETURN n LIMIT 25';
  const defaultMongo = JSON.stringify({ operacion: 'aggregate', coleccion: 'evaluaciones', pipeline: [] }, null, 2);

  const [codigoMongo, setCodigoMongo] = useState(
    motor === 'MONGODB' ? original : (consulta?.mongoQuery || defaultMongo)
  );
  const [codigoCypher, setCodigoCypher] = useState(
    motor === 'NEO4J' ? original : (consulta?.cypherQuery || defaultCypher)
  );
  const [abierto, setAbierto] = useState(true);
  const [ejecutando, setEjecutando] = useState(false);
  const [error, setError] = useState(null);
  const [resultado, setResultado] = useState(null);
  const [tabActiva, setTabActiva] = useState(motor === 'MONGODB' ? 'mongo' : 'cypher');

  useEffect(() => {
    const isMongo = motor === 'MONGODB';
    setCodigoMongo(isMongo ? original : (consulta?.mongoQuery || defaultMongo));
    setCodigoCypher(!isMongo ? original : (consulta?.cypherQuery || defaultCypher));
    setResultado(null);
    setError(null);
    setAbierto(true);
    setTabActiva(isMongo ? 'mongo' : 'cypher');
  }, [original, motor, consulta?.mongoQuery, consulta?.cypherQuery]);

  if (!consulta?.codigo) {
    return null;
  }

  const codigoActual = tabActiva === 'mongo' ? codigoMongo : codigoCypher;
  const modificado = tabActiva === 'mongo'
    ? codigoMongo !== (motor === 'MONGODB' ? original : (consulta?.mongoQuery || defaultMongo))
    : codigoCypher !== (motor === 'NEO4J' ? original : (consulta?.cypherQuery || defaultCypher));
  const lineas = codigoActual ? codigoActual.split('\n').length : 0;
  const descOriginal = describirConsulta(motor, original);
  const descActual = describirConsulta(motor, codigoActual);

  const pestañas = motor === 'MONGODB'
    ? [
        { id: 'mongo', label: 'Comando JSON (MongoDB)' }
      ]
    : [
        { id: 'cypher', label: 'Sentencia Cypher (Neo4j)' }
      ];

  const idPrincipal = motor === 'MONGODB' ? 'mongo' : 'cypher';
  const codigoEquivalente = motor === 'MONGODB' ? consulta?.cypherQuery : consulta?.mongoQuery;

  const ejecutar = async () => {
    const esMongo = tabActiva === 'mongo';
    const codigoAEnviar = esMongo ? codigoMongo : codigoCypher;
    if (!codigoAEnviar.trim()) {
      setError('La consulta no puede estar vacía.');
      return;
    }
    if (esMongo) {
      try {
        JSON.parse(codigoAEnviar);
      } catch {
        setError('El comando JSON de MongoDB debe ser un objeto válido (operacion, coleccion y filtro o pipeline).');
        return;
      }
    }
    setEjecutando(true);
    setError(null);
    try {
      const endpoint = esMongo ? ENDPOINTS.MONGODB : ENDPOINTS.NEO4J;
      const body = esMongo
        ? { codigo: codigoAEnviar, parametros: null }
        : { codigo: codigoAEnviar, parametros: consulta.parametros || null };
      const { data } = await api.post(endpoint, body);
      setResultado(data);
    } catch (consultaError) {
      setError(consultaError.message);
      setResultado(null);
    } finally {
      setEjecutando(false);
    }
  };

  const restaurar = () => {
    const isMongo = motor === 'MONGODB';
    if (tabActiva === 'mongo') {
      setCodigoMongo(isMongo ? original : (consulta?.mongoQuery || defaultMongo));
    } else {
      setCodigoCypher(!isMongo ? original : (consulta?.cypherQuery || defaultCypher));
    }
    setResultado(null);
    setError(null);
  };

  return (
    <section className="consulta-ejecutada">
      <div className="consulta-ejecutada-encabezado">
        <div>
          <p className="etiqueta">{descOriginal.motor} · consulta ejecutada</p>
          <h4>{descOriginal.titulo}</h4>
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
          {pestañas.length > 1 && (
            <div className="consulta-editor-tabs">
              {pestañas.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  className={`boton-tab ${tabActiva === p.id ? 'activo' : ''}`}
                  onClick={() => setTabActiva(p.id)}
                >
                  {p.label}
                </button>
              ))}
            </div>
          )}
          <label className="consulta-editor">
            <span>
              {tabActiva === 'mongo'
                ? 'Comando JSON · MongoDB'
                : 'Sentencia Cypher · Neo4j'}
            </span>
            <textarea
              value={codigoActual}
              onChange={(event) => {
                if (tabActiva === 'mongo') setCodigoMongo(event.target.value);
                else setCodigoCypher(event.target.value);
              }}
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
