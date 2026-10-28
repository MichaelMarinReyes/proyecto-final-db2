export function formatearNumero(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  return new Intl.NumberFormat('es-GT').format(Number(value));
}

export function formatearPorcentaje(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  return `${Number(value).toFixed(2)}%`;
}

export function formatearFecha(value) {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return new Intl.DateTimeFormat('es-GT', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date);
}

export function formatearFechaCorta(value) {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  // ETL dates represent calendar days stored at midnight UTC, not local instants.
  return new Intl.DateTimeFormat('es-GT', { dateStyle: 'medium', timeZone: 'UTC' }).format(date);
}

export function formatearDuracion(ms) {
  if (ms === null || ms === undefined) {
    return '—';
  }
  if (ms < 1000) {
    return `${ms} ms`;
  }
  if (ms < 60000) {
    return `${(ms / 1000).toFixed(2)} s`;
  }
  const minutos = Math.floor(ms / 60000);
  const segundos = Math.round((ms % 60000) / 1000);
  return `${minutos} min ${segundos} s`;
}

export function formatearValor(value) {
  if (value === null || value === undefined) {
    return '—';
  }
  if (typeof value === 'number') {
    if (Number.isInteger(value)) {
      // Years, ages and small counts should not be rendered as "2,018".
      return Math.abs(value) < 10000 ? String(value) : formatearNumero(value);
    }
    return Number(value).toFixed(2);
  }
  if (typeof value === 'boolean') {
    return value ? 'Sí' : 'No';
  }
  if (Array.isArray(value)) {
    return value.join(', ');
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

export function descargarCsv(nombre, columnas, filas) {
  const escape = (value) => {
    const text = formatearValor(value).replace(/"/g, '""');
    return `"${text}"`;
  };
  const lineas = [columnas.map(escape).join(',')];
  filas.forEach((fila) => {
    lineas.push(columnas.map((columna) => escape(fila[columna])).join(','));
  });
  const blob = new Blob([`\uFEFF${lineas.join('\n')}`], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nombre;
  link.click();
  URL.revokeObjectURL(url);
}

export function colorPorTipo(tipo) {
  const colores = {
    Aspirante: '#4f46e5',
    Carrera: '#0891b2',
    Materia: '#16a34a',
    Institucion: '#ea580c',
    Municipio: '#ca8a04',
    Departamento: '#db2777',
    Evaluacion: '#7c3aed'
  };
  return colores[tipo] || '#64748b';
}

export function etiquetaNodo(nodo) {
  const propiedades = nodo?.propiedades || {};
  if (propiedades.nombre) {
    return propiedades.nombre;
  }
  if (propiedades.correlativo) {
    return propiedades.correlativo;
  }
  if (propiedades.materia) {
    return `${propiedades.materia} ${propiedades.anio_ingreso ?? ''} #${propiedades.oportunidad ?? ''}`.trim();
  }
  return propiedades.key || nodo?.label || nodo?.id || 'Nodo';
}

export function tituloColumna(columna) {
  return columna
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letra) => letra.toUpperCase());
}
