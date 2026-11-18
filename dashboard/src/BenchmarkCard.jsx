import benchmark from './benchmark.json';

const ROWS = [
    ['Cache misses', 'misses'],
    ['Calls over 100 ms', 'slowCalls'],
    ['Hit rate', 'hitRate'],
    ['Slowest call (ms)', 'maxMs'],
    ['p99 latency (ms)', 'p99Ms'],
];

export default function BenchmarkCard() {
    return (
        <section className="benchmark-card glass-card">
            <div className="card-heading benchmark-heading">
                <div>
                    <p className="eyebrow">Benchmark</p>
                    <h2>Predictive warming: OFF vs ON</h2>
                </div>
                <span className="improved-badge">ON comparison</span>
            </div>
            <div className="benchmark-table" role="table" aria-label="Warming benchmark comparison">
                <div className="benchmark-row benchmark-header" role="row">
                    <span role="columnheader">Metric</span>
                    <span role="columnheader"><i className="status-dot off" /> Warming OFF</span>
                    <span role="columnheader"><i className="status-dot on" /> Warming ON</span>
                </div>
                {ROWS.map(([label, field]) => (
                    <div className="benchmark-row" role="row" key={field}>
                        <strong role="rowheader">{label}</strong>
                        <span className="benchmark-value off-value" role="cell">{formatValue(field, benchmark.off[field])}</span>
                        <span className="benchmark-value on-value" role="cell">{formatValue(field, benchmark.on[field])}</span>
                    </div>
                ))}
            </div>
            <p className="benchmark-note">{benchmark.note}</p>
        </section>
    );
}

function formatValue(field, value) {
    if (field === 'hitRate') return `${(value * 100).toFixed(1)}%`;
    return value;
}