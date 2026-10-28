import { useEffect, useState } from 'react';
import api from '../api/client';

const TIPOS = ['materias', 'carreras', 'departamentos', 'municipios', 'tipos', 'anios'];

export default function useCatalogos() {
  const [catalogos, setCatalogos] = useState({
    materias: [],
    carreras: [],
    departamentos: [],
    municipios: [],
    tipos: [],
    anios: []
  });
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let activo = true;
    const cargar = async () => {
      setCargando(true);
      const resultados = await Promise.allSettled(TIPOS.map((tipo) => api.get(`/catalogos/${tipo}`)));
      if (!activo) return;
      const siguiente = {};
      resultados.forEach((resultado, indice) => {
        siguiente[TIPOS[indice]] = resultado.status === 'fulfilled' ? resultado.value.data : [];
      });
      setCatalogos(siguiente);
      const fallo = resultados.find((resultado) => resultado.status === 'rejected');
      setError(fallo ? fallo.reason.message : null);
      setCargando(false);
    };
    cargar();
    return () => {
      activo = false;
    };
  }, []);

  return { catalogos, cargando, error };
}
