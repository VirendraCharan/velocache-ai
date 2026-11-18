export default function TenantSelector({ tenants, value, onChange }) {
    return (
        <label className="tenant-selector">
            <span>Tenant</span>
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