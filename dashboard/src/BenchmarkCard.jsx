import { useEffect, useState } from 'react';
import { fetchDashboard } from './api';

const ROWS = [
    ['Cache misses', 'misses'],
    ['Calls over 100 ms', 'slowCalls'],
    ['Hit rate', 'hitRate'],
    ['Slowest call (ms)', 'maxMs'],
    ['p99 latency (ms)', 'p99Ms'],
];

export default function BenchmarkCard({ opsKey }) {
    const [benchmark, setBenchmark] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError('');

        fetchDashboard('benchmark', {}, opsKey)
            .then((data) => {
                if (!cancelled) setBenchmark(data);
            })
            .catch((err) => {
                if (!cancelled) setError(err.message);
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [opsKey]);

    return (
        <section className="benchmark-card glass-card">
            <div className="card-heading benchmark-heading">
                <div>
                    <p className="eyebrow">Benchmark</p>
                    <h2>Predictive warming: OFF vs ON</h2>
                </div>
                <span className="improved-badge">ON comparison</span>
            </div>
            {loading ? (
                <p>Loading benchmark...</p>
            ) : error ? (
                <p>
                    {error === 'Server answered 404'
                        ? 'No benchmark run yet. Run simulator\\run_benchmark.py to create one.'
                        : 'Could not load benchmark: ' + error}
                </p>
            ) : !benchmark || !benchmark.off || !benchmark.on ? (
                <p>No benchmark run yet. Run simulator\run_benchmark.py to create one.</p>
            ) : (
                <>
                    <div className="benchmark-table" role="table" aria-label="Warming benchmark comparison">
                        <div className="benchmark-row benchmark-header" role="row">
                            <span role="columnheader">Metric</span>
                            <span role="columnheader"><i className="status-dot off" /> Warming OFF</span>
                            <span role="columnheader"><i className="status-dot on" /> Warming ON</span>
                        </div>
                        {ROWS.map(([label, field]) => (
                            <div className="benchmark-row" role="row" key={field}>
                                <strong role="rowheader">{label}</strong>
                                <span className="benchmark-value off-value" role="cell">{formatValue(field, benchmark.off)}</span>
                                <span className="benchmark-value on-value" role="cell">{formatValue(field, benchmark.on)}</span>
                            </div>
                        ))}
                    </div>
                    <p className="benchmark-note">{benchmark.note}</p>
                </>
            )}
        </section>
    );
}

function formatValue(field, row) {
    if (field === 'hitRate') {
        const hits = row.hits;
        const misses = row.misses;
        const total = hits + misses;
        if (Number.isFinite(hits) && Number.isFinite(misses) && total > 0) {
            return `${((hits / total) * 100).toFixed(1)}%`;
        }
        return '—';
    }
    return row[field];
}