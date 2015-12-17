'use strict';

/**
 * @overview QZ Tray Connector
 *
 * @requires module:jQuery
 *     Makes javascript usable
 * @requires module:RSVP
 *     Provides Promises/A+ functionality for API calls
 */


/* * * * * * * *
 Design Considerations
 ---------------------
 > ajax - everything uses it
 > signing - allow pre-signed content to be passed
 > encoding - ensure data is properly escaped || using POST method ?
 > ???

 * * * * * * * * *

 To be done
 ----------
 > examples in docs
 * * * * * * * */


///// POLYFILLS /////

if (!Array.isArray) {
    Array.isArray = function(arg) {
        return Object.prototype.toString.call(arg) === '[object Array]';
    };
}


///// PRIVATE METHODS /////

var _qz = {
    DEBUG: true,
    log: {
        //Debugging messages
        trace: function() { if (_qz.DEBUG) { console.log.apply(console, arguments); } },
        //General messages
        info: function() { console.info.apply(console, arguments); },
        //Debugging errors
        warn: function() { if (_qz.DEBUG) { console.warn.apply(console, arguments); } },
        //General errors
        error: function() { console.error.apply(console, arguments); }
    },

    connection: null,
    connectConfig: {
        host: "localhost",
        usingSecure: true,
        protocol: {
            secure: "wss://",
            insecure: "ws://"
        },
        port: {
            secure: [8181, 8282, 8383, 8484],
            insecure: [8182, 8283, 8384, 8485],
            usingIndex: 0
        },
        keepAlive: 60
    },

    //stream types (PrintSocketClient.StreamType)
    streams: {
        serial: 'SERIAL',
        usb: 'USB'
    },

    //loop through possible ports to open connection, sets web socket calls that will settle the promise
    findConnection: function(config, resolve, reject) {
        var address;
        if (config.usingSecure) {
            address = config.protocol.secure + config.host + ":" + config.port.secure[config.port.usingIndex];
        } else {
            address = config.protocol.insecure + config.host + ":" + config.port.insecure[config.port.usingIndex];
        }

        try {
            _qz.connection = new WebSocket(address);
        }
        catch(err) {
            _qz.log.error(err);
        }

        if (_qz.connection != null) {
            _qz.connection.established = false;

            _qz.connection.onopen = function(evt) {
                _qz.log.trace(evt);
                _qz.log.info("Established connection with QZ Tray on " + address);

                _qz.setupWebsocket();

                if (config.keepAlive > 0) {
                    var interval = window.setInterval(function() {
                        if (!qz.isActive()) {
                            clearInterval(interval);
                            return;
                        }

                        _qz.connection.send("ping");
                    }, config.keepAlive * 1000);
                }

                resolve();
            };

            _qz.connection.onclose = function() {
                // Safari compatibility fix to raise error event
                if (navigator.userAgent.indexOf('Safari') != -1 && navigator.userAgent.indexOf('Chrome') == -1) {
                    _qz.connection.onerror();
                }
            };

            _qz.connection.onerror = function(evt) {
                _qz.log.trace(evt);

                config.port.usingIndex++;

                if ((config.usingSecure && config.port.usingIndex >= config.port.secure.length)
                    || (!config.usingSecure && config.port.usingIndex >= config.port.insecure.length)) {
                    //give up, all hope is lost
                    reject(new Error("Unable to establish connection with QZ"));
                    return;
                }

                // recursive call until connection established or all ports are exhausted
                _qz.findConnection(config, resolve, reject);
            };
        } else {
            reject(new Error("Unable to establish connection with QZ"));
        }
    },

    //finish setting calls on success, sets web socket calls that won't settle the promise
    setupWebsocket: function() {
        _qz.connection.established = true;

        //called when an open connection is closed
        _qz.connection.onclose = function(evt) {
            _qz.log.trace(evt);
            _qz.log.info("Closed connection with QZ Tray");

            //if this is set, then an explicit close call was made
            if (_qz.connection.promise != undefined) {
                _qz.connection.promise.resolve();
            }

            _qz.callClose(evt);
            _qz.connection = null;
        };

        //called for any errors with an open connection
        _qz.connection.onerror = function(evt) {
            _qz.callError(evt);
        };

        //send objects to qz
        _qz.connection.sendData = function(obj) {
            if (obj.timestamp == undefined) {
                obj.timestamp = Date.now();
            }
            if (obj.promise != undefined) {
                obj.uid = _qz.newUID();
                _qz.pendingCalls[obj.uid] = obj.promise;
            }

            try {
                if (obj.call != undefined && obj.signature == undefined) {
                    var signObj = {
                        call: obj.call,
                        params: obj.params,
                        timestamp: obj.timestamp
                    };

                    _qz.callSign(JSON.stringify(signObj)).then(function(signature) {
                        obj.signature = signature;
                        _qz.signContent = undefined;

                        //TODO - ensure prototype does not mess with our stringify
                        _qz.connection.send(JSON.stringify(obj));
                    });
                } else {
                    //called for pre-signed content and (unsigned) setup calls
                    _qz.connection.send(JSON.stringify(obj));
                }
            }
            catch(err) {
                _qz.log.error(err);

                if (obj.promise != undefined) {
                    obj.promise.reject(err);
                    delete _qz.pendingCalls[obj.uid];
                }
            }
        };

        //receive message from qz
        _qz.connection.onmessage = function(evt) {
            var returned = $.parseJSON(evt.data);

            if (returned.uid == null) {
                if (returned.type == null) {
                    _qz.log.warn("Response is incorrectly formatted", returned);
                } else {
                    if (returned.type == _qz.streams.serial) {
                        _qz.callSerial(returned.key, returned.data)
                    } else if (returned.type == _qz.streams.usb) {
                        //TODO - usb callback
                    } else {
                        _qz.log.warn("Cannot determine stream type for callback", returned);
                    }
                }

                return;
            }

            var promise = _qz.pendingCalls[returned.uid];
            if (promise == undefined) {
                _qz.log.warn('No promise found for returned result');
            } else {
                if (returned.error != undefined) {
                    promise.reject(new Error(returned.error));
                } else {
                    promise.resolve(returned.result);
                }
            }

            delete _qz.pendingCalls[returned.uid];
        };


        //send up the certificate before making any calls
        //also gives the user a chance to deny the connection
        _qz.callCert().then(function(cert) {
            var msg = { certificate: cert };
            _qz.connection.sendData(msg);
        });
    },

    pendingCalls: {}, //library of promises waiting response, id -> promise
    newUID: function() {
        var len = 6;
        return (new Array(len + 1).join("0") + (Math.random() * Math.pow(36, len) << 0).toString(36)).slice(-len)
    },


    defaultConfig: {
        color: true,
        copies: 1,
        dpi: 72,
        duplex: false,
        margins: 0,
        orientation: null,
        paperThickness: null,
        printerTray: null,
        rotation: 0,
        size: null,

        altPrinting: false,
        encoding: null,
        endOfDoc: null,
        language: null,
        perSpool: 1
    },


    errorCallbacks: [],
    callError: function(evt) {
        if (Array.isArray(_qz.errorCallbacks)) {
            for(var i = 0; i < _qz.errorCallbacks.length; i++) {
                _qz.errorCallbacks[i](evt);
            }
        } else {
            _qz.errorCallbacks(evt);
        }
    },

    closedCallbacks: [],
    callClose: function(evt) {
        if (Array.isArray(_qz.closedCallbacks)) {
            for(var i = 0; i < _qz.closedCallbacks.length; i++) {
                _qz.closedCallbacks[i](evt);
            }
        } else {
            _qz.closedCallbacks(evt);
        }
    },

    serialCallbacks: [],
    callSerial: function(port, output) {
        if (Array.isArray(_qz.serialCallbacks)) {
            for(var i = 0; i < _qz.serialCallbacks.length; i++) {
                _qz.serialCallbacks[i](port, output);
            }
        } else {
            _qz.serialCallbacks(port, output);
        }
    },

    certPromise: function(resolve, reject) { reject("Undefined"); },
    callCert: function() {
        return new RSVP.Promise(_qz.certPromise);
    },

    signContent: undefined,
    signaturePromise: function(resolve) { resolve(""); },
    callSign: function(toSign) {
        _qz.signContent = toSign;
        return new RSVP.Promise(_qz.signaturePromise);
    }
};


