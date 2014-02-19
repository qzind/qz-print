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
		findPrinter($(this).val());
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
function qzReady() {
	
	// Setup our global qz object
	window["qz"] = document.getElementById('qz');
	
	if (qz) {
		try {
			// Set the progress bar to 66% / "Finding Printers" / Yellow
			$('#progressbar').progressbar({
				value: 66
			});
			$('.progress-label').text("Finding Printers...");
			$('.ui-progressbar-value').css('backgroundColor', '#FF0');
			
			document.title = document.title + " " + qz.getVersion();
		} catch(err) { // LiveConnect error, display a detailed meesage
		
			// Set the progress bar to 100% / "Error" / Red
			$('#progressbar').progressbar({
				value: 100
			});
			$('.progress-label').text("Error");
			$('.ui-progressbar-value').css('backgroundColor', '#F00');
			
			document.getElementById("container").style.background = "#F5A9A9";
			alert("ERROR:  \nThe applet did not load correctly.  Communication to the " + 
				"applet has failed, likely caused by Java Security Settings.  \n\n" + 
				"CAUSE:  \nJava 7 update 25 and higher block LiveConnect calls " + 
				"once Oracle has marked that version as outdated, which " + 
				"is likely the cause.  \n\nSOLUTION:  \n  1. Update Java to the latest " + 
				"Java version \n          (or)\n  2. Lower the security " + 
				"settings from the Java Control Panel.");
		}
		
		// set updateQueueInfo to trigger every second
		queueUpdateInterval = setInterval(updateQueueInfo, 1000);
		
		// Get the CSV listing of attached printers
		var printers = qz.getPrinters().split(',');
		
		// Set the printer select with the current list of printers
		var ps_html = '<option value="">Default Printer</option>';
		for(var i = 0; i < printers.length; i++) {
			ps_html += '<option value="' + printers[i] + '">' + printers[i] + '</option>';
		}
		$('#printer_select').html(ps_html);
		
		// Set the progress bar to 100% / "Ready" / Green
		$('#progressbar').progressbar({
			value: 100
		});
		$('.progress-label').text("Ready");
		$('.ui-progressbar-value').css('backgroundColor', '#0F0');
		
		// Use the default printer automatically
		findPrinter();
		
  }
}

/**
* Returns whether or not the applet is not ready to print.
* Displays an alert if not ready.
**/
function notReady() {
	// If applet is not loaded, display an error.
	if (!qz) {
		alert('Error:\n\n\tApplet is not loaded!');
		return true;
	} 
	// If a printer hasn't been selected, display a message.
	else if (!qz.getPrinter()) {
		alert('Please select a printer first by using the "Detect Printer" button.');
		return true;
	}
	return false;
}

/**
* Returns is the applet is not loaded properly
*/
function isLoaded() {
	if (!qz) {
		alert('Error:\n\n\tPrint plugin is NOT loaded!');
		return false;
	} else {
		try {
			if (!qz.isActive()) {
				alert('Error:\n\n\tPrint plugin is loaded but NOT active!');
				return false;
			}
		} catch (err) {
			alert('Error:\n\n\tPrint plugin is NOT loaded properly!');
			return false;
		}
	}
	return true;
}

/**
* Automatically gets called when "qz.print()" is finished.
**/
function qzDonePrinting() {
	// Alert error, if any
	if (qz.getException()) {
		alert('Error printing:\n\n\t' + qz.getException().getLocalizedMessage());
		qz.clearException();
		return; 
	}
	
	// Alert success message
	alert('Successfully sent print data to "' + qz.getLastPrinter() + '" queue.');
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