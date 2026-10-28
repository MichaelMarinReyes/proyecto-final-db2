import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import api from '../api/client';

const NAVEGACION = [
  { ruta: '/', titulo: 'Dashboard', icono: 'M3 13h8V3H3v10Zm0 8h8v-6H3v6Zm10 0h8V11h-8v10Zm0-18v6h8V3h-8Z' },
  { ruta: '/carga', titulo: 'Carga de datos', icono: 'M12 3v12m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2' },
  { ruta: '/consultas', titulo: 'Consultas MongoDB', icono: 'M4 19V5m0 14h16M8 16V9m4 7V6m4 10v-4' },
  { ruta: '/grafos', titulo: 'Explorador de grafos', icono: 'M12 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm-7 12a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm14 0a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM10.5 8.5 6.5 14m7-5.5 4 5.5M8 18h8' },
  { ruta: '/buscador', titulo: 'Buscador', icono: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm9 16-4.35-4.35' }
];

const TITULOS = {
  '/': 'Dashboard analítico',
  '/carga': 'Carga y transformación de datos',
  '/consultas': 'Consultas analíticas en MongoDB',
  '/grafos': 'Explorador de relaciones en Neo4j',
  '/buscador': 'Buscador de información'
};

function Icono({ path }) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={path} />
    </svg>
  );
}

export default function Layout() {
  const location = useLocation();
  const [salud, setSalud] = useState(null);
  const [menuAbierto, setMenuAbierto] = useState(false);

  useEffect(() => {
    let activo = true;
    const consultar = async () => {
      try {
        const { data } = await api.get('/salud');
        if (activo) setSalud(data);
      } catch (error) {
        if (activo) setSalud({ estado: 'SIN CONEXIÓN', mongo: { disponible: false }, neo4j: { disponible: false } });
      }
    };
    consultar();
    const intervalo = setInterval(consultar, 30000);
    return () => {
      activo = false;
      clearInterval(intervalo);
    };
  }, []);

  useEffect(() => {
    setMenuAbierto(false);
  }, [location.pathname]);

  const mongoOk = salud?.mongo?.disponible;
  const neo4jOk = salud?.neo4j?.disponible;

  return (
    <div className="app-shell">
      {menuAbierto && <button type="button" className="sidebar-overlay" aria-label="Cerrar menú" onClick={() => setMenuAbierto(false)} />}
      <aside className={`sidebar ${menuAbierto ? 'abierto' : ''}`}>
        <div className="marca">
          <div className="marca-logo">USAC</div>
          <div>
            <strong>Pruebas Específicas</strong>
          </div>
        </div>

        <nav className="navegacion" aria-label="Navegación principal">
          {NAVEGACION.map((item) => (
            <NavLink
              key={item.ruta}
              to={item.ruta}
              end={item.ruta === '/'}
              className={({ isActive }) => `nav-item ${isActive ? 'activo' : ''}`}
            >
              <Icono path={item.icono} />
              <span>{item.titulo}</span>
            </NavLink>
          ))}
        </nav>

        <div className="estado-servicios">
          <p className="estado-titulo">Servicios</p>
          <div className="servicio">
            <span className={`punto ${mongoOk ? 'ok' : 'error'}`} />
            <span>MongoDB</span>
            <small>{mongoOk ? 'en línea' : 'sin conexión'}</small>
          </div>
          <div className="servicio">
            <span className={`punto ${neo4jOk ? 'ok' : 'error'}`} />
            <span>Neo4j</span>
            <small>{neo4jOk ? 'en línea' : 'sin conexión'}</small>
          </div>
          <p className="estado-nota">
            {salud?.colecciones?.evaluaciones !== undefined
              ? `${new Intl.NumberFormat('es-GT').format(salud.colecciones.evaluaciones)} evaluaciones cargadas`
              : 'Sin datos disponibles'}
          </p>
        </div>
      </aside>

      <div className="app-main">
        <header className="topbar">
          <button type="button" className="menu-boton" onClick={() => setMenuAbierto(true)} aria-label="Abrir menú">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <div>
            <p className="migas">Facultad de Ingeniería · USAC</p>
            <h1>{TITULOS[location.pathname] || 'Pruebas Específicas'}</h1>
          </div>
          <div className={`salud-pill ${salud?.estado === 'OK' ? 'ok' : 'degradado'}`}>
            <span className="punto" />
            {salud?.estado === 'OK' ? 'Sistema operativo' : 'Sistema degradado'}
          </div>
        </header>

        <main className="contenido">
          <Outlet context={{ salud }} />
        </main>

        <footer className="pie">
          <span>Proyecto Final · Sistemas de Bases de Datos 2</span>
          <span>MongoDB + Neo4j + Spring Boot + React</span>
        </footer>
      </div>
    </div>
  );
}
