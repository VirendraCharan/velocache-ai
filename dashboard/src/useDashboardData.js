import { useEffect, useState } from 'react';
import { fetchDashboard } from './api';

const POLL_MS = 3000;
const ENDPOINT = '/api/data';

export function useDashboardData(tenantId, opsKey) {
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!opsKey) {
            return;
        }
        let cancelled = false;

        async function load() {
            try {
                const [overlay, stats, events] = await Promise.all([
                    fetchDashboard('forecast-overlay', { tenantId, endpoint: ENDPOINT, minutes: 15 }, opsKey),
                    fetchDashboard('cache-stats', { tenantId }, opsKey),
                    fetchDashboard('warming-events', { tenantId, limit: 20 }, opsKey),
                ]);
                if (!cancelled) {
                    setData({ tenantId, overlay, stats, events });
                    setError(null);
                }
            } catch (e) {
                if (!cancelled) {
                    setError(e.message);
                }
            }
        }

        load();
        const timer = setInterval(load, POLL_MS);
        return () => {
            cancelled = true;
            clearInterval(timer);
        };
    }, [tenantId, opsKey]);

    return { data: data && data.tenantId === tenantId ? data : null, error };
}