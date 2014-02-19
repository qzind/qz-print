/**
* Optionally used to deploy multiple versions of the applet for mixed
* environments.  Oracle uses document.write(), which puts the applet at the
* position where deployJava.js is included and called
*/
deployQZ();

/**
* Deploys different versions of the applet depending on Java version.
* Useful for removing warning dialogs for Java 6.  This function is optional
* however, if used, should replace the <applet> method.  Needed to address 
* MANIFEST.MF TrustedLibrary=true discrepancy between JRE6 and JRE7.
*/
function deployQZ() {
	var attributes = {id: "qz", code:'qz.PrintApplet.class', 
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