/**
 * Echoes the signed message and exits
 */
public void SignMessage(String message)
{
     // #########################################################
     // #             WARNING   WARNING   WARNING               #
     // #########################################################
     // #                                                       #
     // # This file is intended for demonstration purposes      #
     // # only.                                                 #
     // #                                                       #
     // # It is the SOLE responsibility of YOU, the programmer  #
     // # to prevent against unauthorized access to any signing #
     // # functions.                                            #
     // #                                                       #
     // # Organizations that do not protect against un-         #
     // # authorized signing will be black-listed to prevent    #
     // # software piracy.                                      #
     // #                                                       #
     // # -QZ Industries, LLC                                   #
     // #                                                       #
     // #########################################################

    // Sample key.  Replace with one used for CSR generation
	var KEY = "private-key.pem";
	var PASS = "S3cur3P@ssw0rd";

	var pem = System.IO.File.ReadAllText( KEY );
	var cert = new X509Certificate2( GetBytesFromPEM( pem, "RSA PRIVATE", PASS );
	RSACryptoServiceProvider csp = (RSACryptoServiceProvider)cert.PrivateKey;

	byte[] data = new ASCIIEncoding().GetBytes(message);

	byte[] hash = new SHA1Managed().ComputeHash(data);

	Response.ContentType = "text/plain";
	Response.Write(csp.SignHash(hash, CryptoConfig.MapNameToOID("SHA1")));  
	Environment.Exit(0)
}

/**
 * Get PEM certificate data using C#
 */
private byte[] GetBytesFromPEM( string pemString, string section )
{
    var header = String.Format("-----BEGIN {0}-----", section);
    var footer = String.Format("-----END {0}-----", section);
	
    var start = pemString.IndexOf(header, StringComparison.Ordinal) + header.Length;
    var end = pemString.IndexOf(footer, start, StringComparison.Ordinal) - start;
    
	return start < 0 || end < 0 ? null : 
		Convert.FromBase64String( pemString.Substring( start, end ) );
}
