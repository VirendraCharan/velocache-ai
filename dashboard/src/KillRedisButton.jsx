import { useEffect, useState } from 'react';
import { postPublic, fetchPublic } from './api';

export default function KillRedisButton({ tenantId }) {
    const [message, setMessage] = useState('');
    const [busy, setBusy] = useState(false);
    const [outageEndsAt, setOutageEndsAt] = useState(0);
    const [cooldownEnds, setCooldownEnds] = useState({});
    const [now, setNow] = useState(Date.now());

    const cooldownEndsAt = cooldownEnds[tenantId] || 0;
    const outageSeconds = Math.max(0, Math.ceil((outageEndsAt - now) / 1000));
    const cooldownSeconds = Math.max(0, Math.ceil((cooldownEndsAt - now) / 1000));

    useEffect(() => {
        let cancelled = false;

        async function refreshStatus() {
            try {
                const status = await fetchPublic('status', { tenantId });
                if (!cancelled) {
                    setOutageEndsAt(
                        status.simulatedRedisOutage
                            ? Date.now() + status.secondsRemaining * 1000
                            : 0
                    );
                }
            } catch {
                // Keep the last known state when polling fails.
            }
        }

        refreshStatus();
        const interval = setInterval(refreshStatus, 3000);
        return () => {
            cancelled = true;
            clearInterval(interval);
        };
    }, [tenantId]);

    useEffect(() => {
        if (outageEndsAt <= Date.now() && cooldownEndsAt <= Date.now()) {
            return undefined;
        }
        setNow(Date.now());
        const interval = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(interval);
    }, [outageEndsAt, cooldownEndsAt]);

    async function handleClick() {
        setBusy(true);
        setMessage('Sending...');
        try {
            const { status } = await postPublic('kill-redis', { tenantId });
            if (status === 202) {
                const currentTime = Date.now();
                setMessage('Simulated outage started. Watch the hit rate and the miss counter: requests now go straight to the origin.');
                setCooldownEnds((current) => ({
                    ...current,
                    [tenantId]: currentTime + 60000
                }));
                setOutageEndsAt(currentTime + 30000);
            } else if (status === 429) {
                setMessage('A simulated outage was just run. Please wait before the next one.');
                setCooldownEnds((current) => ({
                    ...current,
                    [tenantId]: Date.now() + 10000
                }));
            } else if (status === 404) {
                setMessage('This tenant is not available for the demo.');
            } else {
                setMessage('Could not start the outage.');
            }
        } catch {
            setMessage('Could not reach the server.');
        } finally {
            setBusy(false);
        }
    }

    return (
        <section className="simulate-panel glass-card">
            {outageSeconds > 0 && (
                <p className="outage-banner" role="status">Simulated Redis outage. Redis is still running; for this demo tenant the cache is bypassed and every request goes to the origin.</p>
            )}
            <button
                className="disconnect-button"
                type="button"
                onClick={handleClick}
                disabled={busy || outageSeconds > 0 || cooldownSeconds > 0}
            >
                {outageSeconds > 0
                    ? `Simulated outage: ${outageSeconds}s`
                    : cooldownSeconds > 0
                        ? `Cooldown ${cooldownSeconds}s`
                        : 'Kill Redis (simulated)'}
            </button>
            <p className="simulate-message" aria-live="polite">{message}</p>
            <p className="simulate-note">This simulates a Redis outage for this demo tenant for 30 seconds and also sends a burst of 40 requests. Redis itself keeps running. The app keeps answering, only slower, because every request goes to the origin.</p>
        </section>
    );
}
