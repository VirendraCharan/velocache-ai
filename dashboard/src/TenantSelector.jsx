export default function TenantSelector({ tenants, value, onChange }) {
    return (
        <label>
            Tenant{' '}
            <select value={value} onChange={(e) => onChange(e.target.value)}>
                {tenants.map((tenant) => (
                    <option key={tenant} value={tenant}>
                        {tenant}
                    </option>
                ))}
            </select>
        </label>
    );
}