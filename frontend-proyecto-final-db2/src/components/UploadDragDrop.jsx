import { useRef, useState } from 'react';
import { formatearNumero } from '../utils/formato';

export default function UploadDragDrop({ archivos, onArchivos, onQuitar, onSubir, cargando, progreso, error }) {
  const inputRef = useRef(null);
  const [arrastrando, setArrastrando] = useState(false);
  const [aviso, setAviso] = useState(null);

  const agregar = (lista) => {
    const nuevos = Array.from(lista || []);
    const validos = nuevos.filter((archivo) => archivo.name.toLowerCase().endsWith('.csv'));
    if (validos.length !== nuevos.length) {
      setAviso('Solo se aceptan archivos con extensión .csv; los demás fueron ignorados.');
    } else {
      setAviso(null);
    }
    if (validos.length > 0) {
      onArchivos(validos);
    }
  };

  return (
    <section className="tarjeta">
      <div className="tarjeta-encabezado">
        <div>
          <h2>Cargar archivos CSV</h2>
          <p>Selecciona uno o varios archivos. El ETL valida, transforma y carga los datos en MongoDB y Neo4j.</p>
        </div>
        <span className="etiqueta">ETL</span>
      </div>

      <div
        className={`zona-carga ${arrastrando ? 'arrastrando' : ''} ${cargando ? 'deshabilitada' : ''}`}
        onDragOver={(event) => {
          event.preventDefault();
          if (!cargando) setArrastrando(true);
        }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={(event) => {
          event.preventDefault();
          setArrastrando(false);
          if (!cargando) agregar(event.dataTransfer.files);
        }}
        role="button"
        tabIndex={0}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') inputRef.current?.click();
        }}
        onClick={() => !cargando && inputRef.current?.click()}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".csv,text/csv"
          multiple
          hidden
          disabled={cargando}
          onChange={(event) => {
            agregar(event.target.files);
            event.target.value = '';
          }}
        />
        <div className="zona-carga-icono">CSV</div>
        <p className="zona-carga-titulo">Arrastra tus archivos aquí</p>
        <p className="zona-carga-texto">o haz clic para seleccionarlos desde tu equipo</p>
        <p className="zona-carga-nota">Tamaño máximo por archivo: 100 MB</p>
      </div>

      {aviso && <p className="mensaje-aviso">{aviso}</p>}
      {error && <p className="mensaje-error">{error}</p>}

      {archivos.length > 0 && (
        <div className="lista-archivos">
          {archivos.map((archivo, indice) => (
            <div className="archivo-item" key={`${archivo.name}-${indice}`}>
              <div className="archivo-info">
                <span className="archivo-tipo">CSV</span>
                <div>
                  <strong>{archivo.name}</strong>
                  <small>{formatearNumero(archivo.size)} bytes</small>
                </div>
              </div>
              <button
                type="button"
                className="boton-icono"
                onClick={() => onQuitar(indice)}
                disabled={cargando}
                aria-label={`Quitar ${archivo.name}`}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      {cargando && (
        <div className="progreso-wrapper">
          <div className="progreso-texto">
            <span>Procesando archivos…</span>
            <strong>{progreso ?? 0}%</strong>
          </div>
          <div className="progreso-barra">
            <div className="progreso-valor" style={{ width: `${Math.max(progreso ?? 0, 3)}%` }} />
          </div>
        </div>
      )}

      <div className="zona-acciones">
        <button type="button" className="boton boton-primario" onClick={onSubir} disabled={archivos.length === 0 || cargando}>
          {cargando ? 'Cargando…' : `Iniciar carga${archivos.length > 1 ? ` (${archivos.length} archivos)` : ''}`}
        </button>
        <p className="ayuda">El procesamiento continúa en segundo plano; el reporte se actualiza automáticamente.</p>
      </div>
    </section>
  );
}
