import { postPublic } from './api';
import { useEffect, useState } from 'react';

const COOLDOWN_SECONDS = 60;

export default function SimulateButton({ tenantId }) {
    const [message, setMessage] = useState('');
    const [busy, setBusy] = useState(false);
    const [cooldownEnds, setCooldownEnds] = useState({});
    const [now, setNow] = useState(Date.now());

    const endTime = cooldownEnds[tenantId];
    const remainingSeconds = endTime
        ? Math.max(0, Math.ceil((endTime - now) / 1000))
        : 0;

    useEffect(() => {
        if (!endTime || endTime <= Date.now()) {
            return undefined;
        }
        setNow(Date.now());
        const interval = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(interval);
    }, [tenantId, endTime]);

    async function handleClick() {
        setBusy(true);
        setMessage('Sending...');
        try {
            const { status } = await postPublic('simulate', { tenantId });
            if (status === 202) {
                setMessage('Burst started. Watch the chart and the counters for the next few seconds.');
                setCooldownEnds((current) => ({
                    ...current,
                    [tenantId]: Date.now() + COOLDOWN_SECONDS * 1000
                }));
            } else if (status === 429) {
                setMessage('A burst was just run. Please wait about a minute before the next one.');
                setCooldownEnds((current) => ({
                    ...current,
                    [tenantId]: Date.now() + 10000
                }));
            } else if (status === 404) {
                setMessage('This tenant is not available for the demo.');
            } else {
                setMessage('Could not start the burst.');
            }
        } catch {
            setMessage('Could not reach the server.');
        } finally {
            setBusy(false);
        }
    }

    return (
        <section className="simulate-panel glass-card">
            <button className="disconnect-button" type="button" onClick={handleClick} disabled={busy || remainingSeconds > 0}>
                {remainingSeconds > 0 ? `Cooldown ${remainingSeconds}s` : 'Simulate traffic'}
            </button>
            <p className="simulate-message" aria-live="polite">{message}</p>
            <p className="simulate-note">This sends a short simulated burst of 40 requests through the real cache path. It shows the cache reacting to load. It does not trigger a forecast.</p>
        </section>
    );
}
