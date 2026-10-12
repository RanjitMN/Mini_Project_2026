// Village Energy Coordinator - Frontend Application Logic

let energyChart = null;
let priorityChart = null;
let currentDbTable = 'simulation_results';
let currentSimulationData = null;

document.addEventListener('DOMContentLoaded', async () => {
    initHourJumpSelect();
    initCharts();
    checkDbStatus();
    await loadSimulationData();
    if (!currentSimulationData || !currentSimulationData.summary || currentSimulationData.summary.simulatedHours === 0) {
        await loadPreset('standard');
    }
});

// --- Theme Toggle ---
function toggleTheme() {
    const html = document.documentElement;
    const currentTheme = html.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    html.setAttribute('data-theme', newTheme);
    localStorage.setItem('vec-theme', newTheme);

    const icon = document.getElementById('themeIcon');
    if (newTheme === 'dark') {
        icon.className = 'fa-solid fa-moon';
    } else {
        icon.className = 'fa-solid fa-sun';
    }

    if (energyChart && priorityChart) {
        updateChartTheme();
    }
}

// Restore saved theme
const savedTheme = localStorage.getItem('vec-theme');
if (savedTheme) {
    document.documentElement.setAttribute('data-theme', savedTheme);
    const icon = document.getElementById('themeIcon');
    if (icon) {
        icon.className = savedTheme === 'dark' ? 'fa-solid fa-moon' : 'fa-solid fa-sun';
    }
}

// --- Initialize Quick Jump Select ---
function initHourJumpSelect() {
    const select = document.getElementById('hourJumpSelect');
    select.innerHTML = '<option value="">Select hour...</option>';
    for (let h = 0; h < 24; h++) {
        const opt = document.createElement('option');
        opt.value = h;
        opt.textContent = `Hour ${h < 10 ? '0' + h : h}:00`;
        select.appendChild(opt);
    }
}

function jumpToHour(hour) {
    if (hour === '') return;
    const card = document.getElementById(`hour-card-${hour}`);
    if (card) {
        card.scrollIntoView({ behavior: 'smooth', block: 'center' });
        card.style.borderColor = 'var(--color-brand)';
        setTimeout(() => {
            card.style.borderColor = '';
        }, 1500);
    }
}

// --- Initialize Chart.js ---
function initCharts() {
    const ctxEnergy = document.getElementById('energyProfileChart').getContext('2d');
    const hoursLabels = Array.from({ length: 24 }, (_, i) => `${i < 10 ? '0' + i : i}:00`);

    energyChart = new Chart(ctxEnergy, {
        type: 'bar',
        data: {
            labels: hoursLabels,
            datasets: [
                {
                    type: 'line',
                    label: 'Available Generation (kWh)',
                    data: new Array(24).fill(0),
                    borderColor: '#f59e0b',
                    backgroundColor: 'rgba(245, 158, 11, 0.1)',
                    borderWidth: 3,
                    tension: 0.35,
                    fill: true,
                    pointRadius: 4,
                    pointBackgroundColor: '#fbbf24',
                    order: 1
                },
                {
                    label: 'Allocated (Served) (kWh)',
                    data: new Array(24).fill(0),
                    backgroundColor: '#10b981',
                    borderRadius: 4,
                    order: 2
                },
                {
                    label: 'Unmet Demand (kWh)',
                    data: new Array(24).fill(0),
                    backgroundColor: '#ef4444',
                    borderRadius: 4,
                    order: 3
                },
                {
                    label: 'Curtailed Surplus (kWh)',
                    data: new Array(24).fill(0),
                    backgroundColor: '#06b6d4',
                    borderRadius: 4,
                    order: 4
                },
                {
                    type: 'line',
                    label: 'Battery Stored SoC (kWh)',
                    data: new Array(24).fill(0),
                    borderColor: '#a855f7',
                    backgroundColor: 'rgba(168, 85, 247, 0.05)',
                    borderWidth: 2.5,
                    borderDash: [6, 4],
                    pointRadius: 3,
                    pointBackgroundColor: '#c084fc',
                    tension: 0.35,
                    fill: false,
                    order: 0
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    backgroundColor: '#1e293b',
                    titleFont: { family: 'Outfit', size: 13 },
                    bodyFont: { family: 'JetBrains Mono', size: 12 },
                    padding: 12,
                    cornerRadius: 8,
                    borderColor: 'rgba(255, 255, 255, 0.1)',
                    borderWidth: 1
                }
            },
            scales: {
                x: {
                    grid: { color: 'rgba(255, 255, 255, 0.05)' },
                    ticks: { color: '#94a3b8', font: { family: 'JetBrains Mono', size: 11 } }
                },
                y: {
                    title: { display: true, text: 'Energy (kWh)', color: '#94a3b8' },
                    grid: { color: 'rgba(255, 255, 255, 0.05)' },
                    ticks: { color: '#94a3b8', font: { family: 'JetBrains Mono', size: 11 } }
                }
            }
        }
    });

    // Priority Doughnut Chart
    const ctxPriority = document.getElementById('priorityDoughnutChart').getContext('2d');
    priorityChart = new Chart(ctxPriority, {
        type: 'doughnut',
        data: {
            labels: ['Critical (L3)', 'Essential (L2)', 'Deferrable (L1)'],
            datasets: [{
                data: [30, 45, 25],
                backgroundColor: ['#f43f5e', '#f97316', '#8b5cf6'],
                borderWidth: 0,
                hoverOffset: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '72%',
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#1e293b',
                    callbacks: {
                        label: (ctx) => ` ${ctx.label}: ${ctx.raw.toFixed(1)} kWh`
                    }
                }
            }
        }
    });
}