//our Config "class"
//TODO - docs
function Config(printer, opts) {
    this.setPrinter = function(newPrinter) {
        if (typeof newPrinter === 'string') {
            newPrinter = { name: newPrinter };
        }

        this.printer = newPrinter;
    };
    this.getPrinter = function() {
        return this.printer;
    };

    this.reconfigure = function(newOpts) {
        $.extend(true, this.config, newOpts);
    };
    this.getOptions = function() {
        return this.config;
    };

    this.setPrinter(printer);
    this.config = opts;
}
Config.prototype.print = function(data) {
    qz.print(this, data);
};


///// PUBLIC METHODS /////

/** @namespace */
window.qz = {

    /** Calls related specifically to the web socket connection. */
    websocket: {

        /**
         * Call to setup connection with QZ Tray on user's system.
         *
         * @param {Object} [options] Configuration options for the web socket connection.
         *  @param {string} [options.host='localhost'] Host running the QZ Tray software.
         *  @param {boolean} [options.usingSecure=true] If the web socket should try to use secure ports for connecting.
         *  @param {number} [options.keepAlive=60] Seconds between keep-alive pings to keep connection open. Set to 0 to disable.
         *
         * @returns {Promise<null|Error>}
         */
        connect: function(options) {
            return new RSVP.Promise(function(resolve, reject) {
                if (qz.isActive()) {
                    reject(new Error("An open connection with QZ Tray already exists"));
                    return;
                }

                // Old standard of WebSocket used const CLOSED as 2, new standards use const CLOSED as 3, we need the newer standard for jetty
                if (!"WebSocket" in window || WebSocket.CLOSED == null || WebSocket.CLOSED == 2) {
                    reject(new Error("Web Sockets are not supported by this browser"));
                    return;
                }

                //disable secure ports if page is not secure
                if (location.protocol !== 'https:') {
                    if (options == undefined) { options = {}; }
                    options.usingSecure = false;
                }

                var config = $.extend(true, {}, _qz.connectConfig, options);
                _qz.findConnection(config, resolve, reject);
            });
        },

        /**
         * Stop any active connection with QZ Tray.
         *
         * @returns {Promise<null|Error>}
         */
        disconnect: function() {
            return new RSVP.Promise(function(resolve, reject) {
                if (qz.isActive()) {
                    _qz.connection.close();
                    _qz.connection.promise = { resolve: resolve, reject: reject };
                } else {
                    reject(new Error("No open connection with QZ Tray"))
                }
            });
        },

        /**
         * List of functions called for any connections errors outside of an API call.<p/>
         * Also called if {@link websocket#connect} fails to connect.
         *
         * @param {Function|Array<Function>} calls Single or array of <code>Function({Event} event)</code> calls.
         */
        setErrorCallbacks: function(calls) {
            _qz.errorCallbacks = calls;
        },

        /**
         * List of functions called for any connection closing event outside of an API call.<p/>
         * Also called when {@link websocket#disconnect} is called.
         *
         * @param {Function|Array<Function>} calls Single or array of <code>Function({Event} event)</code> calls.
         */
        setClosedCallbacks: function(calls) {
            _qz.closedCallbacks = calls;
        },

        /**
         * @returns {Promise<Object<{ipAddress: String, macAddress: String}>|Error>} Connected system's network information.
         */
        getNetworkInfo: function() {
            return new RSVP.Promise(function(resolve, reject) {
                var msg = {
                    call: 'websocket.getNetworkInfo',
                    promise: {
                        resolve: resolve, reject: reject
                    }
                };

                _qz.connection.sendData(msg);
            });
        }

    },


    /**
     * Check connection status. Active connection is necessary for other calls to run.
     *
     * @returns {boolean} If there is an active connection with QZ Tray.
     *
     * @see connect
     */
    isActive: function() {
        return _qz.connection != null && _qz.connection.established;
    },


    /** Calls related to getting printer information from the connection. */
    printers: {
        /**
         * @returns {Promise<string|Error>} Name of the connected system's default printer.
         */
        getDefault: function() {
            return new RSVP.Promise(function(resolve, reject) {
                var msg = {
                    call: 'printers.getDefault',
                    promise: {
                        resolve: resolve, reject: reject
                    }
                };

                _qz.connection.sendData(msg);
            });
        },

        /**
         * @param {string} [query] Search for a specific printer. All printers are returned if not provided.
         *
         * @returns {Promise<Array<string>|string|Error>} The matched printer name if `query` is provided.
         *                                                Otherwise an array of printers found on the connected system.
         */
        find: function(query) {
            return new RSVP.Promise(function(resolve, reject) {
                var msg = {
                    call: 'printers.find',
                    promise: {
                        resolve: resolve, reject: reject
                    },
                    params: {
                        query: query
                    }
                };

                _qz.connection.sendData(msg);
            });
        }
    },

    /** Calls related to setting up new printer configurations. */
    configs: {
        /**
         * Default options used by new configs if not overridden.
         * Setting a value to NULL will use the printer's default options.
         * Updating these will not update the options on any created config.
         *
         * @param {Object} options Default options used by printer configs if not overridden.
         *
         *  @param {string} [options.colorType='color'] Valid values  <code>[color|greyscale|blackwhite]</code>
         *  @param {number} [options.copies=1] Number of copies to be printed.
         *  @param {number} [options.density=72] Pixel density (DPI, DPMM, or DPCM depending on  <code>[options.units]</code>).
         *  @param {boolean} [options.duplex=false] Double sided printing
         *  @param {Object|number} [options.margins=0] If just a number is provided, it is used as the margin for all sides.
         *   @param {number} [options.margins.top=0]
         *   @param {number} [options.margins.right=0]
         *   @param {number} [options.margins.bottom=0]
         *   @param {number} [options.margins.left=0]
         *  @param {string} [options.orientation=null] Valid values  <code>[portrait|landscape|reverse-landscape]</code>
         *  @param {number} [options.paperThickness=null]
         *  @param {string} [options.printerTray=null] //TODO - string?
         *  @param {number} [options.rotation=0] Image rotation in degrees.
         *  @param {Object} [options.size=null] Paper size.
         *   @param {number} [options.size.width=null] Page width.
         *   @param {number} [options.size.height=null] Page height.
         *   @param {boolean} [options.size.scaleImage=false] Scales image to page size, keeping ratio.
         *  @param {string} [options.units] Page units, applies to paper size, margins, and density. Valid value  <code>[in|cm|mm]</code>
         *
         *  @param {boolean} [options.altPrinting=false]
         *  @param {string} [options.encoding=null] Character set
         *  @param {string} [options.endOfDoc=null]
         *  @param {string} [options.language=null] Printer language
         *  @param {number} [options.perSpool=1] Number of pages per spool.
         */
        setDefaults: function(options) {
            $.extend(true, _qz.defaultConfig, options);
        },

        /**
         * Creates new printer config to be used in printing.
         *
         * @param {string|object} printer Name of printer. Use object type to specify printing to file or host.
         *  @param {string} [printer.name] Name of printer to send printing.
         *  @param {string} [printer.file] Name of file to send printing.
         *  @param {string} [printer.host] IP address or host name to send printing.
         *  @param {string} [printer.port] Port used by &lt;printer.host>.
         * @param {Object} [options] Override any of the default options for this config only.
         *
         * @returns {Config} The new config.
         *
         * @see config.setDefaults
         */
        create: function(printer, options) {
            var myOpts = $.extend(true, {}, _qz.defaultConfig, options);
            return new Config(printer, myOpts);
        }
    },


    /**
     * Send data to selected config for printing.
     * The promise for this method will resolve when the document has been sent to the printer. Actual printing may not be complete.
     *
     * Optionally, print requests can be pre-signed:
     * Signed content consists of a JSON object string containing no spacing,
     * following the format of the "call" and "params" keys in the API call, with the addition of a "timestamp" key in milliseconds
     * ex. <code>'{"call":"<callName>","params":{...},"timestamp":1450000000}'</code>
     *
     * @param {Object<Config>} config Previously created config object.
     * @param {Array<Object|string>} data Array of data being sent to the printer. String values are interpreted the same as the default <code>[raw]</code> object value.
     *  @param {string} data.data
     *  @param {string} data.type Valid values <code>[html|image|pdf|raw]</code>
     *  @param {string} [data.format='auto'] Format of data provided. Generally only needed for raw printing<p/>
     *      The <code>[auto]</code> format is valid for all data types.<p/>
     *      For <code>[html]</code> types, valid formats are <code>[file|plain]</code>.<p/>
     *      For <code>[image]</code> types, valid formats are <code>[base64|file|visual]</code>.<p/>
     *      For <code>[pdf]</code> types, valid format is <code>[file]</code>.<p/>
     *      For <code>[raw]</code> types, valid formats are <code>[base64|file|visual|plain|hex|xml]</code>, use of <code>[auto]</code> assumes <code>[plain]</code>.
     *  @param {Object} [data.options]
     *   @param {number} [data.options.x] Used only with raw printing <code>[visual]</code> type. The X position of the image.
     *   @param {number} [data.options.y] Used only with raw printing <code>[visual]</code> type. The Y position of the image.
     *   @param {string|number} [data.options.dotDensity] Used only with raw printing <code>[visual]</code> type.
     *   @param {string} [data.options.xmlTag] Required if passing xml data. Tag name containing base64 formatted data.
     *   @param {number} [data.options.pageWidth=1280] Used only with <code>[html]</code> type printing. Width of the web page to render.
     * @param {boolean} [signature] Pre-signed signature of JSON string containing <code>call</code>, <code>params</code>, and <code>timestamp</code>.
     * @param {number} [signingTimestamp] Required with <code>signature</code>. Timestamp used with pre-signed content.
     *
     * @returns {Promise<null|Error>}
     *
     * @see qz.config.create
     */
    print: function(config, data, signature, signingTimestamp) {
        return new RSVP.Promise(function(resolve, reject) {
            var msg = {
                call: 'print',
                promise: {
                    resolve: resolve, reject: reject
                },
                params: {
                    printer: config.getPrinter(),
                    options: config.getOptions(),
                    data: data
                },
                signature: signature,
                timestamp: signingTimestamp
            };

            _qz.connection.sendData(msg);
        });
    },


    /** Calls related to interaction with serial ports. */
    serial: {
        /**
         * @returns {Promise<Array<string>|Error>} Communication (RS232, COM, TTY) ports available on connected system.
         */
        findPorts: function() {
            return new RSVP.Promise(function(resolve, reject) {
                var msg = {
                    call: 'serial.findPorts',
                    promise: {
                        resolve: resolve, reject: reject
                    }
                };

                _qz.connection.sendData(msg);
            });
        },

        /**
         * List of functions called for any response from open serial ports.
         *
         * @param {Function|Array<Function>} calls Single or array of <code>Function({string} portName, {string} output)</code> calls.
         */
        setSerialCallbacks: function(calls) {
            _qz.serialCallbacks = calls;
        },

        /**
         * @param {string} port Name of port to open.
         * @param {Object} bounds Boundaries of serial port output.
         *  @param {string} [bounds.begin=0x0002] Character denoting start of serial response. Not used if <code>width</code is provided.
         *  @param {string} [bounds.end=0x000D] Character denoting end of serial response. Not used if <code>width</code> is provided.
         *  @param {number} [bounds.width] Used for fixed-width response serial communication.
         *
         * @returns {Promise<null|Error>}
         */
        openPort: function(port, bounds) {
            return new RSVP.Promise(function(resolve, reject) {
                var msg = {
                    call: 'serial.openPort',
                    promise: {
                        resolve: resolve, reject: reject
                    },
                    params: {
                        port: port,
                        bounds: bounds
                    }
                };

                _qz.connection.sendData(msg);
            });
        },

        /**
         * Send commands over a serial port.
         *
         * @param {string} port An open port to send data over.
         * @param {string} data The data to send to the serial device.
         * @param {Object} [properties] Properties of data being sent over the serial port.
         *  @param {string} [properties.baudRate=9600]
         *  @param {string} [properties.dataBits=8]
         *  @param {string} [properties.stopBits=1]
         *  @param {string} [properties.parity='NONE']
         *  @param {string} [properties.flowControl='NONE']
         *
         * @returns {Promise<null|Error>}
         */
        sendData: function(port, data, properties) {
            return new RSVP.Promise(function(resolve, reject) {
                var msg = {
                    call: 'serial.sendData',
                    promise: {
                        resolve: resolve, reject: reject
                    },
                    params: {
                        port: port,
                        data: data,
                        properties: properties
                    }
                };

                _qz.connection.sendData(msg);
            });
        },

        /**
         * @param {string} port Name of port to close.
         *
         * @returns {Promise<null|Error>}
         */
        closePort: function(port) {
            return new RSVP.Promise(function(resolve, reject) {
                var msg = {
                    call: 'serial.closePort',
                    promise: {
                        resolve: resolve, reject: reject
                    },
                    params: {
                        port: port
                    }
                };

                _qz.connection.sendData(msg);
            });
        }
    },


    /** Calls related to signing connection requests. */
    signing: {
        /**
         * List of functions called when requesting a public certificate for signing requests.
         *
         * @param {Function} promiseCall <code>Function({function} resolve)</code> called as promise for getting the public certificate.
         *        Should call <code>resolve</code> parameter with the result.
         */
        setCertificatePromise: function(promiseCall) {
            _qz.certPromise = promiseCall;
        },

        /**
         * List of functions called to sign a request to the connection.
         * Use <code>qz.signing.getContent()</code> to get the content to sign.
         *
         * @param {Function} promiseCall <code>Function({function} resolve)</code> called as promise for signing requests.
         *        Should call <code>resolve</code> parameter with the result.
         * @see qz.signing.getContent
         */
        setSignaturePromise: function(promiseCall) {
            _qz.signaturePromise = promiseCall;
        },

        /**
         * Call to get the string currently needing signed.
         * Mainly used inside the promise function needed for signing requests.
         *
         * @returns {string} String needing signed. <code>Undefined</code> if unnecessary.
         * @see qz.signing.setSignaturePromise
         */
        getContent: function() {
            return _qz.signContent;
        }
    },

    /**
     * Get version of connected QZ Tray application
     *
     * @returns {Promise<string|Error>} Version number of QZ Tray
     */
    getVersion: function() {
        return new RSVP.Promise(function(resolve, reject) {
            var msg = {
                call: 'getVersion',
                promise: {
                    resolve: resolve, reject: reject
                }
            };

            _qz.connection.sendData(msg);
        });
    }

};
