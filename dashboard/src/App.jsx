import { useEffect, useState } from 'react';
import { useDashboardData } from './useDashboardData';
import StatCards from './StatCards';
import TenantSelector from './TenantSelector';
import TrafficChart from './TrafficChart';
import BenchmarkCard from './BenchmarkCard';
import './App.css';

const TENANTS = ['tenant-a', 'tenant-b'];

export default function App() {
    const [opsKey, setOpsKey] = useState('');
    const [draftKey, setDraftKey] = useState('');
    const [tenantId, setTenantId] = useState(TENANTS[0]);
    const { data, error } = useDashboardData(tenantId, opsKey);
    const [lastUpdated, setLastUpdated] = useState(null);

    useEffect(() => {
        if (data) setLastUpdated(new Date());
    }, [data]);

    const updatedLabel = lastUpdated
        ? lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
        : 'Waiting';

    return (
        <main className="dashboard-shell">
            {!opsKey ? (
                <section className="login-panel glass-card">
                    <div className="brand-mark">VC</div>
                    <div>
                        <p className="eyebrow">Operations console</p>
                        <h1>VeloCache AI</h1>
                    </div>
                    <form
                        className="ops-form"
                        onSubmit={(e) => {
                            e.preventDefault();
                            setOpsKey(draftKey);
                            setDraftKey('');
                        }}
                    >
                        <label htmlFor="ops-key">Ops key</label>
                        <input
                            id="ops-key"
                            type="password"
                            autoComplete="off"
                            value={draftKey}
                            onChange={(e) => setDraftKey(e.target.value)}
                            placeholder="Enter your key"
                        />
                        <button type="submit" disabled={!draftKey.trim()}>
                            Connect
                        </button>
                    </form>
                </section>
            ) : (
                <>
                    <header className="dashboard-header">
                        <div className="header-brand">
                            <div className="brand-mark">VC</div>
                            <div>
                                <h1>VeloCache AI</h1>
                                <div className="live-status"><span className="live-dot" /> LIVE</div>
                            </div>
                        </div>
                        <div className="header-controls">
                            <div className="updated-time">
                                <span>Last updated</span>
                                <time>{updatedLabel}</time>
                            </div>
                            <TenantSelector tenants={TENANTS} value={tenantId} onChange={setTenantId} />
                            <button className="disconnect-button" onClick={() => setOpsKey('')}>Disconnect</button>
                        </div>
                    </header>

                    <div className="dashboard-content">
                        {error && (
                            <section className="state-card error-state glass-card" role="alert">
                                <div className="state-icon warning-icon">!</div>
                                <div>
                                    <h2>Connection interrupted</h2>
                                    <p>{error}</p>
                                </div>
                            </section>
                        )}
                        {data ? (
                            <>
                                <div className="section-heading">
                                    <div>
                                        <p className="eyebrow">Overview</p>
                                        <h2>{tenantId}</h2>
                                    </div>
                                    <span className="environment-badge">Production</span>
                                </div>
                                <StatCards stats={data.stats} />
                                <TrafficChart
                                    series={data.overlay.series}
                                    forecast={data.overlay.forecast}
                                    events={data.events}
                                />
                            </>
                        ) : (
                            !error && (
                                <section className="state-card loading-state glass-card" aria-live="polite">
                                    <span className="loading-spinner" />
                                    <div>
                                        <h2>Loading operations data</h2>
                                        <p>Connecting to {tenantId}…</p>
                                    </div>
                                </section>
                            )
                        )}
                        <BenchmarkCard opsKey={opsKey} />
                    </div>
                </>
            )}
        </main>
    );
}