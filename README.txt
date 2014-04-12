                      QZ Industries README.TXT
               Copyright (c) 2013 QZ Industries, LLC
                     QZ Print Plugin (qz-print)

                          INSTRUCTIONS

1. Extract /dist
2. Open sample.html in a web browser
3. View-Source of sample.html for usage
4. Visit code.google.com/p/jzebra or qzindustries.com for API,
   resources, examples, bug reports and feature requests.
READFIRST:
   - Premium versions of the applet will work "out of the box"
   - Free versons of the applet are self-signed and will only 
     run when qz-free.csr has been added as a "Signer CA" from
     within the Java Control Panel.
   - If clients do not recieve an update after refreshing the
     page you can force an update using Java Control Panel,
     General, View, Remove "QZ Print Plugin"
   - IIS Users need to permit JNLP file extensions before the
     applet will work.
   - Apache + HTTPS users may need ServerName mydomain.com, 
     ServerAlias www.mydomain.com added to apache config
     section <VirtualHost mydomain.com:443> for HTTP/SSL to
     work properly.
   - OSX Mavericks users will need to change Safari, 
     Preferences, Security, Manage Websites, Allow Always
     in order to print using Java.

Thank you for using qz-print!

Sincerely,

-QZ Industries, LLC