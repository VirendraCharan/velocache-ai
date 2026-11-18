import { postPublic } from './api';
import { useState } from 'react';

export default function SimulateButton({ tenantId }) {
    const [message, setMessage] = useState('');
    const [busy, setBusy] = useState(false);

    async function handleClick() {
        setBusy(true);
        setMessage('Sending...');
        try {
            const { status } = await postPublic('simulate', { tenantId });
            if (status === 202) {
                setMessage('Burst started. Watch the chart and the counters for the next few seconds.');
            } else if (status === 429) {
                setMessage('A burst was just run. Please wait about a minute before the next one.');
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
            <button className="disconnect-button" type="button" onClick={handleClick} disabled={busy}>
                Simulate traffic
            </button>
            <p className="simulate-message" aria-live="polite">{message}</p>
            <p className="simulate-note">This sends a short simulated burst of 40 requests through the real cache path. It shows the cache reacting to load. It does not trigger a forecast.</p>
        </section>
    );
}
