import { useMemo, useState } from 'react';
import Grafica from '../components/Grafica';
import TablaResultados from '../components/TablaResultados';
import Cargando from '../components/Cargando';
import EstadoError from '../components/EstadoError';
import FiltroBar, { SelectCampo } from '../components/FiltroBar';
import useConsultas from '../hooks/useConsultas';
import useCatalogos from '../hooks/useCatalogos';
import ConsultasGuardadas from '../components/ConsultasGuardadas';
import ConsultaEjecutada from '../components/ConsultaEjecutada';
import FichaAspirante from '../components/FichaAspirante';
import { formatearNumero } from '../utils/formato';

const CONSULTAS = [
  {
    n: 1,
    titulo: 'Aspirantes por tipo de institución',
    descripcion: 'Cuenta aspirantes únicos agrupados por tipo de institución educativa. Usa dos etapas de $group para no duplicar a quienes tienen varias evaluaciones.',
    tecnica: '$group · $sum',
    columnas: ['tipo', 'aspirantes'],
    grafico: { tipo: 'bar', xKey: 'tipo', yKey: 'aspirantes', nombreY: 'Aspirantes' }
  },
  {
    n: 2,
    titulo: 'Aprobados por materia',
    descripcion: 'Filtra las evaluaciones aprobadas y cuenta cuántas hay por materia.',
    tecnica: '$match · $group',
    columnas: ['materia', 'aprobados'],
    grafico: { tipo: 'bar', xKey: 'materia', yKey: 'aprobados', nombreY: 'Aprobados' }
  },
  {
    n: 3,
    titulo: 'Aprobados por carrera y año',
    descripcion: 'Cantidad de aspirantes únicos aprobados, agrupada por carrera objetivo y año de ingreso.',
    tecnica: '$group compuesto',
    columnas: ['carrera', 'anio', 'aprobados']
  },
  {
    n: 4,
    titulo: 'Porcentaje de aprobación por materia',
    descripcion: 'Calcula la tasa de aprobación por materia con $cond, $divide y $round.',
    tecnica: '$cond · $divide',
    columnas: ['materia', 'total', 'aprobados', 'porcentaje'],
    grafico: { tipo: 'bar', xKey: 'materia', yKey: 'porcentaje', nombreY: '% aprobación', unidad: '%' }
  },
  {
    n: 5,
    titulo: 'Edad promedio por carrera',
    descripcion: 'Promedio de edad de ingreso por carrera objetivo, contando una vez a cada aspirante.',
    tecnica: '$avg · $round',
    columnas: ['carrera', 'edad_promedio', 'muestras'],
    grafico: { tipo: 'bar', xKey: 'carrera', yKey: 'edad_promedio', nombreY: 'Edad promedio' }
  },
  {
    n: 6,
    titulo: 'Resumen por carrera ($merge)',
    descripcion: 'Genera la colección auxiliar resumen_carrera con $merge y luego la consulta. Incluye totales, aprobados, reprobados, tasa y edad promedio.',
    tecnica: '$merge · $out auxiliar',
    columnas: ['carrera', 'total_evaluaciones', 'total_aspirantes', 'aprobados', 'reprobados', 'tasa_aprobacion', 'promedio_edad'],
    esResumen: true
  },
  {
    n: 7,
    titulo: 'Edad de aprobados por carrera y tipo',
    descripcion: 'Cruza la edad promedio de los aprobados según carrera objetivo y tipo de institución.',
    tecnica: '$group compuesto · $avg',
    columnas: ['carrera', 'tipo', 'edad_promedio', 'muestras']
  },
  {
    n: 8,
    titulo: 'Aprobados por municipio y carrera',
    descripcion: 'Distribución de aspirantes únicos aprobados por municipio de la institución y carrera objetivo.',
    tecnica: '$group + $limit',
    columnas: ['municipio', 'departamento', 'carrera', 'aprobados']
  },
  {
    n: 9,
    titulo: 'Evaluaciones por mes y materia',
    descripcion: 'Evaluaciones de instituciones públicas agrupadas por mes de asignación y materia. Usa $month sobre la fecha.',
    tecnica: '$month · $group',
    columnas: ['mes', 'materia', 'total']
  },
  {
    n: 10,
    titulo: 'Top 5 carreras (16 a 18 años)',
    descripcion: 'Carreras más demandadas por aspirantes de 16 a 18 años, contando aspirantes únicos.',
    tecnica: '$addToSet · $size',
    columnas: ['carrera', 'total_aspirantes'],
    grafico: { tipo: 'pie', xKey: 'carrera', yKey: 'total_aspirantes' }
  },
  {
    n: 11,
    titulo: 'Historial de un aspirante',
    descripcion: 'Devuelve todas las evaluaciones de un correlativo, ordenadas por año, oportunidad y fecha.',
    tecnica: '$find + $sort',
    columnas: ['fecha_asignacion', 'materia', 'oportunidad', 'anio_ingreso', 'aprobacion', 'carrera_objetivo'],
    requiereCorrelativo: true
  },
  {
    n: 12,
    titulo: 'Distribución por sexo y tipo',
    descripcion: 'Cantidad de aspirantes por sexo y tipo de institución educativa.',
    tecnica: '$group compuesto',
    columnas: ['sexo', 'tipo', 'aspirantes']
  },
  {
    n: 13,
    titulo: 'Tasa de aprobación por edad',
    descripcion: 'Muestra cuántas evaluaciones hubo por edad y qué porcentaje resultó aprobado.',
    tecnica: '$group · $divide',
    columnas: ['edad', 'total', 'aprobados', 'porcentaje'],
    grafico: { tipo: 'line', xKey: 'edad', series: [{ key: 'porcentaje', nombre: '% aprobación', color: '#4f46e5' }], unidad: '%' }
  },
  {
    n: 14,
    titulo: 'Intentos promedio por materia',
    descripcion: 'Cuenta oportunidades distintas por año y aspirante para cada materia y promedia los intentos registrados. No infiere intentos faltantes a partir de su número.',
    tecnica: '$group · $sum · $avg',
    columnas: ['materia', 'intentos_promedio', 'aspirantes'],
    grafico: { tipo: 'bar', xKey: 'materia', yKey: 'intentos_promedio', nombreY: 'Intentos promedio' }
  },
  {
    n: 15,
    titulo: 'Ficha completa del aspirante',
    descripcion: 'Documento embebido de la colección aspirantes, con su historial completo de evaluaciones.',
    tecnica: 'Modelo embebido · $find',
    columnas: ['_id', 'sexo', 'anio_nacimiento', 'total_evaluaciones', 'materias', 'carreras_objetivo'],
    requiereCorrelativo: true
  },
  {
    n: 16,
    titulo: 'Reprobados en primer intento por carrera',
    descripcion: 'Carreras con más aspirantes reprobados en su primera oportunidad.',
    tecnica: '$match · $addToSet',
    columnas: ['carrera', 'total_reprobados'],
    grafico: { tipo: 'bar', xKey: 'carrera', yKey: 'total_reprobados', nombreY: 'Reprobados' }
  },
  {
    n: 17,
    titulo: 'Top municipios por aspirantes',
    descripcion: 'Cinco municipios con mayor cantidad de aspirantes únicos.',
    tecnica: '$addToSet · $size',
    columnas: ['municipio', 'departamento', 'total_aspirantes'],
    grafico: { tipo: 'bar', xKey: 'municipio', yKey: 'total_aspirantes', nombreY: 'Aspirantes' }
  },
  {
    n: 18,
    titulo: 'Aprobación por tipo de institución',
    descripcion: 'Compara la tasa de aprobación entre instituciones públicas y privadas.',
    tecnica: '$cond · $round',
    columnas: ['tipo', 'total', 'aprobados', 'porcentaje'],
    grafico: { tipo: 'bar', xKey: 'tipo', yKey: 'porcentaje', nombreY: '% aprobación', unidad: '%' }
  },
  {
    n: 19,
    titulo: 'Top 3 carreras por departamento',
    descripcion: 'Usa $setWindowFields para rankear las carreras más demandadas dentro de cada departamento.',
    tecnica: '$setWindowFields · $rank',
    columnas: ['departamento', 'carrera', 'demanda', 'rank']
  },
  {
    n: 20,
    titulo: 'Evolución anual de aprobación',
    descripcion: 'Aprobados, reprobados y porcentaje de aprobación por año de ingreso.',
    tecnica: '$group · $cond',
    columnas: ['anio', 'total', 'aprobados', 'no_aprobados', 'porcentaje_aprobacion', 'porcentaje_no_aprobacion'],
    grafico: {
      tipo: 'line',
      xKey: 'anio',
      series: [
        { key: 'porcentaje_aprobacion', nombre: '% aprobación', color: '#4f46e5' },
        { key: 'porcentaje_no_aprobacion', nombre: '% no aprobación', color: '#dc2626' }
      ],
      unidad: '%'
    }
  }
];

