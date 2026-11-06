export default function StatCards({ stats }) {
    const noTraffic = stats.hits + stats.misses === 0;
    const hitRate = noTraffic ? '—' : (stats.hitRate * 100).toFixed(1) + '%';
    return (
        <div style={{ display: 'flex', gap: 16, justifyContent: 'center', margin: '16px 0' }}>
            <Card label="Cache hits" value={stats.hits} />
            <Card label="Cache misses" value={stats.misses} />
            <Card label="Hit rate" value={hitRate} />
        </div>
    );
}

function Card({ label, value }) {
    return (
        <div style={{ border: '1px solid #444', borderRadius: 8, padding: '12px 20px' }}>
            <div style={{ fontSize: 28 }}>{value}</div>
            <div style={{ opacity: 0.7 }}>{label}</div>
        </div>
    );
}