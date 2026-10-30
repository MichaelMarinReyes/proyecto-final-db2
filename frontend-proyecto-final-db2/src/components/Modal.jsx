import { useEffect } from 'react';

export default function Modal({ abierto, titulo, subtitulo = 'Detalle', onCerrar, children, ancho = '980px' }) {
  useEffect(() => {
    if (!abierto) {
      return undefined;
    }
    const alPresionarTecla = (event) => {
      if (event.key === 'Escape') {
        onCerrar?.();
      }
    };
    const overflowPrevio = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', alPresionarTecla);
    return () => {
      document.body.style.overflow = overflowPrevio;
      document.removeEventListener('keydown', alPresionarTecla);
    };
  }, [abierto, onCerrar]);

  if (!abierto) {
    return null;
  }

  return (
    <div
      className="modal-fondo"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onCerrar?.();
        }
      }}
    >
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        style={{ '--modal-ancho': ancho }}
      >
        <header className="modal-encabezado">
          <div>
            <p className="etiqueta">{subtitulo}</p>
            <h3>{titulo}</h3>
          </div>
          <button type="button" className="boton-icono" onClick={onCerrar} aria-label="Cerrar">
            ×
          </button>
        </header>
        <div className="modal-cuerpo">{children}</div>
      </div>
    </div>
  );
}
