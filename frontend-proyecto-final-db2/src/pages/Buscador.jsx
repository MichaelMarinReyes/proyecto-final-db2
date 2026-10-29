import { useState } from 'react';
import api from '../api/client';
import TablaResultados from '../components/TablaResultados';
import Cargando from '../components/Cargando';
import EstadoError from '../components/EstadoError';
import { formatearNumero } from '../utils/formato';
import FichaAspirante from '../components/FichaAspirante';

const CRITERIOS = [
  { value: 'aspirante', label: 'Aspirante (correlativo)' },
  { value: 'carrera', label: 'Carrera objetivo' },
  { value: 'materia', label: 'Materia' },
  { value: 'institucion', label: 'Institución educativa' },
  { value: 'municipio', label: 'Municipio' },
  { value: 'departamento', label: 'Departamento' }
];

export default function Buscador() {
  const [tipo, setTipo] = useState('aspirante');
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState([]);
  const [buscando, setBuscando] = useState(false);
  const [buscado, setBuscado] = useState(false);
  const [error, setError] = useState(null);
  const [ficha, setFicha] = useState(null);
  const [historial, setHistorial] = useState([]);
  const [cargandoFicha, setCargandoFicha] = useState(false);
  const [errorFicha, setErrorFicha] = useState(null);

  const abrirFicha = async (correlativo) => {
    setCargandoFicha(true);
    setErrorFicha(null);
    try {
      const [aspiranteRes, historialRes] = await Promise.all([
        api.get(`/aspirantes/${encodeURIComponent(correlativo)}`),
        api.get(`/aspirantes/${encodeURIComponent(correlativo)}/historial`)
      ]);
      setFicha(aspiranteRes.data);
      setHistorial(historialRes.data);
    } catch (consultaError) {
      setErrorFicha(consultaError.message);
      setFicha(null);
      setHistorial([]);
    } finally {
      setCargandoFicha(false);
    }
  };

  const buscar = async (event) => {
    event?.preventDefault();
    if (!texto.trim()) {
      setError('Ingresa un término de búsqueda.');
      return;
    }
    setBuscando(true);
    setError(null);
    setBuscado(true);
    setResultados([]);
    setFicha(null);
    setHistorial([]);
    try {
      const { data } = await api.get('/buscar', { params: { tipo, q: texto.trim() } });
      setResultados(data);
      if (tipo === 'aspirante' && data.length > 0) {
        await abrirFicha(data[0]._id);
      }
    } catch (consultaError) {
      setError(consultaError.message);
    } finally {
      setBuscando(false);
    }
  };

  return (
    <div className="pagina">
      <header className="pagina-encabezado">
        <div>
          <p className="eyebrow">Búsqueda</p>
          <h2>Encuentra aspirantes, carreras y territorios</h2>
          <p>La búsqueda usa índices y expresiones regulares sobre MongoDB. Para aspirantes se muestra la ficha completa con su historial.</p>
        </div>
      </header>

      <section className="tarjeta">
        <form className="buscador-form" onSubmit={buscar}>
          <label className="campo">
            <span>Criterio de búsqueda</span>
            <select value={tipo} onChange={(event) => setTipo(event.target.value)}>
              {CRITERIOS.map((criterio) => (
                <option key={criterio.value} value={criterio.value}>{criterio.label}</option>
              ))}
            </select>
          </label>
          <label className="campo campo-busqueda">
            <span>Término</span>
            <input
              type="search"
              value={texto}
              onChange={(event) => setTexto(event.target.value)}
              placeholder={tipo === 'aspirante' ? 'Ej. 0A1B2C3D o prefijo' : 'Escribe parte del nombre'}
            />
          </label>
          <button type="submit" className="boton boton-primario" disabled={buscando}>
            {buscando ? 'Buscando…' : 'Buscar'}
          </button>
        </form>
        <p className="ayuda">Consejo: para aspirantes puedes buscar por el correlativo exacto o por su prefijo.</p>
      </section>

      {buscando && <Cargando mensaje="Buscando coincidencias…" />}
      {error && <EstadoError mensaje={error} onReintentar={() => buscar()} titulo="No se pudo realizar la búsqueda" />}

      {buscado && !buscando && !error && resultados.length === 0 && (
        <section className="tarjeta">
          <p className="vacio-mini">No se encontraron resultados para “{texto}”.</p>
        </section>
      )}

      {resultados.length > 0 && tipo === 'aspirante' && (
        <section className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Coincidencias</p>
              <h3>Aspirantes encontrados</h3>
              <p>Selecciona un aspirante para abrir su ficha.</p>
            </div>
            <span className="chip">{formatearNumero(resultados.length)} resultados</span>
          </div>
          <div className="lista-resultados">
            {resultados.map((aspirante) => (
              <button
                key={aspirante._id}
                type="button"
                className={`resultado-item ${ficha?._id === aspirante._id ? 'activo' : ''}`}
                onClick={() => abrirFicha(aspirante._id)}
              >
                <div>
                  <strong>{aspirante._id}</strong>
                  <small>{aspirante.sexo || 'Sin sexo'} · {formatearNumero(aspirante.total_evaluaciones)} evaluaciones</small>
                </div>
                <span className="chip">{aspirante.carreras_objetivo?.[0] || 'Sin carrera'}</span>
              </button>
            ))}
          </div>
        </section>
      )}

      {resultados.length > 0 && tipo !== 'aspirante' && (
        <section className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Coincidencias</p>
              <h3>Resultados de la búsqueda</h3>
            </div>
            <span className="chip">{formatearNumero(resultados.length)} resultados</span>
          </div>
          <TablaResultados titulo={`busqueda-${tipo}`} filas={resultados} />
        </section>
      )}

      {cargandoFicha && <Cargando mensaje="Cargando ficha del aspirante…" />}
      {errorFicha && <EstadoError mensaje={errorFicha} onReintentar={() => ficha?._id && abrirFicha(ficha._id)} titulo="No se pudo cargar la ficha" />}

      {ficha && !cargandoFicha && (
        <section className="tarjeta">
          <FichaAspirante ficha={ficha} historial={historial} />
        </section>
      )}
    </div>
  );
}
