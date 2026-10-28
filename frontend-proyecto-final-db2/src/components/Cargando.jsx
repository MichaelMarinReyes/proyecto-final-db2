export default function Cargando({ mensaje = 'Cargando información…' }) {
  return (
    <div className="cargando" role="status">
      <span className="spinner" />
      <p>{mensaje}</p>
    </div>
  );
}