function updateChartTheme() {
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    const gridColor = isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.06)';
    const textColor = isDark ? '#94a3b8' : '#475569';

    if (energyChart) {
        energyChart.options.scales.x.grid.color = gridColor;
        energyChart.options.scales.y.grid.color = gridColor;
        energyChart.options.scales.x.ticks.color = textColor;
        energyChart.options.scales.y.ticks.color = textColor;
        energyChart.update();
    }
}

// --- Data Fetching & Sync ---
async function checkDbStatus() {
    try {
        const res = await fetch('/api/status');
        const data = await res.json();
        
        const badge = document.getElementById('dbStatusBadge');
        const nameText = document.getElementById('dbNameText');
        const dot = badge.querySelector('.db-dot');

        if (data.isConnected) {
            nameText.textContent = data.dbType;
            dot.className = data.dbType.includes('MySQL') ? 'db-dot dot-green' : 'db-dot dot-yellow';
        } else {
            nameText.textContent = 'Disconnected';
            dot.className = 'db-dot dot-red';
        }

        document.getElementById('modalDbStatus').textContent = `Active: ${data.dbType} — ${data.statusMessage}`;
        document.getElementById('countGen').textContent = data.generationRows || 0;
        document.getElementById('countLoads').textContent = data.loadRows || 0;
        document.getElementById('countRes').textContent = data.resultRows || 0;
    } catch (e) {
        console.error('Error fetching DB status:', e);
    }
}

async function loadSimulationData() {
    try {
        const res = await fetch('/api/simulation');
        const data = await res.json();
        currentSimulationData = data;

        updateKpis(data.summary);
        updateChartsWithData(data.hours);
        renderHourCards(data.hours);
        checkDbStatus();
    } catch (e) {
        console.error('Error loading simulation:', e);
        showToast('Error loading simulation state', 'error');
    }
}

