import {
    CartesianGrid,
    Line,
    LineChart,
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
    const markerVisible =
        forecast.status === 'ok' &&
        forecast.predictedMinute &&
        minutesOnAxis.has(forecast.predictedMinute);
    const warmedMinutes = [...new Set(events.map((event) => event.minute))].filter((minute) =>
        minutesOnAxis.has(minute)
    );

    return (
        <div>
            <div style={{ width: '100%', height: 320 }}>
                <ResponsiveContainer>
                    <LineChart data={series} margin={{ top: 10, right: 20, bottom: 0, left: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="minute" />
                        <YAxis allowDecimals={false} />
                        <Tooltip />
                        <Line
                            type="linear"
                            dataKey="actual"
                            name="Requests"
                            stroke="#a78bfa"
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
                                label={{ value: 'Predicted rush', position: 'insideTopRight', fill: '#fbbf24' }}
                            />
                        )}
                        {warmedMinutes.map((minute) => (
                            <ReferenceDot
                                key={minute}
                                x={minute}
                                y={0}
                                r={6}
                                fill="#2dd4bf"
                                stroke="none"
                                ifOverflow="visible"
                            />
                        ))}
                    </LineChart>
                </ResponsiveContainer>
            </div>
            <p>{forecastMessage(forecast)}</p>
            <p style={{ opacity: 0.7, fontSize: 14 }}>Teal dots: minutes when the warmer pre-filled the cache</p>
        </div>
    );
}