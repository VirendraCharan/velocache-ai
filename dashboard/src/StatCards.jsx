export default function StatCards({ stats }) {
    const noTraffic = stats.hits + stats.misses === 0;
    const hitRate = noTraffic ? '—' : (stats.hitRate * 100).toFixed(1) + '%';
    const totalRequests = stats.hits + stats.misses;
    const cards = [
        { label: 'Cache hits', value: stats.hits, accent: 'teal', icon: '↑' },
        { label: 'Cache misses', value: stats.misses, accent: 'coral', icon: '↓' },
        { label: 'Hit rate', value: hitRate, accent: 'violet', icon: '%' },
        { label: 'Total requests', value: totalRequests, accent: 'amber', icon: '#' },
    ];

    return (
        <div className="stat-grid">
            {cards.map((card, index) => (
                <Card key={card.label} {...card} delay={index * 70} />
            ))}
        </div>
    );
}

function Card({ label, value, accent, icon, delay }) {
    return (
        <div className={`stat-card glass-card accent-${accent}`} style={{ animationDelay: `${delay}ms` }}>
            <div className="stat-card-top">
                <span className="stat-icon">{icon}</span>
                <span className="accent-bar" />
            </div>
            <div className="stat-value" key={value}>{value}</div>
            <div className="stat-label">{label}</div>
        </div>
    );
}