function updateKpis(summary) {
    if (!summary) return;
    document.getElementById('kpiTotalGen').textContent = summary.totalGeneration.toFixed(1);
    document.getElementById('kpiTotalAlloc').textContent = summary.totalAllocated.toFixed(1);
    document.getElementById('kpiTotalUnmet').textContent = summary.totalUnmet.toFixed(1);
    document.getElementById('kpiTotalCurt').textContent = summary.totalCurtailed.toFixed(1);

    if (summary.batteryCapacity !== undefined) {
        document.getElementById('kpiBatteryCap').textContent = summary.batteryCapacity.toFixed(0);
        document.getElementById('kpiBatterySoC').textContent = summary.batterySoC.toFixed(1);
        const capBtnLabel = document.getElementById('btnBatteryCapLabel');
        if (capBtnLabel) capBtnLabel.textContent = `${summary.batteryCapacity.toFixed(0)} kWh`;
        
        const pct = summary.batteryPercent || 0;
        const pctBadge = document.getElementById('kpiBatPercentBadge');
        if (pctBadge) pctBadge.textContent = `${pct.toFixed(0)}% SoC`;
        
        const barFill = document.getElementById('kpiBatBarFill');
        if (barFill) barFill.style.width = `${Math.min(100, Math.max(0, pct))}%`;

        const hint = document.getElementById('kpiBatFlowHint');
        if (hint) {
            hint.innerHTML = `<i class="fa-solid fa-bolt"></i> +${(summary.totalBatteryCharged || 0).toFixed(1)} kWh in | -${(summary.totalBatteryDischarged || 0).toFixed(1)} kWh out`;
        }
    }

    const satEl = document.getElementById('kpiSatisfaction');
    satEl.innerHTML = `<i class="fa-solid fa-chart-pie"></i> ${summary.satisfactionRate.toFixed(1)}% demand satisfied (${summary.simulatedHours}/24 hrs simulated)`;
}

function updateChartsWithData(hours) {
    if (!hours || !energyChart) return;

    const genData = [];
    const allocData = [];
    const unmetData = [];
    const curtData = [];
    const batData = [];

    let critEnergy = 0;
    let essEnergy = 0;
    let defEnergy = 0;

    hours.forEach(h => {
        genData.push(h.generation);
        allocData.push(h.allocated);
        unmetData.push(h.unmet);
        curtData.push(h.curtailed);
        batData.push(h.batterySoC !== undefined ? h.batterySoC : 0);

        // Sum load priorities
        (h.loads || []).forEach(load => {
            const val = load.allocated > 0 ? load.allocated : load.demand;
            if (load.priority === 'CRITICAL') critEnergy += val;
            else if (load.priority === 'ESSENTIAL') essEnergy += val;
            else defEnergy += val;
        });
    });

    energyChart.data.datasets[0].data = genData;
    energyChart.data.datasets[1].data = allocData;
    energyChart.data.datasets[2].data = unmetData;
    energyChart.data.datasets[3].data = curtData;
    if (energyChart.data.datasets[4]) {
        energyChart.data.datasets[4].data = batData;
    }
    energyChart.update();

    if (priorityChart) {
        priorityChart.data.datasets[0].data = [critEnergy, essEnergy, defEnergy];
        priorityChart.update();
    }
}

