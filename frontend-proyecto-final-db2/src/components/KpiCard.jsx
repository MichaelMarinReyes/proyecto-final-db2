export default function KpiCard({ titulo, valor, detalle, tono = 'indigo', icono }) {
  return (
    <article className={`kpi-card tono-${tono}`}>
      <div className="kpi-icono" aria-hidden="true">{icono}</div>
      <div>
        <p className="kpi-titulo">{titulo}</p>
        <p className="kpi-valor">{valor}</p>
        {detalle && <p className="kpi-detalle">{detalle}</p>}
      </div>
    </article>
  );
}
