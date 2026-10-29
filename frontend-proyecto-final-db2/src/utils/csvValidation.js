import Papa from 'papaparse';

const clean = (value) => String(value ?? '').replaceAll('\uFEFF', '').replace(/\s+/gu, ' ').trim();
const plain = (value) => clean(value).normalize('NFD').replace(/\p{M}/gu, '');
const header = (value) => plain(value).toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
const aliases = {
  correlativo_aspirante: ['correlativo', 'correlativo_del_aspirante', 'correlativo_estudiante', 'no_correlativo'],
  fecha_asignacion: ['fecha_de_asignacion', 'fecha_asignacion_prueba', 'fecha_evaluacion', 'fecha_de_evaluacion'],
  sexo: ['genero'],
  anio_nacimiento: ['ano_nacimiento', 'anio_de_nacimiento', 'ano_de_nacimiento'],
  materia: ['materia_evaluada', 'curso'],
  oportunidad: ['numero_de_fecha_de_evaluacion', 'numero_fecha_de_evaluacion', 'numero_de_fecha_evaluacion', 'no_de_fecha_de_evaluacion', 'numero_de_evaluacion', 'numero_de_intento'],
  anio_ingreso: ['anio_de_ingreso', 'ano_de_ingreso', 'ano_ingreso'],
  aprobacion: ['resultado', 'resultado_evaluacion'],
  carrera_objetivo: ['carrera_a_la_que_aspira', 'carrera_solicitada'],
  departamento_ie: ['departamento_institucion_educativa', 'departamento_de_la_institucion_educativa', 'departamento_del_establecimiento'],
  municipio_ie: ['municipio_institucion_educativa', 'municipio_de_la_institucion_educativa', 'municipio_del_establecimiento'],
  tipo_ie: ['tipo_institucion_educativa', 'tipo_de_institucion_educativa', 'sector'],
  archivo_origen: [], fila_origen: []
};
const lookup = Object.fromEntries(Object.entries(aliases).flatMap(([key, values]) => [key, ...values].map((value) => [value, key])));
const required = ['correlativo_aspirante', 'materia', 'oportunidad', 'anio_ingreso', 'aprobacion'];
const columns = Object.keys(aliases);
const integer = (value) => /^[+-]?\d+$/.test(clean(value)) && Number.isSafeInteger(Number(value)) && Number(value) >= -2147483648 && Number(value) <= 2147483647 ? Number(value) : null;