function renderHourCards(hours) {
    const grid = document.getElementById('hourCardsGrid');
    grid.innerHTML = '';

    hours.forEach(h => {
        const card = document.createElement('div');
        card.className = 'hour-card';
        card.id = `hour-card-${h.hour}`;

        const hourStr = h.hour < 10 ? '0' + h.hour : h.hour;
        const totalLoadDemand = (h.loads || []).reduce((acc, l) => acc + l.demand, 0);

        // Calculate progress percentage
        let allocPct = 0;
        let curtPct = 0;
        if (h.generation > 0) {
            allocPct = Math.min(100, (h.allocated / h.generation) * 100);
            curtPct = Math.min(100, (h.curtailed / h.generation) * 100);
        }

        let loadsHtml = '';
        if (!h.loads || h.loads.length === 0) {
            loadsHtml = '<div style="color:var(--text-muted); font-size:0.8rem; font-style:italic;">No loads scheduled</div>';
        } else {
            loadsHtml = h.loads.map(l => {
                const pClass = `priority-${l.priorityLevel}`;
                const allocBadge = h.simulated 
                    ? `<span class="load-alloc-tag ${l.allocated >= l.demand ? 'text-green' : (l.allocated > 0 ? 'text-yellow' : 'text-red')}">${l.allocated.toFixed(1)} / ${l.demand.toFixed(1)} kWh</span>`
                    : `<span class="load-alloc-tag text-muted">${l.demand.toFixed(1)} kWh</span>`;

                const typeHint = l.type === 'MINIMUM_SERVICE' ? `[Min ${l.minRequired} kWh]` : '[Divisible]';

                return `
                    <div class="load-item ${pClass}">
                        <div class="load-name-wrap">
                            <span class="load-name">${escapeHtml(l.name)}</span>
                            <span class="load-sub">${l.priority} • ${typeHint}</span>
                        </div>
                        ${allocBadge}
                    </div>
                `;
            }).join('');
        }

        const simStatus = h.simulated 
            ? `<span class="badge badge-alloc"><i class="fa-solid fa-check"></i> Simulated</span>` 
            : `<span class="badge" style="background:rgba(255,255,255,0.06); color:var(--text-muted);">Pending</span>`;

        let batteryPill = '';
        if (h.simulated) {
            if (h.batteryCharged > 0.05) {
                batteryPill = `<span class="hour-battery-badge bat-charge" title="Charged ${h.batteryCharged.toFixed(1)} kWh excess solar"><i class="fa-solid fa-bolt"></i> +${h.batteryCharged.toFixed(1)} Chg</span>`;
            } else if (h.batteryDischarged > 0.05) {
                batteryPill = `<span class="hour-battery-badge bat-discharge" title="Discharged ${h.batteryDischarged.toFixed(1)} kWh from storage"><i class="fa-solid fa-battery-charging"></i> -${h.batteryDischarged.toFixed(1)} Dis</span>`;
            } else if (h.batterySoC > 0) {
                batteryPill = `<span class="hour-battery-badge" style="background:rgba(255,255,255,0.05); color:var(--text-muted);"><i class="fa-solid fa-car-battery text-purple"></i> ${h.batterySoC.toFixed(1)}</span>`;
            }
        }

        card.innerHTML = `
            <div class="hour-card-header">
                <span class="hour-title-pill">${hourStr}:00</span>
                <span class="hour-gen-badge"><i class="fa-solid fa-bolt"></i> ${h.generation.toFixed(1)} kWh Gen</span>
                ${batteryPill}
                ${simStatus}
            </div>

            <div class="allocation-progress-bar" title="Allocated: ${h.allocated.toFixed(1)} kWh | Curtailed: ${h.curtailed.toFixed(1)} kWh | Battery SoC: ${(h.batterySoC || 0).toFixed(1)} kWh">
                <div class="prog-alloc" style="width: ${allocPct}%;"></div>
                <div class="prog-curt" style="width: ${curtPct}%;"></div>
            </div>

            <div class="hour-stat-row">
                <span>Demand: <strong>${totalLoadDemand.toFixed(1)} kWh</strong></span>
                <span>Allocated: <strong>${h.allocated.toFixed(1)} kWh</strong></span>
                <span>Unmet: <strong class="text-red">${h.unmet.toFixed(1)} kWh</strong></span>
            </div>

            <div class="loads-list">
                ${loadsHtml}
            </div>

            <div class="hour-card-footer">
                <button class="btn btn-outline-sm" onclick="openHourAddLoadModal(${h.hour})">
                    <i class="fa-solid fa-plus"></i> Add Load
                </button>
                <button class="btn btn-primary btn-outline-sm" onclick="simulateHour(${h.hour})">
                    <i class="fa-solid fa-play"></i> Simulate
                </button>
            </div>
        `;

        grid.appendChild(card);
    });
}

// --- Action Handlers ---
async function loadPreset(presetName, btnElement) {
    document.querySelectorAll('.preset-group .btn-preset').forEach(b => b.classList.remove('active'));
    if (btnElement) {
        btnElement.classList.add('active');
    } else {
        const btn = document.querySelector(`.preset-group .btn-preset[data-preset="${presetName}"]`);
        if (btn) btn.classList.add('active');
    }

    showToast(`Loading scenario '${presetName}' & calculating microgrid dispatch...`, 'info');
    try {
        const res = await fetch('/api/load-preset', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ preset: presetName })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`Scenario '${presetName}' successfully simulated & saved to database!`, 'success');
            await loadSimulationData();
        } else {
            showToast(data.error || 'Failed to load preset', 'error');
        }
    } catch (e) {
        showToast('Error communicating with backend', 'error');
    }
}

