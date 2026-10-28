import { useMemo, useState } from 'react';
import { descargarCsv, formatearFecha, formatearFechaCorta, formatearNumero, formatearValor, tituloColumna } from '../utils/formato';

const TAMANOS = [10, 25, 50];

function valorCelda(columna, valor) {
  if (valor === null || valor === undefined) {
    return '—';
  }
  const nombre = columna.toLowerCase();
  if (nombre.endsWith('ms') && Number.isFinite(Number(valor))) {
    return `${formatearNumero(valor)} ms`;
  }
  if (nombre === 'fecha_asignacion') {
    return formatearFechaCorta(valor);
  }
  if (nombre.startsWith('fecha') || nombre === 'generado_en') {
    return formatearFecha(valor);
  }
  if (nombre.includes('porcentaje') || nombre.includes('tasa')) {
    return `${formatearValor(valor)}%`;
  }
  if (columna === 'estado') {
    return <span className={`badge estado-${String(valor).toLowerCase()}`}>{String(valor).replaceAll('_', ' ')}</span>;
  }
  if (Array.isArray(valor)) {
    return valor.slice(0, 4).join(', ') + (valor.length > 4 ? ` (+${valor.length - 4})` : '');
  }
  if (typeof valor === 'object') {
    return <code className="celda-json">{JSON.stringify(valor)}</code>;
  }
  return formatearValor(valor);
}

export default function TablaResultados({ filas = [], columnas, titulo, exportar = true, paginada = true, maximo }) {
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(10);
  const [orden, setOrden] = useState({ campo: null, direccion: 'asc' });

  const columnasEfectivas = useMemo(() => {
    if (columnas && columnas.length > 0) {
      return columnas;
    }
    const conjunto = new Set();
    filas.slice(0, 5).forEach((fila) => Object.keys(fila).forEach((clave) => conjunto.add(clave)));
    return Array.from(conjunto);
  }, [columnas, filas]);

  const filasOrdenadas = useMemo(() => {
    if (!orden.campo) {
      return filas;
    }
    return [...filas].sort((a, b) => {
      const valorA = a[orden.campo];
      const valorB = b[orden.campo];
      if (typeof valorA === 'number' && typeof valorB === 'number') {
        return orden.direccion === 'asc' ? valorA - valorB : valorB - valorA;
      }
      return orden.direccion === 'asc'
        ? String(valorA ?? '').localeCompare(String(valorB ?? ''), 'es')
        : String(valorB ?? '').localeCompare(String(valorA ?? ''), 'es');
    });
  }, [filas, orden]);

  const filasVisibles = useMemo(() => {
    const base = maximo ? filasOrdenadas.slice(0, maximo) : filasOrdenadas;
    if (!paginada) {
      return base;
    }
    return base.slice(pagina * tamano, pagina * tamano + tamano);
  }, [filasOrdenadas, maximo, pagina, paginada, tamano]);

  const totalPaginas = Math.max(1, Math.ceil((maximo ? Math.min(filasOrdenadas.length, maximo) : filasOrdenadas.length) / tamano));

  if (!filas || filas.length === 0) {
    return <p className="vacio-mini">No hay resultados para mostrar</p>;
  }

  const cambiarOrden = (campo) => {
    setOrden((actual) => ({
      campo,
      direccion: actual.campo === campo && actual.direccion === 'asc' ? 'desc' : 'asc'
    }));
    setPagina(0);
  };

  return (
    <div className="tabla-wrapper">
      <div className="tabla-barra">
        <span className="tabla-conteo">{formatearNumero(filas.length)} registros</span>
        <div className="tabla-acciones">
          {paginada && (
            <label className="tamano-pagina">
              Filas
              <select
                value={tamano}
                onChange={(event) => {
                  setTamano(Number(event.target.value));
                  setPagina(0);
                }}
              >
                {TAMANOS.map((valor) => <option key={valor} value={valor}>{valor}</option>)}
              </select>
            </label>
          )}
          {exportar && (
            <button
              type="button"
              className="boton boton-secundario boton-pequeno"
              onClick={() => descargarCsv(`${titulo || 'resultados'}.csv`, columnasEfectivas, filasOrdenadas)}
            >
              Exportar CSV
            </button>
          )}
        </div>
      </div>

      <div className="tabla-scroll">
        <table>
          <thead>
            <tr>
              {columnasEfectivas.map((columna) => (
                <th key={columna}>
                  <button type="button" className="orden-boton" onClick={() => cambiarOrden(columna)}>
                    {tituloColumna(columna)}
                    {orden.campo === columna && <span>{orden.direccion === 'asc' ? ' ↑' : ' ↓'}</span>}
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filasVisibles.map((fila, indice) => (
              <tr key={indice}>
                {columnasEfectivas.map((columna) => (
                  <td key={columna}>{valorCelda(columna, fila[columna])}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {paginada && totalPaginas > 1 && (
        <div className="paginacion">
          <button type="button" className="boton boton-secundario boton-pequeno" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>
            Anterior
          </button>
          <span>Página {pagina + 1} de {totalPaginas}</span>
          <button type="button" className="boton boton-secundario boton-pequeno" disabled={pagina >= totalPaginas - 1} onClick={() => setPagina((p) => p + 1)}>
            Siguiente
          </button>
        </div>
      )}
    </div>
  );
}
