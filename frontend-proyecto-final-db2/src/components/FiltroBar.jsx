export function SelectCampo({ etiqueta, valor, onChange, opciones = [], placeholder = 'Todos', disabled = false }) {
  return (
    <label className="campo">
      <span>{etiqueta}</span>
      <select value={valor ?? ''} onChange={(event) => onChange(event.target.value)} disabled={disabled}>
        <option value="">{placeholder}</option>
        {opciones.map((opcion) => {
          const value = typeof opcion === 'object' ? opcion.value : opcion;
          const label = typeof opcion === 'object' ? opcion.label : opcion;
          return <option key={String(value)} value={value}>{label}</option>;
        })}
      </select>
    </label>
  );
}

export default function FiltroBar({ children, onLimpiar, onAplicar, aplicando = false, textoAplicar = 'Aplicar filtros' }) {
  return (
    <div className="filtro-bar">
      <div className="filtro-campos">{children}</div>
      {(onLimpiar || onAplicar) && (
        <div className="filtro-acciones">
          {onLimpiar && (
            <button type="button" className="boton boton-secundario" onClick={onLimpiar}>
              Limpiar
            </button>
          )}
          {onAplicar && (
            <button type="button" className="boton boton-primario" onClick={onAplicar} disabled={aplicando}>
              {aplicando ? 'Consultando…' : textoAplicar}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