async function simulateHour(hour) {
    try {
        const res = await fetch('/api/simulate-hour', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ hour: hour })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`Hour ${hour}:00 simulated and saved to DB.`, 'success');
            await loadSimulationData();
        } else {
            showToast(data.error || 'Failed to simulate hour', 'error');
        }
    } catch (e) {
        showToast('Error simulating hour', 'error');
    }
}

async function simulateAllHours() {
    showToast('Simulating all 24 hours & updating database...', 'info');
    try {
        const res = await fetch('/api/simulate-all', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });
        const data = await res.json();
        if (data.success) {
            showToast('All 24 hours simulated and recorded to database!', 'success');
            await loadSimulationData();
        } else {
            showToast(data.error || 'Failed to run full simulation', 'error');
        }
    } catch (e) {
        showToast('Error running simulation', 'error');
    }
}

async function resetSimulation() {
    if (!confirm('Are you sure you want to clear current daily simulation and database tables?')) return;
    try {
        const res = await fetch('/api/reset', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });
        const data = await res.json();
        if (data.success) {
            showToast('Simulation reset and database cleared.', 'info');
            await loadSimulationData();
        }
    } catch (e) {
        showToast('Error resetting simulation', 'error');
    }
}

// --- Modals & Forms ---
function openAddLoadModal() {
    document.getElementById('addLoadModal').classList.add('active');
}

function openHourAddLoadModal(hour) {
    document.getElementById('loadHour').value = hour;
    openAddLoadModal();
}

function openSetGenModal() {
    document.getElementById('setGenModal').classList.add('active');
}

function openBatteryModal() {
    if (currentSimulationData && currentSimulationData.summary) {
        document.getElementById('batteryCapacityInput').value = currentSimulationData.summary.batteryCapacity ?? 200;
        document.getElementById('batterySoCInput').value = currentSimulationData.summary.batterySoC ?? 40;
    }
    document.getElementById('batteryModal').classList.add('active');
}

function applyBatteryPreset(cap, soc) {
    document.getElementById('batteryCapacityInput').value = cap;
    document.getElementById('batterySoCInput').value = soc;
}

async function submitSetBattery(e) {
    e.preventDefault();
    const capacity = parseFloat(document.getElementById('batteryCapacityInput').value);
    const initialSoC = parseFloat(document.getElementById('batterySoCInput').value);

    showToast(`Configuring Battery Storage to ${capacity} kWh (SoC: ${initialSoC} kWh)...`, 'info');
    try {
        const res = await fetch('/api/set-battery', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ capacity, initialSoC })
        });
        const data = await res.json();
        if (data.success) {
            closeModal('batteryModal');
            showToast(`Battery updated! Re-simulating all 24 hours...`, 'info');
            // Re-simulate all hours to reflect new battery sizing
            await fetch('/api/simulate-all', { method: 'POST' });
            await loadSimulationData();
            showToast(`Battery dispatch complete! Check the updated 24h curve.`, 'success');
        } else {
            showToast(data.error || 'Failed to update battery', 'error');
        }
    } catch (err) {
        showToast('Error configuring battery', 'error');
    }
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
}

function toggleMinReqField(loadType) {
    const minGroup = document.getElementById('minReqGroup');
    minGroup.style.display = loadType === 'MINIMUM_SERVICE' ? 'flex' : 'none';
}

async function submitAddLoad(e) {
    e.preventDefault();
    const hour = parseInt(document.getElementById('loadHour').value, 10);
    const name = document.getElementById('loadName').value.trim();
    const type = document.getElementById('loadType').value;
    const priority = document.getElementById('loadPriority').value;
    const demand = parseFloat(document.getElementById('loadDemand').value);
    const minRequired = type === 'MINIMUM_SERVICE' ? parseFloat(document.getElementById('loadMinRequired').value || 0) : 0;

    try {
        const res = await fetch('/api/add-load', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                hour, name, type, priority, demand, minRequired
            })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`Added load "${name}" to Hour ${hour} and saved to DB.`, 'success');
            closeModal('addLoadModal');
            document.getElementById('addLoadForm').reset();
            await loadSimulationData();
        } else {
            showToast(data.error || 'Failed to add load', 'error');
        }
    } catch (err) {
        showToast('Error submitting load', 'error');
    }
}

