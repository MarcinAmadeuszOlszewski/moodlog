document.addEventListener("DOMContentLoaded", function () {
	var el = document.getElementById('timezone');
	if (el) {
		el.value = (typeof Intl !== 'undefined' && Intl.DateTimeFormat)
			? (Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Warsaw')
			: 'Europe/Warsaw';
	}
});
