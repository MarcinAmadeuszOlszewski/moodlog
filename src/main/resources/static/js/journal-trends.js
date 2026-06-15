document.addEventListener("DOMContentLoaded", function () {
	const dataEl = document.getElementById('trends-data');
	if (!window.Chart || !dataEl) {
		return;
	}

	const journalTrendsData = JSON.parse(dataEl.textContent);

	const chartDefinitions = [
		{
			canvasId: "seven-day-trend-chart",
			label: "Średni wynik nastroju",
			series: journalTrendsData.sevenDay
		},
		{
			canvasId: "thirty-day-trend-chart",
			label: "Średni wynik nastroju",
			series: journalTrendsData.thirtyDay
		},
		{
			canvasId: "weekly-trend-chart",
			label: "Średni wynik nastroju",
			series: journalTrendsData.weekly
		}
	];

	chartDefinitions.forEach(function (definition) {
		const canvas = document.getElementById(definition.canvasId);
		if (!canvas) {
			return;
		}

		new window.Chart(canvas, {
			type: "line",
			data: {
				labels: definition.series.labels,
				datasets: [
					{
						label: definition.label,
						data: definition.series.values,
						borderColor: "#2563eb",
						backgroundColor: "rgba(37, 99, 235, 0.16)",
						fill: false,
						pointRadius: 4,
						pointHoverRadius: 5,
						spanGaps: false,
						tension: 0.25
					}
				]
			},
			options: {
				responsive: true,
				maintainAspectRatio: false,
				scales: {
					y: {
						beginAtZero: true,
						max: 100,
						ticks: {
							stepSize: 20
						}
					}
				}
			}
		});
	});
});
