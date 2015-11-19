var qzConfig = {
    callbackMap: {
        findPrinter:     'qzDoneFinding',
        findPrinters:    'qzDoneFinding',
        appendFile:      'qzDoneAppending',
        appendXML:       'qzDoneAppending',
        appendPDF:       'qzDoneAppending',
        appendImage:     'qzDoneAppending',
        print:           'qzDonePrinting',
        printPS:         'qzDonePrinting',
        printHTML:       'qzDonePrinting',
        printToHost:     'qzDonePrinting',
        printToFile:     'qzDonePrinting',
        findPorts:       'qzDoneFindingPorts',
        openPort:        'qzDoneOpeningPort',
        closePort:       'qzDoneClosingPort',
        findNetworkInfo: 'qzDoneFindingNetwork'
    },
    protocols: ["wss://", "ws://"],   // Protocols to use, will try secure WS before insecure
    uri: "localhost",                // Base URL to server
    ports: [8181, 8282, 8383, 8484], // Ports to try, insecure WS uses port (ports[x] + 1)
    keepAlive: (60 * 1000),           // Interval in millis to send pings to server

    port: function() { return qzConfig.ports[qzConfig.portIndex] + qzConfig.protocolIndex; },
    protocol: function() { return qzConfig.protocols[qzConfig.protocolIndex]; },
    url: function() { return qzConfig.protocol() + qzConfig.uri + ":" + qzConfig.port(); },
    increment: function() {
        if (++qzConfig.portIndex < qzConfig.ports.length) {
            return true;
        } else if (++qzConfig.protocolIndex < qzConfig.protocols.length) {
            qzConfig.portIndex = 0;
            return true;
        }
        return false;
    },
    outOfBounds: function() { return qzConfig.portIndex >= qzConfig.ports.length },
    init: function(){
        qzConfig.preemptive = {isActive: '', getVersion: '', getPrinter: '', getLogPostScriptFeatures: ''};
        qzConfig.protocolIndex = 0;         // Used to track which value in 'protocol' array is being used
        qzConfig.portIndex = 0;             // Used to track which value in 'ports' array is being used
        return qzConfig;
    }
};


function deployQZ() {
    console.log(WebSocket);
    qzConfig.init();

    // Old standard of WebSocket used const CLOSED as 2, new standards use const CLOSED as 3, we need the newer standard for jetty
    if ("WebSocket" in window && WebSocket.CLOSED != null && WebSocket.CLOSED > 2) {
        console.log('Starting deploy of qz');
        connectWebsocket();
    } else {
        alert("WebSocket not supported");
        window["deployQZ"] = null;
    }
}

function connectWebsocket() {
    console.log('Attempting connection on port ' + qzConfig.port());

    try {
        var websocket = new WebSocket(qzConfig.url());
    }
    catch(e) {
        console.error(e);
    }

    if (websocket != null) {
        websocket.valid = false;

        websocket.onopen = function(evt) {
            console.log('Open:');
            console.log(evt);

            websocket.valid = true;
            connectionSuccess(websocket);

            // Create the QZ object
            createQZ(websocket);

            // Send keep-alive to the websocket so connection does not timeout
            // keep-alive over reconnecting so server is always able to send to client
            websocket.keepAlive = window.setInterval(function() {
                websocket.send("ping");
            }, qzConfig.keepAlive);
        };

        websocket.onclose = function(event) {
            try {
                if (websocket.valid || qzConfig.outOfBounds()) {
                    qzSocketClose(event);
                }
                // Safari compatibility fix to raise error event
                if (!websocket.valid && navigator.userAgent.indexOf('Safari') != -1 && navigator.userAgent.indexOf('Chrome') == -1) {
                    websocket.onerror();
                }
                websocket.cleanup();
            } catch (ignore) {}
        };

        websocket.onerror = function(event) {
            if (websocket.valid || qzConfig.outOfBounds()) {
                qzSocketError(event);
            }

            // Move on to the next port
            if (!websocket.valid) {
                websocket.cleanup();
                if (qzConfig.increment()) {
                    connectWebsocket();
                } else {
                    qzNoConnection();
                }
            }
        };
		
        websocket.cleanup = function() {
            // Explicitly clear setInterval
            if (websocket.keepAlive) {
                window.clearInterval(websocket.keepAlive);        		
            }
            websocket = null;
        };

    } else {
        console.warn('Websocket connection failed');
        qzNoConnection();
    }
}

// Prototype-safe JSON.stringify
function stringify(o) {
    if (Array.prototype.toJSON) {
        console.warn("Overriding Array.prototype.toJSON");
        var result = null;
        var tmp = Array.prototype.toJSON;
        delete Array.prototype.toJSON;
        result = JSON.stringify(o);
        Array.prototype.toJSON = tmp;
        return result;
    }
    return JSON.stringify(o);
}

