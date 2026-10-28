export default function EstadoError({ mensaje, onReintentar, titulo = 'No se pudo cargar la información' }) {
  return (
    <div className="estado-error" role="alert">
      <div className="estado-error-icono">!</div>
      <div>
        <h3>{titulo}</h3>
        <p>{mensaje}</p>
        <p className="estado-error-pista">
          Verifica que el backend esté en ejecución y que MongoDB y Neo4j estén disponibles.
        </p>
      </div>
      {onReintentar && (
        <button type="button" className="boton boton-primario" onClick={onReintentar}>
          Reintentar
        </button>
      )}
    </div>
  );
}
