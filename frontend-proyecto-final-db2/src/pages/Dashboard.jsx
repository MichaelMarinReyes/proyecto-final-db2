import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import KpiCard from '../components/KpiCard';
import Grafica from '../components/Grafica';
import Cargando from '../components/Cargando';
import EstadoError from '../components/EstadoError';
import { formatearNumero, formatearPorcentaje } from '../utils/formato';

export default function Dashboard() {
  const [datos, setDatos] = useState({});
  const [salud, setSalud] = useState(null);
  const [carreras, setCarreras] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError(null);
    try {
      const [saludRes, c1, c2, c4, c17, c18, c20, carrerasRes] = await Promise.all([
        api.get('/salud'),
        api.get('/consultas/mongo/1'),
        api.get('/consultas/mongo/2'),
        api.get('/consultas/mongo/4'),
        api.get('/consultas/mongo/17'),
        api.get('/consultas/mongo/18'),
        api.get('/consultas/mongo/20'),
        api.get('/catalogos/carreras')
      ]);
      setSalud(saludRes.data);
      setCarreras(carrerasRes.data);
      const filas = (respuesta) => (Array.isArray(respuesta.data) ? respuesta.data : respuesta.data.filas || []);
      setDatos({
        c1: filas(c1),
        c2: filas(c2),
        c4: filas(c4),
        c17: filas(c17),
        c18: filas(c18),
        c20: filas(c20)
      });
    } catch (consultaError) {
      setError(consultaError.message);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  if (cargando) {
    return <Cargando mensaje="Calculando indicadores y agregaciones…" />;
  }

  if (error) {
    return <EstadoError mensaje={error} onReintentar={cargar} />;
  }

  const colecciones = salud?.colecciones || {};
  const totalEvaluaciones = colecciones.evaluaciones || 0;
  const totalAspirantes = colecciones.aspirantes || 0;
  const totalRechazados = colecciones.registros_rechazados || 0;

  const aprobadosTotales = (datos.c18 || []).reduce((suma, fila) => suma + Number(fila.aprobados || 0), 0);
  const evaluacionesTotales = (datos.c18 || []).reduce((suma, fila) => suma + Number(fila.total || 0), 0);
  const tasaGlobal = evaluacionesTotales > 0 ? (aprobadosTotales / evaluacionesTotales) * 100 : 0;

  const sinDatos = totalEvaluaciones === 0;

  return (
    <div className="pagina">
      <header className="pagina-encabezado">
        <div>
          <p className="eyebrow">Resumen general</p>
          <h2>Panorama de las pruebas específicas</h2>
        </div>
        <button type="button" className="boton boton-secundario" onClick={cargar}>Actualizar datos</button>
      </header>

      {sinDatos && (
        <div className="aviso-vacio">
          <div>
            <strong>Aún no hay datos cargados.</strong>
            <p>Ve a la sección de carga y sube el CSV de pruebas específicas para habilitar el análisis.</p>
          </div>
          <Link className="boton boton-primario" to="/carga">Cargar datos</Link>
        </div>
      )}

      <section className="grid-kpis">
        <KpiCard titulo="Aspirantes únicos" valor={formatearNumero(totalAspirantes)} detalle="Documentos en la colección aspirantes" tono="indigo" icono="A" />
        <KpiCard titulo="Evaluaciones" valor={formatearNumero(totalEvaluaciones)} detalle="Registros válidos transformados" tono="cyan" icono="E" />
        <KpiCard titulo="Tasa de aprobación global" valor={formatearPorcentaje(tasaGlobal)} detalle={`${formatearNumero(aprobadosTotales)} evaluaciones aprobadas`} tono="verde" icono="%" />
        <KpiCard titulo="Carreras objetivo" valor={formatearNumero(carreras.length || (datos.c18 || []).length)} detalle={`${formatearNumero(totalRechazados)} registros rechazados`} tono="naranja" icono="C" />
      </section>

      <section className="grid-graficas">
        <article className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Consulta 1 · Aggregation pipeline</p>
              <h3>Aspirantes por tipo de institución</h3>
            </div>
            <span className="nota-tecnica">$group + $sum</span>
          </div>
          <Grafica tipo="bar" datos={datos.c1 || []} xKey="tipo" yKey="aspirantes" nombreY="Aspirantes" altura={280} />
        </article>

        <article className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Consulta 2 · Aggregation pipeline</p>
              <h3>Aprobados por materia</h3>
            </div>
            <span className="nota-tecnica">$match + $group</span>
          </div>
          <Grafica tipo="bar" datos={(datos.c2 || []).slice(0, 12)} xKey="materia" yKey="aprobados" nombreY="Aprobados" altura={280} />
        </article>

        <article className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Consulta 4 · Aggregation pipeline</p>
              <h3>Porcentaje de aprobación por materia</h3>
            </div>
            <span className="nota-tecnica">$cond + $divide</span>
          </div>
          <Grafica tipo="bar" datos={(datos.c4 || []).slice(0, 12)} xKey="materia" yKey="porcentaje" nombreY="% aprobación" unidad="%" altura={280} />
        </article>

        <article className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Consulta 17 · Aggregation pipeline</p>
              <h3>Top municipios por aspirantes</h3>
            </div>
            <span className="nota-tecnica">$addToSet + $size</span>
          </div>
          <Grafica tipo="pie" datos={datos.c17 || []} xKey="municipio" yKey="total_aspirantes" altura={280} />
        </article>

        <article className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Consulta 18 · Aggregation pipeline</p>
              <h3>Aprobación por tipo de institución</h3>
            </div>
            <span className="nota-tecnica">$group + $round</span>
          </div>
          <Grafica tipo="pie" datos={datos.c18 || []} xKey="tipo" yKey="porcentaje" unidad="%" altura={280} />
        </article>

        <article className="tarjeta">
          <div className="tarjeta-encabezado">
            <div>
              <p className="etiqueta">Consulta 20 · Aggregation pipeline</p>
              <h3>Evolución de la aprobación por año</h3>
            </div>
            <span className="nota-tecnica">$group + $divide</span>
          </div>
          <Grafica
            tipo="line"
            datos={datos.c20 || []}
            xKey="anio"
            series={[{ key: 'porcentaje_aprobacion', nombre: '% aprobación', color: '#4f46e5' }]}
            unidad="%"
            altura={280}
          />
        </article>
      </section>

      <section className="tarjeta nota-dashboard">
        <h3>¿Cómo leer estos resultados?</h3>
        <p>
          Las gráficas resumen el comportamiento de los aspirantes: el tipo de institución de procedencia,
          el rendimiento por materia, la distribución geográfica y la evolución anual. Cada tarjeta indica la
          consulta de MongoDB que la alimenta; el detalle completo de las 20 consultas (18 aggregation pipelines y 2 consultas documentales) está en la sección
          <Link to="/consultas"> Consultas MongoDB</Link>.
        </p>
      </section>
    </div>
  );
}
