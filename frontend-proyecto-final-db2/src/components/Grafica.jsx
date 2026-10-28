import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import { formatearValor } from '../utils/formato';

export const COLORES = ['#4f46e5', '#0891b2', '#16a34a', '#ea580c', '#ca8a04', '#db2777', '#7c3aed', '#0d9488', '#dc2626'];

function acortar(value) {
  const texto = String(value ?? '');
  return texto.length > 18 ? `${texto.slice(0, 17)}…` : texto;
}

function tooltipFormatter(unidad) {
  return (value, name) => [`${formatearValor(value)}${unidad || ''}`, name];
}

export default function Grafica({
  tipo = 'bar',
  datos = [],
  xKey = 'label',
  yKey = 'value',
  series = [],
  altura = 300,
  unidad = '',
  nombreY
}) {
  if (!datos || datos.length === 0) {
    return <p className="vacio-mini">Sin datos para graficar</p>;
  }

  const seriesEfectivas = series.length > 0 ? series : [{ key: yKey, nombre: nombreY || yKey, color: COLORES[0] }];

  if (tipo === 'pie') {
    return (
      <ResponsiveContainer width="100%" height={altura}>
        <PieChart>
          <Pie
            data={datos}
            dataKey={yKey}
            nameKey={xKey}
            cx="50%"
            cy="50%"
            innerRadius="38%"
            outerRadius="72%"
            paddingAngle={2}
          >
            {datos.map((entrada, indice) => (
              <Cell key={`celda-${indice}`} fill={COLORES[indice % COLORES.length]} />
            ))}
          </Pie>
          <Tooltip formatter={tooltipFormatter(unidad)} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
        </PieChart>
      </ResponsiveContainer>
    );
  }

  if (tipo === 'line' || tipo === 'area') {
    const Contenedor = tipo === 'area' ? AreaChart : LineChart;
    return (
      <ResponsiveContainer width="100%" height={altura}>
        <Contenedor data={datos} margin={{ top: 10, right: 18, left: 0, bottom: 30 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey={xKey} tick={{ fontSize: 11 }} tickFormatter={acortar} angle={-15} textAnchor="end" height={50} />
          <YAxis tick={{ fontSize: 11 }} width={54} />
          <Tooltip formatter={tooltipFormatter(unidad)} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {seriesEfectivas.map((serie, indice) =>
            tipo === 'area' ? (
              <Area
                key={serie.key}
                type="monotone"
                dataKey={serie.key}
                name={serie.nombre}
                stroke={serie.color || COLORES[indice % COLORES.length]}
                fill={serie.color || COLORES[indice % COLORES.length]}
                fillOpacity={0.15}
                strokeWidth={2}
              />
            ) : (
              <Line
                key={serie.key}
                type="monotone"
                dataKey={serie.key}
                name={serie.nombre}
                stroke={serie.color || COLORES[indice % COLORES.length]}
                strokeWidth={2}
                dot={{ r: 3 }}
              />
            )
          )}
        </Contenedor>
      </ResponsiveContainer>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={altura}>
      <BarChart data={datos} margin={{ top: 10, right: 18, left: 0, bottom: 45 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
        <XAxis dataKey={xKey} tick={{ fontSize: 11 }} tickFormatter={acortar} angle={-20} textAnchor="end" height={65} interval={0} />
        <YAxis tick={{ fontSize: 11 }} width={54} />
        <Tooltip formatter={tooltipFormatter(unidad)} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        {seriesEfectivas.map((serie, indice) => (
          <Bar
            key={serie.key}
            dataKey={serie.key}
            name={serie.nombre}
            fill={serie.color || COLORES[indice % COLORES.length]}
            radius={[5, 5, 0, 0]}
            maxBarSize={56}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}