async function submitSetGen(e) {
    e.preventDefault();
    const hour = parseInt(document.getElementById('genHour').value, 10);
    const generation = parseFloat(document.getElementById('genAmount').value);

    try {
        const res = await fetch('/api/set-generation', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ hour, generation })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`Hour ${hour} generation updated to ${generation} kWh and saved to DB.`, 'success');
            closeModal('setGenModal');
            document.getElementById('setGenForm').reset();
            await loadSimulationData();
        } else {
            showToast(data.error || 'Failed to set generation', 'error');
        }
    } catch (err) {
        showToast('Error setting generation', 'error');
    }
}

// --- Database Inspector ---
function openDbModal() {
    document.getElementById('dbModal').classList.add('active');
    loadDbTable(currentDbTable);
}

function switchDbTable(tableName, tabBtn) {
    currentDbTable = tableName;
    document.querySelectorAll('.db-tab').forEach(t => t.classList.remove('active'));
    if (tabBtn) tabBtn.classList.add('active');
    loadDbTable(tableName);
}

async function loadDbTable(table) {
    const thead = document.getElementById('dbTableHeader');
    const tbody = document.getElementById('dbTableBody');
    tbody.innerHTML = '<tr><td colspan="8" class="text-center">Querying database table...</td></tr>';

    try {
        const res = await fetch(`/api/db-records?table=${table}`);
        const data = await res.json();
        const records = data.records;

        if (!records || records.length === 0) {
            thead.innerHTML = '';
            tbody.innerHTML = `<tr><td colspan="8" class="text-center" style="padding: 2rem; color: var(--text-muted);">
                <i class="fa-solid fa-database" style="font-size: 2rem; margin-bottom: 0.5rem; display: block;"></i>
                Table <code>${table}</code> is currently empty. Run a simulation preset or add loads to generate rows.
            </td></tr>`;
            return;
        }

        // Render header
        const columns = Object.keys(records[0]);
        thead.innerHTML = `<tr>${columns.map(c => `<th>${escapeHtml(c.toUpperCase())}</th>`).join('')}</tr>`;

        // Render rows
        tbody.innerHTML = records.map(r => {
            return `<tr>${columns.map(c => `<td>${formatDbValue(c, r[c])}</td>`).join('')}</tr>`;
        }).join('');

    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center text-red">Failed to read table records: ${e.message}</td></tr>`;
    }
}

function formatDbValue(col, val) {
    if (val === null || val === undefined) return '<span style="color:var(--text-muted)">NULL</span>';
    if (typeof val === 'number') {
        return Number.isInteger(val) ? val : val.toFixed(2);
    }
    return escapeHtml(String(val));
}

async function reconnectMySQL() {
    showToast('Testing XAMPP MySQL connection on localhost:3306...', 'info');
    try {
        const res = await fetch('/api/reconnect-mysql', { method: 'POST' });
        const data = await res.json();
        if (data.success) {
            showToast(`Successfully connected to XAMPP MySQL (${data.dbType})!`, 'success');
        } else {
            showToast(`MySQL not detected. Kept on SQLite (${data.statusMessage})`, 'info');
        }
        await checkDbStatus();
        if (document.getElementById('dbModal').classList.contains('active')) {
            loadDbTable(currentDbTable);
        }
    } catch (e) {
        showToast('Error attempting MySQL reconnection', 'error');
    }
}

// --- Utilities ---
function showToast(msg, type = 'info') {
    const toast = document.getElementById('toast');
    toast.textContent = msg;
    toast.className = `toast toast-${type} show`;
    setTimeout(() => {
        toast.className = 'toast';
    }, 3500);
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>"']/g, m => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[m]);
}
