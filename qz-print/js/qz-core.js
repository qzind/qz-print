/**
* QZ-PRINT CORE
*
* A redistributable JavaScript deployment tool for the qz-print print plugin 
* providing basic cross-brower readiness features.
*
* License LGPL 2.1, QZ Industries, LLC
*/

/**
* Automatically deploys the applet to the web page using document.write(), 
* which puts the applet at the position where deployJava.js is included and
* called.
*/
deployQZ();

// Help message if Java was detected but the applet failed to load
var qzFailedMessage = "The applet did not load correctly.  Communication to " + 
   "the applet has failed, likely caused by Java Security Settings.  \n\n" + 
   "CAUSES:  \n  1. Java 7u25 and higher block LiveConnect calls once Oracle " + 
   "has marked that version as outdated \n          (or)\n  2. Java 7u51 and " + 
   "higher block self-signed applets.\n\nSOLUTIONS:  \n  1. Update Java to " + 
   "the latest Java version \n          (or)\n  2. Lower the security " + 
   "settings from the Java Control Panel.\n          (or)\n  3. Whitelist " + 
   "via Security, Exception Site List (7u51 and higher only)";

// Global variable to enable or disable alerts
var qzAlerts = true;

// Create an empty console.log, if one doesn't exist
if(!window.console){ window.console = {log: function(){} }; } 

/**
* Deploys different versions of the applet depending on Java version.
* Useful for removing warning dialogs for Java 6.  This function is optional
* however, if used, should replace the <applet> method.  Needed to address 
* MANIFEST.MF TrustedLibrary=true discrepancy between JRE6 and JRE7.
*/
function deployQZ() {
	var attributes = {id: 'qz', code:'qz.PrintApplet.class', 
		archive:'qz-print.jar', width:1, height:1};
	var parameters = {jnlp_href: 'qz-print_jnlp.jnlp', 
		cache_option:'plugin', disable_logging:'false', 
		initial_focus:'false'};
	if (deployJava.versionCheck("1.7+") == true) {}
	else if (deployJava.versionCheck("1.6+") == true) {
		attributes['archive'] = 'jre6/qz-print.jar';
		parameters['jnlp_href'] = 'jre6/qz-print_jnlp.jnlp';
	}
	deployJava.runApplet(attributes, parameters, '1.5');
}

/**
* Returns is the applet is not loaded properly
*/
function isLoaded() {
	if (!qz) {
		return qzAlert('Print plugin is NOT loaded!');
	} else {
		try {
			if (!qz.isActive()) {
				return qzAlert('Print plugin is loaded but NOT active!');
			}
		} catch (err) {
			return qzAlert('Print plugin is NOT loaded properly!');
		}
	}
	return true;
}

/**
* Toggle on/off alerts.  If no value is passed in, it will return the current
* status.
*/
function showAlerts(alertFlag) {
	if (alertFlag == null) {
		return qzAlerts;
	} else if (alertFlag) {
		return (qzAlerts = true);
	} else {
		return (qzAlerts = false);
	}
}


/**
* Returns whether he applet is NOT ready to print.
* Displays an alert if not ready and if qzAlerts is set to true.
**/
function notReady() {
	// If applet is not loaded, display an error.
	if (!qz) {
		return !qzAlert('Applet is not loaded!');
	} 
	// If a printer hasn't been selected, display a message.
	else if (!qz.getPrinter()) {
		return !qzAlert('warning', 'Please select a printer first by using the "findPrinter()" function.');
	}
	return false;
}


/**
* Automatically gets called when the <applet> is loaded and init() is called
* within the applet.  Feel free to override this by declaring a new function 
* called "qzReady" lower in your JavaScript code
**/
function qzReady() {
	// Setup our global qz object
	window['qz'] = document.getElementById('qz');
	if (qz) {
		// Attempt to make a LiveConnect call
		try {
			qz.getVersion();
			blindCall('qzLoaded(true)');
		}
		// LiveConnect error, display a detailed meesage
		catch(err) {
			qzAlert(qzFailedMessage);
			blindCall('qzLoaded(false)');
	  }
  }
}

/**
* Blindly calls a the specified JavaScript function without throwing an
* exception to the browser.  For security reasons, only function starting 
* with "qz" can be called this way.
*/
function blindCall(funcName) {
	// Make sure the function starts with "qz"
	if (funcName.lastIndexOf('qz', 0) === 0) {
		try { return eval(funcName + ';'); }
		catch (err) {}
	}
	return false; 
}

/**
* Automatically gets called when "qz.print()" is finished.
* Feel free to override this by declaring a new function called "qzDonePrinting"
* lower in your JavaScript code
**/
function qzDonePrinting() {
	// Alert error, if any
	if (qz.getException()) {
		qzAlert(qz.getException().getLocalizedMessage());
		qz.clearException();
		return; 
	}
	
	// Alert success message
	qzAlert('info', 'Successfully sent print data to "' + qz.getLastPrinter() + '" queue.');
}

/**
* A wrapper for the alert() function so that alerts can be suppressed without
* editing this file.
*/
function qzAlert(type, msg) {
	if (qzAlerts) { alert((type ? type.toUpperCase() : 'ERROR') + ':\n\n\t' + msg); }
	else { console.log(msg); }
	return false;
}

/**
* Convenience method for qzAlert(null, msg);
*/
function qzAlert(msg) {
	return qzAlert(null, msg);
}
