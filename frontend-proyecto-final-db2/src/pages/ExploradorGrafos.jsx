import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../api/client';
import GrafoCanvas from '../components/GrafoCanvas';
import TablaResultados from '../components/TablaResultados';
import Cargando from '../components/Cargando';
import EstadoError from '../components/EstadoError';
import FiltroBar, { SelectCampo } from '../components/FiltroBar';
import useCatalogos from '../hooks/useCatalogos';
import { colorPorTipo, etiquetaNodo, formatearNumero, formatearValor } from '../utils/formato';

const CONSULTAS_GRAFO = [
  {
    n: 1,
    titulo: 'Subgrafo por carrera',
    descripcion: 'Aspirantes que aspiran a una carrera y sus evaluaciones por materia.',
    tecnica: 'MATCH de 2 saltos + OPTIONAL MATCH',
    columnas: ['aspirante', 'carrera', 'materia', 'resultado', 'anio', 'oportunidad']
  },
  {
    n: 2,
    titulo: 'Carreras por departamento',
    descripcion: 'Carreras demandadas por aspirantes que provienen de un departamento.',
    tecnica: 'MATCH de 4 saltos + count(DISTINCT)',
    columnas: ['carrera', 'aspirantes']
  },
  {
    n: 3,
    titulo: 'Aprobados por materia',
    descripcion: 'Ranking de materias con más evaluaciones aprobadas en el grafo.',
    tecnica: 'Filtro por propiedad + agregación',
    columnas: ['materia', 'aprobados']
  },
  {
    n: 4,
    titulo: 'Aspirantes que comparten institución',
    descripcion: 'Parejas de aspirantes que provienen de la misma institución educativa.',
    tecnica: 'Patrón de nodo compartido',
    columnas: ['aspirante1', 'aspirante2', 'institucion', 'carrera']
  },
  {
    n: 5,
    titulo: 'Materias compartidas entre carreras',
    descripcion: 'Carreras que comparten materias evaluadas y cuántas tienen en común.',
    tecnica: 'Patrón bidireccional + collect(DISTINCT)',
    columnas: ['carrera1', 'carrera2', 'materias_compartidas', 'total']
  },
  {
    n: 6,
    titulo: 'Ruta departamento → carrera',
    descripcion: 'Agrupa la ruta completa departamento, municipio, institución y carrera.',
    tecnica: 'MATCH de 5 saltos + count',
    columnas: ['departamento', 'municipio', 'tipo_institucion', 'carrera', 'aspirantes']
  },
  {
    n: 7,
    titulo: 'Ruta más corta',
    descripcion: 'Calcula shortestPath entre un departamento y una carrera. Requiere ambos filtros.',
    tecnica: 'shortestPath + length',
    columnas: ['ruta', 'saltos'],
    requiereRuta: true
  }
];

const FILTROS_INICIALES = { carrera: '', depto: '', materia: '', tipo: '', anio: '', limit: '150' };

