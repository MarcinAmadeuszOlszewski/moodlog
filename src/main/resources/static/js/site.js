document.addEventListener("DOMContentLoaded", function () {
	document.querySelectorAll("form[data-confirm]").forEach(function (form) {
		form.addEventListener("submit", function (event) {
			if (!confirm(form.getAttribute("data-confirm"))) {
				event.preventDefault();
			}
		});
	});
});