export function normalizeRecord(record, year = new Date().getFullYear()) {
  const data = Object.fromEntries(columns.map((key) => [key, clean(record[key])]));
  const errors = [], warnings = [];
  for (const key of ['correlativo_aspirante', 'materia']) if (!data[key]) errors.push(`${key}: valor obligatorio vacío`);
  for (const [key, min, max] of [['anio_ingreso', 1990, 2100], ['oportunidad', 1, 2147483647]]) {
    const value = integer(data[key]);
    if (value === null || value < min || value > max) errors.push(`${key}: entero fuera de rango o inválido`);
    data[key] = value;
  }
  const approval = plain(data.aprobacion).replace(/[_-]+/g, ' ').replace(/\s+/g, ' ').toUpperCase();
  if (['APROBADO', 'SI', '1'].includes(approval)) data.aprobacion = 'APROBADO';
  else if (['NO APROBADO', 'NO', '0'].includes(approval)) data.aprobacion = 'NO APROBADO';
  else errors.push('aprobacion: estado no reconocido');
  const birth = integer(data.anio_nacimiento);
  if (birth === null || birth < 1900 || birth > year) {
    if (data.anio_nacimiento) warnings.push('anio_nacimiento: convertido a nulo');
    data.anio_nacimiento = null;
  } else data.anio_nacimiento = birth;
  // Preserve supported date strings; reject impossible dates into a controlled null.
  if (data.fecha_asignacion) {
    const match = data.fecha_asignacion.match(/^(?:(\d{4})[-/](\d{2})[-/](\d{2})|(\d{1,2})[/-](\d{1,2})[/-](\d{4}))(?:[ T]\d{2}:\d{2}:\d{2})?$/);
    const y = Number(match?.[1] || match?.[6]), m = Number(match?.[2] || match?.[5]), d = Number(match?.[3] || match?.[4]);
    const date = new Date(0); date.setUTCFullYear(y, m - 1, d);
    const time = data.fecha_asignacion.match(/[ T](\d{2}):(\d{2}):(\d{2})$/);
    if (!match || date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d || (time && (Number(time[1]) > 23 || Number(time[2]) > 59 || Number(time[3]) > 59))) {
      data.fecha_asignacion = null; warnings.push('fecha_asignacion: fecha inválida convertida a nulo');
    } else data.fecha_asignacion = `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  } else data.fecha_asignacion = null;
  for (const key of ['materia', 'carrera_objetivo', 'departamento_ie', 'municipio_ie']) data[key] = data[key].toUpperCase();
  if (!data.carrera_objetivo) { data.carrera_objetivo = 'NINGUNO'; warnings.push('carrera_objetivo: vacío convertido a NINGUNO'); }
  data.departamento_ie ||= 'SIN REGISTRO'; data.municipio_ie ||= 'SIN REGISTRO';
  return { data, errors, warnings };
}

export function prepareCsv(text, name) {
  const source = text.replace(/^\uFEFF/, '');
  // Infer from the header, not from potentially corrupt data rows.
  const delimiter = [',', ';', '\t'].map((value) => {
    const first = Papa.parse(source, { delimiter: value, preview: 1, skipEmptyLines: 'greedy' }).data[0] || [];
    return { value, score: first.filter((cell) => lookup[header(cell)]).length };
  }).sort((a, b) => b.score - a.score)[0].value;
  const parsed = Papa.parse(source, { delimiter, skipEmptyLines: 'greedy' });
  const rows = parsed.data;
  const result = { nombre: name, leidos: Math.max(0, rows.length - 1), invalidos: 0, avisos: 0, errores: [], detalles: [], csv: null };
  const note = (message) => { if (result.detalles.length < 100) result.detalles.push(message); };
  if (!rows.length) { result.errores.push('Archivo vacío.'); return result; }
  const keys = rows[0].map((value) => lookup[header(value)]);
  const missing = required.filter((key) => !keys.includes(key));
  if (missing.length) result.errores.push(`Faltan columnas obligatorias: ${missing.join(', ')}.`);
  if (keys.some((key, index) => key && keys.indexOf(key) !== index)) result.errores.push('Hay encabezados duplicados o ambiguos.');
  const parseErrors = parsed.errors.filter((error) => error.code !== 'UndetectableDelimiter');
  if (parseErrors.length) result.errores.push('CSV corrupto: comillas o estructura inválidas. Revisa el archivo original.');
  if (!result.leidos) result.errores.push('No hay registros para enviar.');
  if (result.errores.length) return result;
  const normalized = [];
  rows.slice(1).forEach((row, index) => {
    if (row.length !== keys.length) { result.invalidos++; note(`Registro ${index + 1}: cantidad de columnas incorrecta.`); return; }
    const record = Object.fromEntries(keys.flatMap((key, i) => key ? [[key, row[i]]] : []));
    const { data, errors, warnings } = normalizeRecord(record);
    if (errors.length) result.invalidos++;
    result.avisos += warnings.length;
    [...errors, ...warnings].forEach((message) => note(`Registro ${index + 1}: ${message}`));
    data.archivo_origen ||= name;
    // Do not invent physical line numbers for multiline CSV records.
    if (!integer(data.fila_origen) || integer(data.fila_origen) < 1) data.fila_origen = '';
    normalized.push(data);
  });
  if (!result.invalidos) result.csv = Papa.unparse({ fields: columns, data: normalized.map((row) => columns.map((key) => row[key] ?? '')) }, { quotes: true });
  return result;
}
