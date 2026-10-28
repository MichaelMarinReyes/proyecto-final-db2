import { Link } from 'react-router-dom';

export default function Error404() {
  return (
    <div className="pagina">
      <section className="tarjeta error-404">
        <p className="error-codigo">404</p>
        <h2>Página no encontrada</h2>
        <p>La ruta que intentaste abrir no existe o fue movida.</p>
        <Link to="/" className="boton boton-primario">Volver al dashboard</Link>
      </section>
    </div>
  );
}
