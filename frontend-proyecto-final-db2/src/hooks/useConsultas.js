import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../api/client';

export default function useConsultas(numero, filtros) {
  const [datos, setDatos] = useState([]);
  const [consulta, setConsulta] = useState(null);
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
      setConsulta(null);
      return [];
    }
    setCargando(true);
    setError(null);
    try {
      const { data } = await api.get(`/consultas/mongo/${numero}`, { params: parametros });
      const filas = Array.isArray(data) ? data : data.filas || [];
      setDatos(filas);
      setConsulta(Array.isArray(data) ? null : data.consulta || null);
      return filas;
    } catch (consultaError) {
      setError(consultaError.message);
      setDatos([]);
      setConsulta(null);
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
      const filas = Array.isArray(data) ? data : data.filas || [];
      setDatos(filas);
      setConsulta(Array.isArray(data) ? null : data.consulta || null);
      return filas;
    } catch (resumenError) {
      setError(resumenError.message);
      return [];
    } finally {
      setGenerando(false);
    }
  }, []);

  return { datos, consulta, cargando, error, recargar: cargar, generarResumen, generando };
}
