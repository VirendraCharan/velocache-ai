import { useState } from 'react';
import { useDashboardData } from './useDashboardData';
import StatCards from './StatCards';
import TenantSelector from './TenantSelector';
import TrafficChart from './TrafficChart';
import BenchmarkCard from './BenchmarkCard';

const TENANTS = ['tenant-a', 'tenant-b'];

export default function App() {
    const [opsKey, setOpsKey] = useState('');
    const [draftKey, setDraftKey] = useState('');
    const [tenantId, setTenantId] = useState(TENANTS[0]);
    const { data, error } = useDashboardData(tenantId, opsKey);

    return (
        <main>
            <h1>VeloCache AI</h1>
            {!opsKey ? (
                <form
                    onSubmit={(e) => {
                        e.preventDefault();
                        setOpsKey(draftKey);
                        setDraftKey('');
                    }}
                >
                    <label>
                        Ops key{' '}
                        <input
                            type="password"
                            autoComplete="off"
                            value={draftKey}
                            onChange={(e) => setDraftKey(e.target.value)}
                        />
                    </label>{' '}
                    <button type="submit">Connect</button>
                </form>
            ) : (
                <>
                    <button onClick={() => setOpsKey('')}>Disconnect</button>{' '}
                    <TenantSelector tenants={TENANTS} value={tenantId} onChange={setTenantId} />
                    {error && <p role="alert">Problem: {error}</p>}
                    {data ? (
                        <>
                            <h2>{tenantId}</h2>
                            <StatCards stats={data.stats} />
                            <TrafficChart
                                series={data.overlay.series}
                                forecast={data.overlay.forecast}
                                events={data.events}
                            />
                        </>
                    ) : (
                        !error && <p>Waiting for data…</p>
                    )}
                    <BenchmarkCard />
                </>
            )}

        </main>
    );
}