import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../api/client';

export default function useCarga() {
  const [reporte, setReporte] = useState(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState(null);
  const [rechazados, setRechazados] = useState([]);
  const intervalo = useRef(null);

  const detener = useCallback(() => {
    if (intervalo.current) {
      clearInterval(intervalo.current);
      intervalo.current = null;
    }
  }, []);

  useEffect(() => detener, [detener]);

  const vigilar = useCallback((id) => {
    detener();
    intervalo.current = setInterval(async () => {
      try {
        const { data } = await api.get(`/cargas/${id}`);
        setReporte(data);
        if (data.estado !== 'EN_PROCESO') {
          detener();
          setCargando(false);
        }
      } catch (consultaError) {
        detener();
        setCargando(false);
        setError(consultaError.message);
      }
    }, 1500);
  }, [detener]);

  const subir = useCallback(async (archivos) => {
    setError(null);
    setRechazados([]);
    const formulario = new FormData();
    archivos.forEach((archivo) => formulario.append('files', archivo));
    setCargando(true);
    try {
      const { data } = await api.post('/cargas/archivos', formulario, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      setReporte(data);
      vigilar(data.id);
      return data;
    } catch (subidaError) {
      setError(subidaError.message);
      setCargando(false);
      return null;
    }
  }, [vigilar]);

  const cargarRechazados = useCallback(async (id, pagina = 0, tamano = 50) => {
    try {
      const { data } = await api.get(`/cargas/${id}/rechazados`, { params: { page: pagina, size: tamano } });
      setRechazados(data);
      return data;
    } catch (consultaError) {
      setError(consultaError.message);
      return [];
    }
  }, []);

  const abrir = useCallback(async (id) => {
    setError(null);
    try {
      const { data } = await api.get(`/cargas/${id}`);
      setReporte(data);
      return data;
    } catch (consultaError) {
      setError(consultaError.message);
      return null;
    }
  }, []);

  return { reporte, cargando, error, rechazados, subir, cargarRechazados, abrir, detener };
}
