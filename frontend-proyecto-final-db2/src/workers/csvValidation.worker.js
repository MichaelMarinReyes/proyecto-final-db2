import { prepareCsv } from '../utils/csvValidation';

self.onmessage = async ({ data: files }) => {
  try {
    const results = [];
    for (const file of files) {
      if (!file.name.toLowerCase().endsWith('.csv') || file.size > 20 * 1024 * 1024) {
        results.push({ nombre: file.name, leidos: 0, invalidos: 0, avisos: 0, detalles: [], errores: ['Selecciona un CSV de hasta 20 MB. Divide los archivos mayores conservando los encabezados.'], csv: null });
        continue;
      }
      const bytes = await file.arrayBuffer();
      let text;
      try { text = new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
      catch { throw new Error(`${file.name}: codificación no válida. Guarda el archivo como CSV UTF-8 antes de cargarlo.`); }
      results.push(prepareCsv(text, file.name));
    }
    self.postMessage({ results });
  } catch (error) {
    self.postMessage({ error: error.message || 'No fue posible leer los archivos.' });
  }
};