export default function Consultas() {
  const [numero, setNumero] = useState(1);
  const [correlativo, setCorrelativo] = useState('');
  const [filtros, setFiltros] = useState({ anio: '', materia: '', carrera: '', depto: '', tipo: '', limit: '' });
  const [vista, setVista] = useState('grafico');
  const { catalogos, cargando: cargandoCatalogos } = useCatalogos();

  const definicion = CONSULTAS.find((consulta) => consulta.n === numero);
  const requiereCorrelativo = Boolean(definicion?.requiereCorrelativo);

  const filtrosConsulta = useMemo(() => {
    if (requiereCorrelativo) {
      return { correlativo };
    }
    if (numero === 6) {
      return {};
    }
    return filtros;
  }, [filtros, numero, requiereCorrelativo, correlativo]);

  const numeroActivo = requiereCorrelativo && !correlativo.trim() ? null : numero;
  const { datos, consulta, cargando, error, recargar, generarResumen, generando } = useConsultas(numeroActivo, filtrosConsulta);

  const cambiarFiltro = (campo, valor) => {
    setFiltros((actual) => ({ ...actual, [campo]: valor }));
  };

  const limpiar = () => {
    setFiltros({ anio: '', materia: '', carrera: '', depto: '', tipo: '', limit: '' });
    setCorrelativo('');
  };

  const tieneGrafico = Boolean(definicion?.grafico);
  const vistaEfectiva = tieneGrafico ? vista : 'tabla';
  const totalRegistros = Array.isArray(datos) ? datos.length : 0;

  return (
    <div className="pagina pagina-ancha">
      <header className="pagina-encabezado">
        <div>
          <p className="eyebrow">20 agregaciones</p>
          <h2>Consultas analíticas en MongoDB</h2>
          <p>Selecciona una consulta, aplica filtros y revisa el resultado como gráfica o tabla.</p>
        </div>
        <button type="button" className="boton boton-secundario" onClick={recargar} disabled={cargando}>
          {cargando ? 'Ejecutando…' : 'Ejecutar de nuevo'}
        </button>
      </header>

      <ConsultasGuardadas motor="MONGODB" />

      <div className="consultas-layout">
        <aside className="lista-consultas">
          {CONSULTAS.map((consulta) => (
            <button
              key={consulta.n}
              type="button"
              className={`consulta-item ${consulta.n === numero ? 'activa' : ''}`}
              onClick={() => {
                setNumero(consulta.n);
                setVista(consulta.grafico ? 'grafico' : 'tabla');
              }}
            >
              <span className="consulta-numero">{String(consulta.n).padStart(2, '0')}</span>
              <span>
                <strong>{consulta.titulo}</strong>
                <small>{consulta.tecnica}</small>
              </span>
            </button>
          ))}
        </aside>

        <section className="tarjeta consulta-detalle">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Consulta {definicion.n}</p>
              <h3>{definicion.titulo}</h3>
              <p>{definicion.descripcion}</p>
            </div>
            <div className="consulta-meta">
              <span className="nota-tecnica">{definicion.tecnica}</span>
              <span className="chip">{formatearNumero(totalRegistros)} filas</span>
            </div>
          </div>

          {!requiereCorrelativo && numero !== 6 && (
            <FiltroBar onLimpiar={limpiar}>
              <SelectCampo etiqueta="Año" valor={filtros.anio} onChange={(valor) => cambiarFiltro('anio', valor)} opciones={catalogos.anios} placeholder="Todos" disabled={cargandoCatalogos} />
              <SelectCampo etiqueta="Materia" valor={filtros.materia} onChange={(valor) => cambiarFiltro('materia', valor)} opciones={catalogos.materias} disabled={cargandoCatalogos} />
              <SelectCampo etiqueta="Carrera" valor={filtros.carrera} onChange={(valor) => cambiarFiltro('carrera', valor)} opciones={catalogos.carreras} disabled={cargandoCatalogos} />
              <SelectCampo etiqueta="Departamento" valor={filtros.depto} onChange={(valor) => cambiarFiltro('depto', valor)} opciones={catalogos.departamentos} disabled={cargandoCatalogos} />
              <SelectCampo etiqueta="Tipo de institución" valor={filtros.tipo} onChange={(valor) => cambiarFiltro('tipo', valor)} opciones={catalogos.tipos} disabled={cargandoCatalogos} />
              <label className="campo campo-corto">
                <span>Límite</span>
                <input
                  type="number"
                  min="1"
                  max="1000"
                  value={filtros.limit}
                  placeholder="Por defecto"
                  onChange={(event) => cambiarFiltro('limit', event.target.value)}
                />
              </label>
            </FiltroBar>
          )}

          {requiereCorrelativo && (
            <FiltroBar>
              <label className="campo campo-largo">
                <span>Correlativo del aspirante</span>
                <input
                  type="text"
                  value={correlativo}
                  placeholder="Ej. 0A1B2C3D..."
                  onChange={(event) => setCorrelativo(event.target.value)}
                />
              </label>
            </FiltroBar>
          )}

          {numero === 6 && (
            <div className="aviso-info">
              <div>
                <strong>Colección auxiliar resumen_carrera</strong>
                <p>El endpoint ejecuta la agregación con <code>$merge</code> y devuelve el resumen actualizado. Puedes regenerarlo cuando cargues nuevos datos.</p>
              </div>
              <button type="button" className="boton boton-primario" onClick={generarResumen} disabled={generando}>
                {generando ? 'Generando…' : 'Regenerar resumen'}
              </button>
            </div>
          )}

          <div className="consulta-barra">
            <span>Resultados</span>
            {tieneGrafico && (
              <div className="segmentado">
                <button type="button" className={vistaEfectiva === 'grafico' ? 'activo' : ''} onClick={() => setVista('grafico')}>Gráfica</button>
                <button type="button" className={vistaEfectiva === 'tabla' ? 'activo' : ''} onClick={() => setVista('tabla')}>Tabla</button>
              </div>
            )}
          </div>

          {requiereCorrelativo && !correlativo.trim() ? (
            <p className="vacio-mini">Ingresa un correlativo para ejecutar la consulta.</p>
          ) : cargando ? (
            <Cargando mensaje={`Ejecutando la consulta ${numero}…`} />
          ) : error ? (
            <EstadoError mensaje={error} onReintentar={recargar} titulo="La consulta no pudo ejecutarse" />
          ) : datos.length === 0 ? (
            <p className="vacio-mini">La consulta no devolvió resultados con los filtros seleccionados.</p>
          ) : numero === 15 ? (
            <FichaAspirante ficha={datos[0]} />
          ) : vistaEfectiva === 'grafico' && definicion.grafico ? (
            <Grafica
              tipo={definicion.grafico.tipo}
              datos={definicion.grafico.tipo === 'bar' ? datos.slice(0, 20) : datos}
              xKey={definicion.grafico.xKey}
              yKey={definicion.grafico.yKey}
              series={definicion.grafico.series}
              unidad={definicion.grafico.unidad}
              nombreY={definicion.grafico.nombreY}
              altura={390}
            />
          ) : (
            <TablaResultados
              titulo={`consulta-${numero}`}
              filas={datos}
              columnas={definicion.columnas}
            />
          )}

          {vistaEfectiva === 'grafico' && tieneGrafico && datos.length > 20 && (
            <p className="ayuda">La gráfica muestra los primeros 20 registros; cambia a la vista de tabla para ver todos.</p>
          )}

          {!cargando && !error && consulta && (
            <ConsultaEjecutada
              key={`${numeroActivo}-${JSON.stringify(filtrosConsulta)}`}
              motor="MONGODB"
              consulta={consulta}
            />
          )}
        </section>
      </div>
    </div>
  );
}
