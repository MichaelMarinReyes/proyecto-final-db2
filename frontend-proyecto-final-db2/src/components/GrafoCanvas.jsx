import { useEffect, useMemo, useRef, useState } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import { colorPorTipo, etiquetaNodo } from '../utils/formato';

export default function GrafoCanvas({ datos, onNodeClick, altura = 560 }) {
  const contenedor = useRef(null);
  const [ancho, setAncho] = useState(900);

  useEffect(() => {
    if (!contenedor.current) {
      return undefined;
    }
    const observador = new ResizeObserver((entradas) => {
      const entrada = entradas[0];
      if (entrada) {
        setAncho(Math.max(320, Math.floor(entrada.contentRect.width)));
      }
    });
    observador.observe(contenedor.current);
    return () => observador.disconnect();
  }, []);

  const graphData = useMemo(() => ({
    nodes: (datos?.nodes || []).map((nodo) => ({ ...nodo, name: etiquetaNodo(nodo) })),
    links: (datos?.links || []).map((link) => ({ ...link }))
  }), [datos]);

  if (!datos || datos.nodes.length === 0) {
    return (
      <div className="grafo-vacio">
        <p>No hay nodos para los filtros seleccionados.</p>
        <small>Prueba con menos filtros o carga un archivo CSV.</small>
      </div>
    );
  }

  return (
    <div ref={contenedor} className="grafo-canvas">
      <ForceGraph2D
        graphData={graphData}
        width={ancho}
        height={altura}
        backgroundColor="#0b1220"
        nodeRelSize={5}
        nodeColor={(nodo) => colorPorTipo(nodo.tipo)}
        nodeLabel={(nodo) => `${nodo.tipo}: ${nodo.name}`}
        linkColor={() => 'rgba(148, 163, 184, 0.42)'}
        linkWidth={1.1}
        linkDirectionalArrowLength={3.5}
        linkDirectionalArrowRelPos={0.9}
        linkLabel={(link) => link.tipo}
        onNodeClick={onNodeClick}
        cooldownTicks={140}
        d3AlphaDecay={0.02}
      />
    </div>
  );
}
