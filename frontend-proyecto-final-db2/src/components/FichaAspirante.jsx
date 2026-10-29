import TablaResultados from './TablaResultados';
import { formatearNumero, formatearValor } from '../utils/formato';

export default function FichaAspirante({ ficha, historial }) {
  if (!ficha) return null;
  const evaluaciones = historial ?? ficha.evaluaciones ?? [];
  return (
    <div className="ficha-aspirante">
      <div className="tarjeta-encabezado">
        <div>
          <p className="etiqueta">Ficha del aspirante</p>
          <h3>{ficha._id}</h3>
          <p>Información completa e historial de evaluaciones del aspirante.</p>
        </div>
        <span className="chip">{formatearNumero(evaluaciones.length)} evaluaciones</span>
      </div>
      <div className="grid-resumen">
        <div><span>Sexo registrado al primer ingreso</span><strong>{formatearValor(ficha.sexo)}</strong></div>
        <div><span>Año de nacimiento registrado</span><strong>{formatearValor(ficha.anio_nacimiento)}</strong></div>
        <div><span>Carreras objetivo</span><strong>{(ficha.carreras_objetivo || []).join(', ') || '—'}</strong></div>
        <div><span>Materias evaluadas</span><strong>{(ficha.materias || []).join(', ') || '—'}</strong></div>
      </div>
      <h4>Historial completo de evaluaciones</h4>
      <TablaResultados
        key={ficha._id}
        titulo={`historial-${ficha._id}`}
        filas={evaluaciones}
        columnas={['fecha_asignacion', 'sexo', 'anio_nacimiento', 'materia', 'oportunidad', 'anio_ingreso',
          'edad_ingreso', 'aprobacion', 'carrera_objetivo', 'departamento_ie', 'municipio_ie', 'tipo_ie', 'archivo_origen']}
      />
      <details className="ficha-documento">
        <summary>Ver documento completo y campos técnicos</summary>
        <pre>{JSON.stringify({ ...ficha, evaluaciones }, null, 2)}</pre>
      </details>
    </div>
  );
}
