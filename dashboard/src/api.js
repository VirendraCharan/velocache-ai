const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

export async function fetchDashboard(path, params, opsKey) {
    const url = new URL('/api/dashboard/' + path, API_BASE);
    for (const [name, value] of Object.entries(params)) {
        url.searchParams.set(name, value);
    }
    const response = await fetch(url, { headers: { 'X-Ops-Key': opsKey } });
    if (response.status === 401) {
        throw new Error('Wrong or missing ops key');
    }
    if (!response.ok) {
        throw new Error('Server answered ' + response.status);
    }
    return response.json();
}