<?php
require_once "RSAToolFactory.php";

$rsa_tool = RSAToolFactory::getRSATool();

$req = $_GET['request'];
$signature = '';
$privateKey = $rsa_tool->loadPrivateKey('test.key');
// 'test.key' will be the server's private key for signing requests

openssl_sign($req,$signature,$privateKey);

$base64signature = base64_encode($signature);
echo $base64signature;
