/**
* Automatically gets called when document is finished loading.
* We can use this to initialize our interface elements
*/
$(document).ready(function() {
	// Set up the tab elements
	$('#tabs').tabs();
	
	// Set the progress bar to 33% / "Loading Applet" / Red
	$('#progressbar').progressbar({
		value: 33
	});
	$('.progress-label').text("Loading Applet...");
	$('.ui-progressbar-value').css('backgroundColor', '#F00');
	
		
	$('#printer_select').on('change', function() {
		var selected = $(this).find('option:selected');
		if (selected.data('index')) {
			qz.setPrinter(selected.data('index'));
		}
	});
	
	/**
	* Print functions to respond to button presses. Grab the value of the radio select and call the appropriate sample print function.
	* Sample print functions are defined in qz-sample.js
	*/
	
	$('#label_print_button').click(function(event) {
		
		var format = $('input[name=label_print_format]:checked').val();
		
		console.log("Print Label! Format: " + format);
		
		switch(format) {
			case 'EPL':
				printEPL();
				break;
			case 'ZPL':
				printZPL();
				break;
			case 'HTML5':
				printHTML();
				break;
			case 'IMAGE':
				printImage();
				break;
		}

		event.preventDefault();
		
	});
	
});

/**
* Automatically gets called when applet has loaded.
*/
function qzLoaded(success) {
	if (success) {
		// Set the progress bar to 66% / "Finding Printers" / Yellow
		$('#progressbar').progressbar({ value: 66 });
		$('.progress-label').text("Finding Printers...");
		$('.ui-progressbar-value').css('backgroundColor', '#FF0');
		document.title = document.title + " " + qz.getVersion();
	} else {
		// Set the progress bar to 100% / "Error" / Red
		$('#progressbar').progressbar({ value: 100 });
		$('.progress-label').text("Error");
		$('.ui-progressbar-value').css('backgroundColor', '#F00');
		return false;
	}

	// set updateQueueInfo to trigger every second
	queueUpdateInterval = setInterval(updateQueueInfo, 1000);

	
	qz.findPrinter();
}

function qzDoneFinding() {
    // Get the CSV listing of attached printers
	var printers = qz.getPrinters().split(',');
    // Set the printer select with the current list of printers	
	$('#printer_select').find('option').remove();
	$('#printer_select').removeAttr('disabled');
	
	//var ps_html = '<option value="" data-index="-1">Default Printer</option>';
	$('<option>').val('').text('Default Printer').data('index', -1).appendTo('#printer_select');
	for (var i in printers) {
		var sel = (qz.getPrinter() == printers[i]);
		$('<option>').val(printers[i]).text(printers[i]).data('index', i).prop('selected', sel).appendTo('#printer_select');
	}
	//$('#printer_select').html(ps_html);

	// Set the progress bar to 100% / "Ready" / Green
	$('#progressbar').progressbar({ value: 100 });
	$('.progress-label').text("Ready");
	$('.ui-progressbar-value').css('backgroundColor', '#0F0');

	// Use the default printer automatically
	/*findPrinter();*/
}

/***************************************************************************
* Sample function to demonstrate getting and displaying spool information
***************************************************************************/ 
function updateQueueInfo() {
	var queueInfo;
	
	if (qz) {
		queueJSON = qz.getQueueInfo();
		queueInfo = $.parseJSON(queueJSON);
		queueHtml = "<table><thead><tr><th>ID</th><th>State</th><th>Copies</th><th>View Job Data (Raw Only)</th></tr></thead><tbody>";
		
		for(var i=0; i < queueInfo.length; i++) {
			queueHtml += "<tr>";
			queueHtml += "<td>" + queueInfo[i].id + "</td>";
			var jobState = queueInfo[i].state.replace("STATE_", "");
			jobState = jobState.charAt(0) + jobState.slice(1).toLowerCase();
			queueHtml += "<td>" + jobState + "</td>";
			queueHtml += "<td>" + queueInfo[i].copies + "</td>";
			queueHtml += "<td><a href='javascript:console.log(qz.getJobInfo(" + queueInfo[i].id + "))'>View Job Data</a></td>";
			queueHtml += "</tr>";
		}
		
		queueHtml += "</tbody></table>";
	
		$('#queue-info').html(queueHtml);
	}
	else {
		//queueInfo = "Error: Applet does not appear to be loaded!";
		queueInfo = "";
	}

}

/***************************************************************************
* Prototype function for finding the closest match to a printer name.
* Usage:
*    qz.findPrinter('zebra');
***************************************************************************/
function findPrinter() {
	
	// Set to blank by default. This will search for the default printer
	var printer_name = '';
	
	// Check for optional name argument
	if(arguments[0] != null) {
		printer_name = arguments[0];
	}
	
	if (qz) {
		// Searches for locally installed printer with specified name
		qz.findPrinter(printer_name);
		var printer = qz.getPrinter();
		
		// Alert the printer name to user
		if(printer_name != '') {
			alert(printer !== null ? 'Printer found: "' + printer + 
				'" after searching for "' + printer_name + '"' : 'Printer "' + 
				printer_name + '" not found.');
		}
		else {
			alert(printer !== null ? 'Printer found: "' + printer + 
				'" after searching for the default printer.' : 'Default printer not found.');
			
			// Set the printer select box to the printer that was found
			$('#printer_select').val(printer.toString());
		}

	} else {
		// If applet is not loaded, display an error.
		return alert('Error:\n\n\tApplet is not loaded!');
	}
	
}