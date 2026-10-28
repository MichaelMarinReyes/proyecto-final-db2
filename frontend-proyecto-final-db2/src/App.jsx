import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Carga from './pages/Carga';
import Consultas from './pages/Consultas';
import ExploradorGrafos from './pages/ExploradorGrafos';
import Buscador from './pages/Buscador';
import Error404 from './pages/Error404';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="carga" element={<Carga />} />
        <Route path="consultas" element={<Consultas />} />
        <Route path="grafos" element={<ExploradorGrafos />} />
        <Route path="buscador" element={<Buscador />} />
        <Route path="inicio" element={<Navigate to="/" replace />} />
        <Route path="*" element={<Error404 />} />
      </Route>
    </Routes>
  );
}