export default function ExploradorGrafos() {
  const { catalogos, cargando: cargandoCatalogos } = useCatalogos();
  const [filtros, setFiltros] = useState(FILTROS_INICIALES);
  const [aplicados, setAplicados] = useState(FILTROS_INICIALES);
  const [grafo, setGrafo] = useState({ nodes: [], links: [] });
  const [cargandoGrafo, setCargandoGrafo] = useState(true);
  const [errorGrafo, setErrorGrafo] = useState(null);
  const [nodoSeleccionado, setNodoSeleccionado] = useState(null);
  const [cargandoNodo, setCargandoNodo] = useState(false);
  const [pestana, setPestana] = useState('visualizacion');
  const [consulta, setConsulta] = useState(1);
  const [resultadoConsulta, setResultadoConsulta] = useState([]);
  const [cargandoConsulta, setCargandoConsulta] = useState(false);
  const [errorConsulta, setErrorConsulta] = useState(null);

  const cargarGrafo = useCallback(async (seleccion) => {
    setCargandoGrafo(true);
    setErrorGrafo(null);
    const params = {};
    Object.entries(seleccion).forEach(([clave, valor]) => {
      if (valor !== '' && valor !== null && valor !== undefined) {
        params[clave] = valor;
      }
    });
    try {
      const { data } = await api.get('/grafo/visualizacion', { params });
      setGrafo(data);
      setNodoSeleccionado(null);
    } catch (error) {
      setErrorGrafo(error.message);
      setGrafo({ nodes: [], links: [] });
    } finally {
      setCargandoGrafo(false);
    }
  }, []);

  useEffect(() => {
    cargarGrafo(aplicados);
  }, [aplicados, cargarGrafo]);

  const cambiarFiltro = (campo, valor) => setFiltros((actual) => ({ ...actual, [campo]: valor }));

  const aplicarFiltros = () => setAplicados({ ...filtros });

  const limpiarFiltros = () => {
    setFiltros(FILTROS_INICIALES);
    setAplicados(FILTROS_INICIALES);
  };

  const abrirNodo = async (nodo) => {
    setCargandoNodo(true);
    try {
      const { data } = await api.get(`/grafo/nodo/${encodeURIComponent(nodo.id)}`);
      setNodoSeleccionado(data);
    } catch (error) {
      setNodoSeleccionado({ error: error.message });
    } finally {
      setCargandoNodo(false);
    }
  };

  const ejecutarConsulta = async (numero = consulta) => {
    const definicion = CONSULTAS_GRAFO.find((item) => item.n === numero);
    if (definicion.requiereRuta && (!filtros.depto || !filtros.carrera)) {
      setErrorConsulta('La ruta más corta requiere seleccionar un departamento y una carrera.');
      return;
    }
    setCargandoConsulta(true);
    setErrorConsulta(null);
    const params = {};
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== '' && valor !== null && valor !== undefined) {
        params[clave] = valor;
      }
    });
    try {
      const { data } = await api.get(`/grafo/consultas/${numero}`, { params });
      setResultadoConsulta(data);
    } catch (error) {
      setErrorConsulta(error.message);
      setResultadoConsulta([]);
    } finally {
      setCargandoConsulta(false);
    }
  };

  const definicionConsulta = useMemo(() => CONSULTAS_GRAFO.find((item) => item.n === consulta), [consulta]);

  const tiposPresentes = useMemo(() => {
    const conjunto = new Set((grafo.nodes || []).map((nodo) => nodo.tipo));
    return Array.from(conjunto).sort();
  }, [grafo.nodes]);

  return (
    <div className="pagina pagina-ancha">
      <header className="pagina-encabezado">
        <div>
          <p className="eyebrow">Neo4j · Cypher</p>
          <h2>Explorador de relaciones</h2>
          <p>Filtra el grafo, recorre las conexiones y ejecuta consultas Cypher sobre aspirantes, carreras, materias e instituciones.</p>
        </div>
        <div className="acciones-encabezado">
          <a className="boton boton-secundario" href="http://localhost:7474" target="_blank" rel="noreferrer">Abrir Neo4j Browser</a>
          <button type="button" className="boton boton-primario" onClick={() => cargarGrafo(aplicados)} disabled={cargandoGrafo}>
            {cargandoGrafo ? 'Consultando…' : 'Actualizar grafo'}
          </button>
        </div>
      </header>

      <FiltroBar onLimpiar={limpiarFiltros} onAplicar={aplicarFiltros} aplicando={cargandoGrafo} textoAplicar="Explorar grafo">
        <SelectCampo etiqueta="Carrera" valor={filtros.carrera} onChange={(valor) => cambiarFiltro('carrera', valor)} opciones={catalogos.carreras} disabled={cargandoCatalogos} />
        <SelectCampo etiqueta="Departamento" valor={filtros.depto} onChange={(valor) => cambiarFiltro('depto', valor)} opciones={catalogos.departamentos} disabled={cargandoCatalogos} />
        <SelectCampo etiqueta="Materia" valor={filtros.materia} onChange={(valor) => cambiarFiltro('materia', valor)} opciones={catalogos.materias} disabled={cargandoCatalogos} />
        <SelectCampo etiqueta="Tipo de institución" valor={filtros.tipo} onChange={(valor) => cambiarFiltro('tipo', valor)} opciones={catalogos.tipos} disabled={cargandoCatalogos} />
        <SelectCampo etiqueta="Año" valor={filtros.anio} onChange={(valor) => cambiarFiltro('anio', valor)} opciones={catalogos.anios} disabled={cargandoCatalogos} />
        <label className="campo campo-corto">
          <span>Límite</span>
          <input type="number" min="1" max="500" value={filtros.limit} onChange={(event) => cambiarFiltro('limit', event.target.value)} />
        </label>
      </FiltroBar>

      <div className="tabs">
        <button type="button" className={pestana === 'visualizacion' ? 'activo' : ''} onClick={() => setPestana('visualizacion')}>Visualización interactiva</button>
        <button type="button" className={pestana === 'consultas' ? 'activo' : ''} onClick={() => setPestana('consultas')}>Consultas Cypher (G1–G7)</button>
      </div>

      {pestana === 'visualizacion' && (
        <div className="grafo-layout">
          <section className="tarjeta tarjeta-grafo">
            <div className="tarjeta-encabezado">
              <div>
                <p className="etiqueta">Grafo filtrado</p>
                <h3>{formatearNumero(grafo.nodes?.length || 0)} nodos · {formatearNumero(grafo.links?.length || 0)} relaciones</h3>
              </div>
              <div className="leyenda">
                {tiposPresentes.map((tipo) => (
                  <span key={tipo} className="leyenda-item">
                    <span className="punto-color" style={{ background: colorPorTipo(tipo) }} />
                    {tipo}
                  </span>
                ))}
              </div>
            </div>

            {cargandoGrafo ? (
              <Cargando mensaje="Construyendo el grafo…" />
            ) : errorGrafo ? (
              <EstadoError mensaje={errorGrafo} onReintentar={() => cargarGrafo(aplicados)} />
            ) : (
              <GrafoCanvas datos={grafo} onNodeClick={abrirNodo} altura={620} />
            )}
            <p className="ayuda">Haz clic en un nodo para consultar sus propiedades y vecinos. El tamaño del grafo se controla con el límite.</p>
          </section>

          <aside className="tarjeta panel-nodo">
            <div className="tarjeta-encabezado">
              <div>
                <p className="etiqueta">Detalle</p>
                <h3>Nodo seleccionado</h3>
              </div>
            </div>
            {cargandoNodo ? (
              <Cargando mensaje="Consultando nodo…" />
            ) : nodoSeleccionado?.error ? (
              <p className="mensaje-error">{nodoSeleccionado.error}</p>
            ) : !nodoSeleccionado ? (
              <div className="panel-vacio">
                <p>Selecciona un nodo del grafo para ver su información.</p>
                <small>Los colores identifican el tipo de nodo: aspirante, carrera, materia, institución, municipio, departamento o evaluación.</small>
              </div>
            ) : (
              <>
                <div className="nodo-cabecera">
                  <span className="punto-color" style={{ background: colorPorTipo(nodoSeleccionado.nodo?.tipo) }} />
                  <div>
                    <strong>{etiquetaNodo(nodoSeleccionado.nodo)}</strong>
                    <small>{nodoSeleccionado.nodo?.tipo}</small>
                  </div>
                </div>
                <h4>Propiedades</h4>
                <div className="propiedades">
                  {Object.entries(nodoSeleccionado.nodo?.propiedades || {}).map(([clave, valor]) => (
                    <div className="propiedad" key={clave}>
                      <span>{clave}</span>
                      <strong>{formatearValor(valor)}</strong>
                    </div>
                  ))}
                </div>
                <h4>Vecinos ({nodoSeleccionado.vecinos?.length || 0})</h4>
                <ul className="lista-vecinos">
                  {(nodoSeleccionado.vecinos || []).slice(0, 20).map((vecino) => (
                    <li key={vecino.id}>
                      <span className="punto-color" style={{ background: colorPorTipo(vecino.tipo) }} />
                      <span>{etiquetaNodo(vecino)}</span>
                      <small>{vecino.tipo}</small>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </aside>
        </div>
      )}

      {pestana === 'consultas' && (
        <div className="grafo-consultas">
          <aside className="lista-consultas">
            {CONSULTAS_GRAFO.map((item) => (
              <button
                key={item.n}
                type="button"
                className={`consulta-item ${item.n === consulta ? 'activa' : ''}`}
                onClick={() => {
                  setConsulta(item.n);
                  setResultadoConsulta([]);
                  setErrorConsulta(null);
                }}
              >
                <span className="consulta-numero">G{item.n}</span>
                <span>
                  <strong>{item.titulo}</strong>
                  <small>{item.tecnica}</small>
                </span>
              </button>
            ))}
          </aside>

          <section className="tarjeta">
            <div className="tarjeta-encabezado">
              <div>
                <p className="etiqueta">Consulta G{definicionConsulta.n}</p>
                <h3>{definicionConsulta.titulo}</h3>
                <p>{definicionConsulta.descripcion}</p>
              </div>
              <button type="button" className="boton boton-primario" onClick={() => ejecutarConsulta()} disabled={cargandoConsulta}>
                {cargandoConsulta ? 'Ejecutando…' : 'Ejecutar consulta'}
              </button>
            </div>

            {definicionConsulta.requiereRuta && (!filtros.depto || !filtros.carrera) && (
              <p className="mensaje-aviso">Selecciona un departamento y una carrera en los filtros superiores para calcular la ruta más corta.</p>
            )}

            {cargandoConsulta ? (
              <Cargando mensaje="Ejecutando Cypher en Neo4j…" />
            ) : errorConsulta ? (
              <EstadoError mensaje={errorConsulta} onReintentar={() => ejecutarConsulta()} titulo="La consulta Cypher no pudo ejecutarse" />
            ) : resultadoConsulta.length === 0 ? (
              <p className="vacio-mini">Ejecuta la consulta para ver sus resultados.</p>
            ) : (
              <TablaResultados titulo={`cypher-g${consulta}`} filas={resultadoConsulta} columnas={definicionConsulta.columnas} />
            )}
          </section>
        </div>
      )}
    </div>
  );
}
