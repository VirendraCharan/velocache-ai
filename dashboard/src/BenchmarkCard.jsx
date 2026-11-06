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
        <section
            style={{ border: '1px solid #444', borderRadius: 8, padding: 16, margin: '24px auto', maxWidth: 560 }}
        >
            <h2>Predictive warming: OFF vs ON</h2>
            <table style={{ width: '100%', textAlign: 'center' }}>
                <thead>
                <tr>
                    <th></th>
                    <th>Warming OFF</th>
                    <th>Warming ON</th>
                </tr>
                </thead>
                <tbody>
                {ROWS.map(([label, field]) => (
                    <tr key={field}>
                        <td style={{ textAlign: 'left' }}>{label}</td>
                        <td>{benchmark.off[field]}</td>
                        <td>{benchmark.on[field]}</td>
                    </tr>
                ))}
                </tbody>
            </table>
            <p style={{ opacity: 0.8, fontSize: 14 }}>{benchmark.note}</p>
        </section>
    );
}