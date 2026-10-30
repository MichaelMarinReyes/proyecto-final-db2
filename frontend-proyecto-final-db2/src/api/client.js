import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
  timeout: 180000
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const detalle = error.response?.data?.mensaje
      || error.response?.data?.message
      || error.message
      || 'No se pudo completar la solicitud';
    const wrapped = new Error(detalle);
    wrapped.status = error.response?.status;
    wrapped.original = error;
    return Promise.reject(wrapped);
  }
);

export default api;