function connectionSuccess(websocket) {
    console.log('Websocket connection successful');

    websocket.sendObj = function(objMsg) {
        var msg = stringify(objMsg);

        console.log("Sending " + msg);
        var ws = this;

        // Determine if the message requires signing
        if (objMsg.method === 'listMessages' || Object.keys(qzConfig.preemptive).indexOf(objMsg.method) != -1) {
            ws.send(msg);
        } else {
            signRequest(msg,
                        function(signature) {
                            ws.send(signature + msg);
                        }
            );
        }
    };

    websocket.onmessage = function(evt) {
        var message = JSON.parse(evt.data);

        if (message.error != undefined) {
            console.log(message.error);
            return;
        }

        // After we ask for the list, the value will come back as a message.
        // That means we have to deal with the listMessages separately from everything else.
        if (message.method == 'listMessages') {
            // Take the list of messages and add them to the qz object
            mapMethods(websocket, message.result);

        } else {
            // Got a return value from a call
            console.log('Message:');
            console.log(message);

            if (typeof message.result == 'string') {
                //unescape special characters
                message.result = message.result.replace(/%5C/g, "\\").replace(/%22/g, "\"");

                //ensure boolean strings are read as booleans
                if (message.result == "true" || message.result == "false") {
                    message.result = (message.result == "true");
                }

                if (message.result.substring(0, 1) == '[') {
                    message.result = JSON.parse(message.result);
                }

                //ensure null is read as null
                if (message.result == "null") {
                    message.result = null;
                }
            }

            if (message.callback != 'setupMethods' && message.result != undefined && message.result.constructor !== Array) {
                message.result = [message.result];
            }

            // Special case for getException
            if (message.method == 'getException') {
                if (message.result != null) {
                    var result = message.result;
                    message.result = {
                        getLocalizedMessage: function() {
                            return result;
                        }
                    };
                }
            }

            if (message.callback == 'setupMethods') {
                console.log("Resetting function call");
                console.log(message.result);
                qz[message.method] = function() {
                    return message.result;
                }
            }

            if (message.callback != null) {
                try {
                    console.log("Callbacking: " + message.callback);
                    if (window["qz"][message.callback] != undefined) {
                        window["qz"][message.callback].apply(this, message.init ? [message.method] : message.result);
                    } else {
                        window[message.callback].apply(this, message.result);
                    }
                }
                catch(err) {
                    console.error(err);
                }
            }
        }

        console.log("Finished processing message");
    };
}

function createQZ(websocket) {
    // Get list of methods from websocket
    getCertificate(function(cert) {
        websocket.sendObj({method: 'listMessages', params: [cert]});
        window["qz"] = {};
    });
}

function mapMethods(websocket, methods) {
    console.log('Adding ' + methods.length + ' methods to qz object');
    for(var x = 0; x < methods.length; x++) {
        var name = methods[x].name;
        var returnType = methods[x].returns;
        var numParams = methods[x].parameters;

        // Determine how many parameters there are and create method with that many
        (function(_name, _numParams, _returnType) {
            //create function to map function name to parameter counted function
            window["qz"][_name] = function() {
                var func = undefined;
                if (typeof arguments[arguments.length - 1] == 'function') {
                    func = window["qz"][_name + '_' + (arguments.length - 1)];
                } else {
                    func = window["qz"][_name + '_' + arguments.length];
                }

                func.apply(this, arguments);
            };

            //create parameter counted function to include overloaded java methods in javascript object
            window["qz"][_name + '_' + _numParams] = function() {
                var args = [];
                for(var i = 0; i < _numParams; i++) {
                    args.push(arguments[i]);
                }

                var cb = arguments[arguments.length - 1];
                var cbName = _name + '_callback';

                if ($.isFunction(cb)) {
                    var method = cb.name;

                    // Special case for IE, which does not have function.name property ..
                    if (method == undefined) {
                        method = cb.toString().match(/^function\s*([^\s(]+)/)[1];
                    }

                    if (method == 'setupMethods') {
                        cbName = method;
                    }

                    window["qz"][cbName] = cb;
                } else {
                    console.log("Using mapped callback " + qzConfig.callbackMap[_name] + "() for " + _name + "()");
                    cbName = qzConfig.callbackMap[_name];
                }

                console.log("Calling " + _name + "(" + args + ") --> CB: " + cbName + "()");
                websocket.sendObj({method: _name, params: args, callback: cbName, init: (cbName == 'setupMethods')});
            }
        })(name, numParams, returnType);
    }

    // Re-setup all functions with static returns
    for(var key in qzConfig.preemptive) {
        window["qz"][key](setupMethods);
    }

    console.log("Sent methods off to get rehabilitated");
}

function setupMethods(methodName) {
    if ($.param(qzConfig.preemptive).length > 0) {
        console.log("Reset " + methodName);
        delete qzConfig.preemptive[methodName];

        console.log("Methods left to return: " + $.param(qzConfig.preemptive).length);

        // Fire ready method when everything on the QZ object has been added
        if ($.param(qzConfig.preemptive).length == 0) {
            qzReady();
        }
    }
}
