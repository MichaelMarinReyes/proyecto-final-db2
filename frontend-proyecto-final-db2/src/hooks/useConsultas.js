import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../api/client';

export default function useConsultas(numero, filtros) {
  const [datos, setDatos] = useState([]);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState(null);
  const [generando, setGenerando] = useState(false);

  const parametros = useMemo(() => {
    const resultado = {};
    Object.entries(filtros || {}).forEach(([clave, valor]) => {
      if (valor !== null && valor !== undefined && valor !== '') {
        resultado[clave] = valor;
      }
    });
    return resultado;
  }, [filtros]);

  const cargar = useCallback(async () => {
    if (!numero) {
      setDatos([]);
      return [];
    }
    setCargando(true);
    setError(null);
    try {
      const { data } = await api.get(`/consultas/mongo/${numero}`, { params: parametros });
      setDatos(data);
      return data;
    } catch (consultaError) {
      setError(consultaError.message);
      setDatos([]);
      return [];
    } finally {
      setCargando(false);
    }
  }, [numero, parametros]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const generarResumen = useCallback(async () => {
    setGenerando(true);
    setError(null);
    try {
      const { data } = await api.post('/consultas/mongo/6/resumen');
      setDatos(data);
      return data;
    } catch (resumenError) {
      setError(resumenError.message);
      return [];
    } finally {
      setGenerando(false);
    }
  }, []);

  return { datos, cargando, error, recargar: cargar, generarResumen, generando };
}
