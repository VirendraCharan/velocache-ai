import {
    Area,
    AreaChart,
    CartesianGrid,
    ReferenceDot,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';

function forecastMessage(forecast) {
    if (forecast.status === 'ok' && forecast.predictedMinute) {
        return 'Next rush expected at ' + forecast.predictedMinute + ' (confidence: ' + forecast.confidence + ')';
    }
    if (forecast.status === 'ok') {
        return 'Forecast is fresh, but no rush is predicted';
    }
    if (forecast.status === 'stale') {
        return 'Forecast is out of date, so warming is paused';
    }
    return 'No forecast available';
}

export default function TrafficChart({ series, forecast, events }) {
    const minutesOnAxis = new Set(series.map((point) => point.minute));
    const predictedPoint = series.find((point) => point.minute === forecast.predictedMinute);
    const markerVisible =
        forecast.status === 'ok' &&
        forecast.predictedMinute &&
        minutesOnAxis.has(forecast.predictedMinute);
    const warmedMinutes = [...new Set(events.map((event) => event.minute))].filter((minute) =>
        minutesOnAxis.has(minute)
    );

    return (
        <section className="traffic-card glass-card">
            <div className="card-heading">
                <div>
                    <p className="eyebrow">Traffic operations</p>
                    <h2>Requests & warming</h2>
                </div>
                <div className="chart-legend">
                    <span><i className="legend-line teal" />Requests</span>
                    <span><i className="legend-line amber" />Forecast</span>
                    <span><i className="legend-dot" />Warming</span>
                </div>
            </div>
            <div className="chart-wrap">
                <ResponsiveContainer>
                    <AreaChart data={series} margin={{ top: 16, right: 12, bottom: 0, left: -12 }}>
                        <defs>
                            <linearGradient id="trafficArea" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="0%" stopColor="#2dd4bf" stopOpacity={0.32} />
                                <stop offset="100%" stopColor="#2dd4bf" stopOpacity={0.02} />
                            </linearGradient>
                        </defs>
                        <CartesianGrid stroke="#8b97ad" strokeOpacity={0.12} strokeDasharray="3 6" vertical={false} />
                        <XAxis dataKey="minute" axisLine={false} tickLine={false} tick={{ fill: '#8b97ad', fontSize: 12 }} />
                        <YAxis allowDecimals={false} axisLine={false} tickLine={false} tick={{ fill: '#8b97ad', fontSize: 12 }} width={48} />
                        <Tooltip content={<TrafficTooltip />} cursor={{ stroke: '#a78bfa', strokeOpacity: 0.35 }} />
                        <Area
                            type="linear"
                            dataKey="actual"
                            name="Requests"
                            stroke="#2dd4bf"
                            strokeWidth={2.5}
                            fill="url(#trafficArea)"
                            dot={false}
                            connectNulls={false}
                            isAnimationActive={false}
                        />
                        {markerVisible && (
                            <ReferenceLine
                                x={forecast.predictedMinute}
                                stroke="#fbbf24"
                                strokeDasharray="6 4"
                                ifOverflow="visible"
                                label={{ value: 'Forecast', position: 'insideTopRight', fill: '#fbbf24', fontSize: 11 }}
                            />
                        )}
                        {markerVisible && predictedPoint && (
                            <ReferenceDot
                                x={forecast.predictedMinute}
                                y={predictedPoint.actual || 0}
                                r={6}
                                fill="#fbbf24"
                                stroke="#1a1f2e"
                                strokeWidth={3}
                                ifOverflow="visible"
                            />
                        )}
                        {warmedMinutes.map((minute) => (
                            <ReferenceDot
                                key={minute}
                                x={minute}
                                y={series.find((point) => point.minute === minute)?.actual || 0}
                                r={5}
                                fill="#34d399"
                                stroke="#34d399"
                                strokeWidth={2}
                                className="warming-dot"
                                ifOverflow="visible"
                            />
                        ))}
                    </AreaChart>
                </ResponsiveContainer>
            </div>
            <div className="forecast-status">
                <span className="forecast-icon">AI</span>
                <p>{forecastMessage(forecast)}</p>
            </div>
        </section>
    );
}

function TrafficTooltip({ active, payload, label }) {
    if (!active || !payload?.length) return null;
    const value = payload.find((item) => item.dataKey === 'actual')?.value;
    return (
        <div className="chart-tooltip">
            <span>Minute {label}</span>
            <strong>{value ?? '-'} requests</strong>
        </div>
    );
}