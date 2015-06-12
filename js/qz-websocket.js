var qzConfig = {
    preemptive: {isActive: '', getVersion: '', getPrinter: '', getLogPostScriptFeatures: ''},
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
        findPorts:       'qzDoneFindingPorts',
        openPort:        'qzDoneOpeningPort',
        closePort:       'qzDoneClosingPort',
        findNetworkInfo: 'qzDoneFindingNetwork'
    },
    protocol: ["wss://", "ws://"],   // Protocols to use, will try secure WS before insecure
    uri: "localhost",                // Base URL to server
    ports: [8181, 8282, 8383, 8484], // Ports to try, insecure WS uses port (ports[x] + 1)
    protocolIndex: 0,                // Used to track which value in 'protocol' array is being used
    portIndex: 0,                    // Used to track which value in 'ports' array is being used
    keepAlive: (60 * 1000)           // Interval in millis to send pings to server
};


function deployQZ() {
    console.log(WebSocket);

    //Old standard of WebSocket used const CLOSED as 2, new standards use const CLOSED as 3, we need the newer standard for jetty
    if ("WebSocket" in window && WebSocket.CLOSED != null && WebSocket.CLOSED > 2) {
        console.log('Starting deploy of qz');

        connectWebsocket(qzConfig.ports[qzConfig.portIndex]);
    } else {
        alert("WebSocket not supported");
        window["deployQZ"] = null;
    }
}

function connectWebsocket(port) {
    console.log('Attempting connection on port ' + port);

    try {
        var websocket = new WebSocket(qzConfig.protocol[qzConfig.protocolIndex] + qzConfig.uri + ":" + port);
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
            window.setInterval(function() {
                websocket.send("ping");
            }, qzConfig.keepAlive);
        };

        websocket.onclose = function(event) {
            if (websocket.valid || qzConfig.portIndex >= qzConfig.ports.length) {
                qzSocketClose(event);
            }
        };

        websocket.onerror = function(event) {
            if (websocket.valid || qzConfig.portIndex >= qzConfig.ports.length) {
                qzSocketError(event);
            }

            // Move on to the next port
            if (!websocket.valid) {
                if (++qzConfig.portIndex < qzConfig.ports.length) {
                    connectWebsocket(qzConfig.ports[qzConfig.portIndex] + qzConfig.protocolIndex);
                } else {
                    if (++qzConfig.protocolIndex < qzConfig.protocol.length) {
                        //Try again using insecure protocol
                        qzConfig.portIndex = 0;
                        connectWebsocket(qzConfig.ports[qzConfig.portIndex] + qzConfig.protocolIndex);
                    } else {
                        qzNoConnection();
                    }
                }
            }
        };

    } else {
        console.warn('Websocket connection failed');
        websocket = null;
        qzNoConnection();
    }
}

function connectionSuccess(websocket) {
    console.log('Websocket connection successful');

    websocket.sendObj = function(objMsg) {
        var msg = JSON.stringify(objMsg);

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
                var result = message.result;
                message.result = {
                    getLocalizedMessage: function() {
                        return result;
                    }
                };
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
